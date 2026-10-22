package com.josercha.openplayer

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Exclusão de vídeos com autorização feita UMA vez (como no XPlayer) e depois sem janelas:
 *
 *  1. Primeiro tenta apagar em silêncio (vale para todos os vídeos de uma vez).
 *  2. Se faltar permissão:
 *     - Android 10 ou anterior: pede a permissão de escrita (uma vez). Vídeos no cartão SD pedem a
 *       autorização da raiz do cartão (uma vez, guardada para sempre).
 *     - Android 11 ou mais novo: oferece liberar "Acesso a todos os arquivos" (uma vez). Se o usuário
 *       preferir não liberar, usa a janela de confirmação do sistema.
 *
 * Crie o objeto no início da Activity (propriedade da classe), antes de ela ficar visível.
 */
class DeleteHelper(
    private val activity: ComponentActivity,
    private val onFinished: (deleted: Int, failed: Int, cancelled: Boolean) -> Unit
) {

    private class Item(val uri: Uri, val path: String?)

    private val cr get() = activity.contentResolver
    // lazy: a Activity ainda não tem contexto quando esta classe é criada
    private val prefs by lazy { activity.getSharedPreferences("delete_helper", Context.MODE_PRIVATE) }

    private var pending: List<Item> = emptyList()
    private var deleted = 0
    private var failed = 0
    private var cancelled = false
    private var triedWrite = false

    private var sdUuid: String? = null
    private var sdItems: List<Item> = emptyList()

    private var senderMode = 0 // 1 = pedido do sistema (Android 11+), 2 = autorização por arquivo (Android 10)
    private var systemItems: List<Item> = emptyList()
    private val queue = ArrayDeque<Item>()

    // ---------- Janelas / permissões ----------

    private val writePerm = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { attempt(pending) }

    private val allFilesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            attempt(pending)
        } else if (Build.VERSION.SDK_INT >= 30) {
            systemDelete(pending)
        }
    }

    private val treeLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r -> onTreePicked(if (r.resultCode == Activity.RESULT_OK) r.data?.data else null) }

    private val sender = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r -> onSenderResult(r.resultCode == Activity.RESULT_OK) }

    // ---------- Entrada ----------

    fun delete(uris: List<Uri>) {
        if (uris.isEmpty()) {
            onFinished(0, 0, false)
            return
        }
        deleted = 0
        failed = 0
        cancelled = false
        triedWrite = false
        Thread {
            val items = uris.map { Item(it, queryPath(it)) }
            activity.runOnUiThread { attempt(items) }
        }.start()
    }

    /** Tenta apagar tudo em silêncio; o que sobrar segue para o próximo passo. */
    private fun attempt(items: List<Item>) {
        Thread {
            val left = ArrayList<Item>()
            for (i in items) {
                if (silentDelete(i)) {
                    deleted++
                    forgetPosition(i.uri)
                } else {
                    left.add(i)
                }
            }
            activity.runOnUiThread {
                pending = left
                next()
            }
        }.start()
    }

    private fun silentDelete(i: Item): Boolean {
        try {
            cr.delete(i.uri, null, null)
        } catch (_: Exception) {
        }
        val p = i.path ?: return !rowExists(i.uri)
        val f = File(p)
        if (f.exists()) f.delete()
        return !f.exists()
    }

    private fun next() {
        if (pending.isEmpty()) {
            done()
            return
        }
        if (Build.VERSION.SDK_INT >= 30) nextApi30() else nextLegacy()
    }

    // ---------- Android 10 e anteriores ----------

    private fun nextLegacy() {
        if (!hasWritePermission() && !triedWrite) {
            triedWrite = true
            writePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        val sd = pending.filter { it.path != null && volumeOf(it.path) != null }
        if (sd.isNotEmpty()) {
            askSdAccess(sd)
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            queue.clear()
            queue.addAll(pending)
            pending = emptyList()
            runRecoverable()
        } else {
            failed += pending.size
            pending = emptyList()
            done()
        }
    }

    /** Cartão SD: pede a raiz do cartão uma única vez e guarda a autorização. */
    private fun askSdAccess(items: List<Item>) {
        val uuid = volumeOf(items[0].path!!)!!.first
        val group = items.filter { volumeOf(it.path!!)?.first == uuid }
        val tree = savedTree(uuid)
        if (tree != null) {
            deleteViaTree(uuid, tree, group)
            return
        }
        sdUuid = uuid
        sdItems = group
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.del_sd_title)
            .setMessage(activity.getString(R.string.del_sd_msg, "/storage/$uuid"))
            .setPositiveButton(R.string.btn_start) { _, _ ->
                treeLauncher.launch(treeIntent(group[0].path!!))
            }
            .setNegativeButton(R.string.cancel) { _, _ -> cancelItems(group) }
            .setOnCancelListener { cancelItems(group) }
            .show()
    }

    private fun onTreePicked(uri: Uri?) {
        val uuid = sdUuid
        val group = sdItems
        if (uri == null || uuid == null) {
            cancelItems(group)
            return
        }
        val id = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            ""
        }
        if (id != "$uuid:") {
            // Escolheu uma pasta dentro do cartão em vez da raiz: pede de novo
            Toast.makeText(activity, activity.getString(R.string.del_sd_wrong, "/storage/$uuid"), Toast.LENGTH_LONG).show()
            askSdAccess(group)
            return
        }
        try {
            cr.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        prefs.edit().putString("tree_$uuid", uri.toString()).apply()
        deleteViaTree(uuid, uri, group)
    }

    private fun deleteViaTree(uuid: String, tree: Uri, group: List<Item>) {
        Thread {
            var okCount = 0
            for (i in group) {
                val rel = volumeOf(i.path!!)!!.second
                val doc = DocumentsContract.buildDocumentUriUsingTree(tree, "$uuid:$rel")
                val ok = try {
                    DocumentsContract.deleteDocument(cr, doc)
                } catch (_: Exception) {
                    false
                }
                if (ok) {
                    okCount++
                    deleted++
                    forgetPosition(i.uri)
                    try {
                        cr.delete(i.uri, null, null)
                    } catch (_: Exception) {
                    }
                    MediaScannerConnection.scanFile(activity, arrayOf(i.path!!), null, null)
                } else {
                    failed++
                }
            }
            // Autorização guardada deixou de valer (cartão trocado etc.): pede de novo na próxima vez
            if (okCount == 0) prefs.edit().remove("tree_$uuid").apply()
            activity.runOnUiThread {
                pending = pending.filter { it !in group }
                next()
            }
        }.start()
    }

    private fun cancelItems(group: List<Item>) {
        failed += group.size
        cancelled = true
        pending = pending.filter { it !in group }
        next()
    }

    private fun savedTree(uuid: String): Uri? {
        val s = prefs.getString("tree_$uuid", null) ?: return null
        val u = Uri.parse(s)
        val valid = cr.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
        return if (valid) u else null
    }

    @Suppress("DEPRECATION")
    private fun treeIntent(path: String): Intent {
        try {
            val sm = activity.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.getStorageVolume(File(path))?.createOpenDocumentTreeIntent()?.let { return it }
        } catch (_: Exception) {
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    /** Android 10 (sem a permissão de escrita): o sistema autoriza um vídeo por vez. */
    private fun runRecoverable() {
        senderMode = 2
        Thread {
            while (queue.isNotEmpty()) {
                val i = queue.first()
                try {
                    if (cr.delete(i.uri, null, null) > 0) {
                        deleted++
                        forgetPosition(i.uri)
                    } else {
                        failed++
                    }
                    queue.removeFirst()
                } catch (e: SecurityException) {
                    val intentSender = recoverable(e)
                    if (intentSender != null) {
                        activity.runOnUiThread {
                            try {
                                sender.launch(IntentSenderRequest.Builder(intentSender).build())
                            } catch (_: Exception) {
                                queue.removeFirst()
                                failed++
                                runRecoverable()
                            }
                        }
                        return@Thread
                    }
                    failed++
                    queue.removeFirst()
                } catch (_: Exception) {
                    failed++
                    queue.removeFirst()
                }
            }
            activity.runOnUiThread { done() }
        }.start()
    }

    private fun recoverable(e: SecurityException): IntentSender? {
        if (Build.VERSION.SDK_INT < 29) return null
        return (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
    }

    // ---------- Android 11 ou mais novo ----------

    private fun nextApi30() {
        if (Build.VERSION.SDK_INT < 30) return
        if (Environment.isExternalStorageManager() || prefs.getBoolean("asked_all_files", false)) {
            systemDelete(pending)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.del_all_title)
            .setMessage(R.string.del_all_msg)
            .setPositiveButton(R.string.btn_allow) { _, _ ->
                prefs.edit().putBoolean("asked_all_files", true).apply()
                openAllFilesSettings()
            }
            .setNegativeButton(R.string.btn_not_now) { _, _ ->
                prefs.edit().putBoolean("asked_all_files", true).apply()
                systemDelete(pending)
            }
            .setOnCancelListener { systemDelete(pending) }
            .show()
    }

    @RequiresApi(30)
    private fun openAllFilesSettings() {
        try {
            allFilesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (_: Exception) {
            try {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                systemDelete(pending)
            }
        }
    }

    /** Confirmação do sistema: uma janela só para todos os vídeos que sobraram. */
    @RequiresApi(30)
    private fun systemDelete(items: List<Item>) {
        if (items.isEmpty()) {
            pending = emptyList()
            done()
            return
        }
        systemItems = items
        senderMode = 1
        try {
            val pi = MediaStore.createDeleteRequest(cr, items.map { it.uri })
            sender.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (_: Exception) {
            failed += items.size
            pending = emptyList()
            done()
        }
    }

    private fun onSenderResult(ok: Boolean) {
        if (senderMode == 1) {
            if (ok) {
                deleted += systemItems.size
                systemItems.forEach { forgetPosition(it.uri) }
            } else {
                failed += systemItems.size
                cancelled = true
            }
            systemItems = emptyList()
            pending = emptyList()
            done()
        } else {
            if (ok) {
                runRecoverable()
            } else {
                failed += queue.size
                queue.clear()
                cancelled = true
                done()
            }
        }
    }

    // ---------- Utilidades ----------

    private fun queryPath(u: Uri): String? = try {
        cr.query(u, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun rowExists(u: Uri): Boolean = try {
        cr.query(u, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
    } catch (_: Exception) {
        true
    }

    /** "/storage/3043-1A01/Pasta/x.mp4" -> ("3043-1A01", "Pasta/x.mp4"); memória interna -> null. */
    private fun volumeOf(path: String?): Pair<String, String>? {
        if (path == null) return null
        val m = Regex("^/storage/([^/]+)/(.+)$").find(path) ?: return null
        val vol = m.groupValues[1]
        if (vol == "emulated" || vol == "self") return null
        return Pair(vol, m.groupValues[2])
    }

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun forgetPosition(u: Uri) {
        activity.getSharedPreferences("positions", Activity.MODE_PRIVATE)
            .edit().remove(u.toString()).apply()
    }

    private fun done() {
        pending = emptyList()
        onFinished(deleted, failed, cancelled)
    }
}

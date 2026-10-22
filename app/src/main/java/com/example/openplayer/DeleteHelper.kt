package com.example.openplayer

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Exclusão de vídeos que funciona em todas as versões do Android:
 *  - Android 11+  : pedido de exclusão do sistema (MediaStore.createDeleteRequest), uma janela só para todos
 *                   os vídeos. Se o "Acesso a todos os arquivos" estiver liberado, apaga direto, sem perguntar.
 *  - Android 10   : tenta apagar; se o sistema exigir, mostra a janela de permissão (RecoverableSecurityException)
 *                   e tenta de novo.
 *  - Android 9-   : pede a permissão de escrita em tempo de execução e apaga.
 *
 * Crie o objeto direto no início da Activity (propriedade da classe ou onCreate), antes de ela ficar visível.
 */
class DeleteHelper(
    private val activity: ComponentActivity,
    private val onFinished: (deleted: Int, failed: Int, cancelled: Boolean) -> Unit
) {

    private val todo = ArrayDeque<Uri>()
    private var requested: List<Uri> = emptyList()
    private var deleted = 0
    private var failed = 0

    private val sender = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+: o próprio sistema apagou tudo se o usuário aceitou
            if (r.resultCode == Activity.RESULT_OK) {
                deleted += requested.size
                requested.forEach { forgetPosition(it) }
                done(false)
            } else {
                failed += requested.size
                done(true)
            }
        } else {
            // Android 10: o usuário liberou este arquivo; repete a tentativa
            if (r.resultCode == Activity.RESULT_OK) {
                runQueue()
            } else {
                failed += todo.size
                todo.clear()
                done(true)
            }
        }
    }

    private val writePermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runQueue()
        } else {
            failed += todo.size
            todo.clear()
            done(true)
        }
    }

    fun delete(uris: List<Uri>) {
        if (uris.isEmpty()) {
            onFinished(0, 0, false)
            return
        }
        deleted = 0
        failed = 0
        todo.clear()
        todo.addAll(uris)
        when {
            Build.VERSION.SDK_INT >= 30 -> deleteApi30()
            Build.VERSION.SDK_INT <= 28 && !hasWritePermission() ->
                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> runQueue()
        }
    }

    // ---------- Android 11+ ----------

    @RequiresApi(30)
    private fun deleteApi30() {
        val all = todo.toList()
        todo.clear()
        Thread {
            val remaining = ArrayList<Uri>()
            if (Environment.isExternalStorageManager()) {
                // Com "Acesso a todos os arquivos" não precisa perguntar
                for (u in all) if (deleteDirect(u)) { deleted++; forgetPosition(u) } else remaining.add(u)
            } else {
                remaining.addAll(all)
            }
            activity.runOnUiThread {
                if (remaining.isEmpty()) {
                    done(false)
                } else {
                    requested = remaining
                    try {
                        val pi = MediaStore.createDeleteRequest(activity.contentResolver, remaining)
                        sender.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } catch (_: Exception) {
                        failed += remaining.size
                        done(false)
                    }
                }
            }
        }.start()
    }

    // ---------- Android 10 e anteriores ----------

    private fun runQueue() {
        Thread {
            while (todo.isNotEmpty()) {
                val u = todo.first()
                try {
                    if (activity.contentResolver.delete(u, null, null) > 0) {
                        deleted++
                        forgetPosition(u)
                    } else {
                        failed++
                    }
                    todo.removeFirst()
                } catch (e: SecurityException) {
                    val intentSender = recoverable(e)
                    if (intentSender != null) {
                        // O sistema precisa que o usuário autorize este vídeo
                        activity.runOnUiThread {
                            try {
                                sender.launch(IntentSenderRequest.Builder(intentSender).build())
                            } catch (_: Exception) {
                                todo.removeFirst()
                                failed++
                                runQueue()
                            }
                        }
                        return@Thread
                    }
                    failed++
                    todo.removeFirst()
                } catch (_: Exception) {
                    failed++
                    todo.removeFirst()
                }
            }
            activity.runOnUiThread { done(false) }
        }.start()
    }

    private fun recoverable(e: SecurityException): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < 29) return null
        return (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
    }

    // ---------- Utilidades ----------

    private fun deleteDirect(u: Uri): Boolean = try {
        activity.contentResolver.delete(u, null, null) > 0
    } catch (_: Exception) {
        false
    }

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    /** Esquece a posição salva do vídeo apagado. */
    private fun forgetPosition(u: Uri) {
        activity.getSharedPreferences("positions", Activity.MODE_PRIVATE)
            .edit().remove(u.toString()).apply()
    }

    private fun done(cancelled: Boolean) {
        requested = emptyList()
        onFinished(deleted, failed, cancelled)
    }
}

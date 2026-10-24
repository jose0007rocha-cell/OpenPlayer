package com.josercha.openplayer

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * Renomeia o arquivo do vídeo. Primeiro tenta pelo MediaStore (mantém o mesmo vídeo na biblioteca);
 * se o Android exigir autorização, mostra a janela do sistema uma vez e repete; como último recurso
 * tenta renomear o arquivo direto (funciona com acesso a todos os arquivos).
 * Crie o objeto no início da Activity (propriedade da classe).
 */
class RenameHelper(
    private val activity: ComponentActivity,
    private val onFinished: (success: Boolean, cancelled: Boolean) -> Unit
) {

    private var video: Video? = null
    private var newName = ""
    private var granted = false // a janela do sistema já foi aceita nesta tentativa

    private val sender = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            granted = true
            attempt()
        } else {
            onFinished(false, true)
        }
    }

    /** [fullName] já inclui a extensão (ex.: "ferias.mp4"). */
    fun rename(v: Video, fullName: String) {
        video = v
        newName = fullName
        granted = false
        attempt()
    }

    private fun attempt() {
        val v = video ?: return
        Thread {
            var ok = false
            var ask: IntentSender? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                ok = activity.contentResolver.update(v.uri, values, null, null) > 0
            } catch (e: SecurityException) {
                if (!granted) ask = authorization(e, v)
            } catch (_: Exception) {
            }
            if (!ok && ask == null) ok = renameFile(v)
            val asking = ask
            activity.runOnUiThread {
                if (asking != null) {
                    try {
                        sender.launch(IntentSenderRequest.Builder(asking).build())
                    } catch (_: Exception) {
                        onFinished(false, false)
                    }
                } else {
                    onFinished(ok, false)
                }
            }
        }.start()
    }

    /** Janela do sistema que autoriza a troca de nome deste vídeo (Android 10 em diante). */
    private fun authorization(e: SecurityException, v: Video): IntentSender? {
        if (Build.VERSION.SDK_INT >= 30) {
            return try {
                MediaStore.createWriteRequest(activity.contentResolver, listOf(v.uri)).intentSender
            } catch (_: Exception) {
                null
            }
        }
        if (Build.VERSION.SDK_INT == 29) {
            return (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
        }
        return null
    }

    private fun renameFile(v: Video): Boolean {
        if (v.path.isEmpty()) return false
        val from = File(v.path)
        val to = File(from.parentFile ?: return false, newName)
        if (!from.exists() || to.exists() || !from.renameTo(to)) return false
        MediaScannerConnection.scanFile(activity, arrayOf(from.path, to.path), null, null)
        return true
    }
}

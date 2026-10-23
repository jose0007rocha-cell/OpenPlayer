package com.josercha.openplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object VideoRepository {

    /** Permissões pedidas ao usuário, por versão do Android. */
    val permissions: Array<String> = when {
        // Android 14+: o pedido precisa incluir a permissão de acesso parcial
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Permissão principal (usada para saber se o Android ainda vai perguntar). */
    val permission: String = permissions[0]

    private fun granted(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Acesso total aos vídeos ou, no Android 14+, acesso parcial aos vídeos escolhidos. */
    fun hasPermission(c: Context): Boolean =
        granted(c, permission) ||
            (Build.VERSION.SDK_INT >= 34 && granted(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))

    @Suppress("DEPRECATION")
    fun load(c: Context): List<Video> {
        val result = ArrayList<Video>()
        val media = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        try {
            c.contentResolver.query(
                media, projection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cur ->
                val iId = cur.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = cur.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = cur.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = cur.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iBucketId = cur.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val iBucket = cur.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val iData = cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (cur.moveToNext()) {
                    result.add(
                        Video(
                            uri = ContentUris.withAppendedId(media, cur.getLong(iId)),
                            name = cur.getString(iName) ?: "?",
                            durationMs = cur.getLong(iDur),
                            sizeBytes = cur.getLong(iSize),
                            folderId = cur.getLong(iBucketId),
                            folderName = cur.getString(iBucket) ?: "?",
                            path = cur.getString(iData) ?: ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun folders(videos: List<Video>): List<Folder> =
        videos.groupBy { it.folderId }
            .map { (id, list) -> Folder(id, list.first().folderName, list.size) }
            .sortedBy { it.name.lowercase() }
}

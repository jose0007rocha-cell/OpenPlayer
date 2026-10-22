package com.josercha.openplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object VideoRepository {

    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED

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

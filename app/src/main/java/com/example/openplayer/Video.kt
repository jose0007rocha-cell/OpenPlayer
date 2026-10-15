package com.example.openplayer

import android.content.Context
import android.content.Intent
import android.net.Uri

data class Video(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderId: Long,
    val folderName: String,
    val path: String
) {
    fun isDownload(): Boolean = path.contains("/download", ignoreCase = true)
}

data class Folder(
    val id: Long,
    val name: String,
    val count: Int
)

fun Context.openVideo(uri: Uri) {
    startActivity(
        Intent(this, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
        }
    )
}

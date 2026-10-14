package com.example.openplayer

import android.net.Uri

data class Video(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long
)

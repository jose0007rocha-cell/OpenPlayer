package com.josercha.openplayer

import android.content.Context
import android.net.Uri

/** Posição em que cada vídeo parou (usada pelo player e pelo serviço de reprodução). */
object Positions {

    private fun prefs(c: Context) = c.getSharedPreferences("positions", Context.MODE_PRIVATE)

    fun get(c: Context, uri: Uri): Long = prefs(c).getLong(uri.toString(), 0L)

    /** Guarda a posição; se o vídeo está nos últimos 3 segundos, esquece (começa do início da próxima vez). */
    fun save(c: Context, uri: Uri, position: Long, duration: Long) {
        val key = uri.toString()
        if (duration > 0 && position > duration - 3000) {
            prefs(c).edit().remove(key).apply()
        } else if (position > 0) {
            prefs(c).edit().putLong(key, position).apply()
        }
    }
}

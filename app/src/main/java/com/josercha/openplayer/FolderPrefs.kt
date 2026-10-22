package com.josercha.openplayer

import android.content.Context

/** Guarda quais pastas o usuário ocultou (os vídeos continuam no aparelho). */
object FolderPrefs {

    private fun p(c: Context) = c.getSharedPreferences("folders", Context.MODE_PRIVATE)

    fun hidden(c: Context): Set<Long> =
        (p(c).getStringSet("hidden", emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun setHidden(c: Context, ids: Set<Long>, hide: Boolean) {
        val cur = hidden(c).toMutableSet()
        if (hide) cur.addAll(ids) else cur.removeAll(ids)
        p(c).edit().putStringSet("hidden", cur.map { it.toString() }.toSet()).apply()
    }
}

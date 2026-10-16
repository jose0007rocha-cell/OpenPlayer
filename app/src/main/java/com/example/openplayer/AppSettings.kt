package com.example.openplayer

import android.content.Context
import android.content.pm.ActivityInfo

object AppSettings {

    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // 0 = sistema, 1 = sensor, 2 = retrato, 3 = paisagem
    fun rotation(c: Context): Int = p(c).getInt("rotation", 0)
    fun setRotation(c: Context, v: Int) = p(c).edit().putInt("rotation", v).apply()

    fun orientationFlag(c: Context): Int = when (rotation(c)) {
        1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun subtitlesOn(c: Context): Boolean = p(c).getBoolean("subs_on", true)
    fun setSubtitlesOn(c: Context, v: Boolean) = p(c).edit().putBoolean("subs_on", v).apply()

    // 0 = pequena, 1 = média, 2 = grande
    fun subSize(c: Context): Int = p(c).getInt("sub_size", 1)
    fun setSubSize(c: Context, v: Int) = p(c).edit().putInt("sub_size", v).apply()

    fun subSizeScale(c: Context): Float = when (subSize(c)) {
        0 -> 0.8f
        2 -> 1.4f
        else -> 1.0f
    }

    // 0 = sombra, 1 = fundo preto, 2 = contorno, 3 = amarela
    fun subStyle(c: Context): Int = p(c).getInt("sub_style", 0)
    fun setSubStyle(c: Context, v: Int) = p(c).edit().putInt("sub_style", v).apply()

    // Ordenação dentro das pastas: 0 = nome (A-Z), 1 = tamanho
    fun sortBy(c: Context): Int = p(c).getInt("sort_by", 0)
    fun setSortBy(c: Context, v: Int) = p(c).edit().putInt("sort_by", v).apply()

    fun sortDesc(c: Context): Boolean = p(c).getBoolean("sort_desc", false)
    fun setSortDesc(c: Context, v: Boolean) = p(c).edit().putBoolean("sort_desc", v).apply()

    fun gridView(c: Context): Boolean = p(c).getBoolean("grid_view", false)
    fun setGridView(c: Context, v: Boolean) = p(c).edit().putBoolean("grid_view", v).apply()
}

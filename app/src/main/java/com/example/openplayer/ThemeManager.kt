package com.example.openplayer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    class Accent(val id: String, val nameRes: Int, val styleRes: Int, val colorRes: Int)

    val accents = listOf(
        Accent("blue", R.string.accent_blue, R.style.AccentBlue, R.color.accent_blue),
        Accent("green", R.string.accent_green, R.style.AccentGreen, R.color.accent_green),
        Accent("orange", R.string.accent_orange, R.style.AccentOrange, R.color.accent_orange),
        Accent("pink", R.string.accent_pink, R.style.AccentPink, R.color.accent_pink),
        Accent("purple", R.string.accent_purple, R.style.AccentPurple, R.color.accent_purple),
        Accent("red", R.string.accent_red, R.style.AccentRed, R.color.accent_red),
        Accent("teal", R.string.accent_teal, R.style.AccentTeal, R.color.accent_teal)
    )

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private fun prefs(c: Context) = c.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun getMode(c: Context): Int = prefs(c).getInt("mode", MODE_SYSTEM)

    fun setMode(c: Context, mode: Int) {
        prefs(c).edit().putInt("mode", mode).apply()
        applyMode(mode)
    }

    fun applyMode(c: Context) = applyMode(getMode(c))

    private fun applyMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun getAccentId(c: Context): String = prefs(c).getString("accent", "blue") ?: "blue"

    fun setAccent(c: Context, id: String) {
        prefs(c).edit().putString("accent", id).apply()
    }

    fun isAmoled(c: Context): Boolean = prefs(c).getBoolean("amoled", false)

    fun setAmoled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("amoled", on).apply()
    }

    fun signature(c: Context): String = getAccentId(c) + "|" + isAmoled(c)

    /** Chamar ANTES de super.onCreate() de cada activity que usa o tema do app. */
    fun apply(a: Activity) {
        val accent = accents.firstOrNull { it.id == getAccentId(a) } ?: accents[0]
        a.theme.applyStyle(accent.styleRes, true)
        val night = (a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (night && isAmoled(a)) {
            a.theme.applyStyle(R.style.OverlayAmoled, true)
        }
    }
}

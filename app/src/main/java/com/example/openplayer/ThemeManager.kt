package com.example.openplayer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import java.io.File

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

    fun signature(c: Context): String =
        getAccentId(c) + "|" + isAmoled(c) + "|" + hasBackground(c) + "|" +
            prefs(c).getLong("bg_ver", 0L) + "|" + getDim(c)

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ---------- Imagem de fundo (da galeria) ----------

    private var cacheBmp: Bitmap? = null
    private var cacheVer = -1L

    private fun bgFile(c: Context) = File(c.filesDir, "background.jpg")

    fun hasBackground(c: Context): Boolean =
        prefs(c).getBoolean("bg_on", false) && bgFile(c).exists()

    fun getDim(c: Context): Int = prefs(c).getInt("bg_dim", 45)

    fun setDim(c: Context, value: Int) {
        prefs(c).edit().putInt("bg_dim", value).apply()
    }

    fun removeBackground(c: Context) {
        prefs(c).edit().putBoolean("bg_on", false).putLong("bg_ver", System.currentTimeMillis()).apply()
        bgFile(c).delete()
        cacheBmp = null
        cacheVer = -1L
    }

    fun loadBackground(c: Context): Bitmap? {
        if (!hasBackground(c)) return null
        val ver = prefs(c).getLong("bg_ver", 0L)
        val cached = cacheBmp
        if (cached != null && cacheVer == ver) return cached
        val bmp = BitmapFactory.decodeFile(bgFile(c).absolutePath) ?: return null
        cacheBmp = bmp
        cacheVer = ver
        return bmp
    }

    /** Lê a imagem escolhida, corta no formato da tela e guarda dentro do app. */
    fun saveBackground(c: Context, uri: Uri): Boolean {
        return try {
            val dm = c.resources.displayMetrics
            val tw = minOf(dm.widthPixels, dm.heightPixels)
            val th = maxOf(dm.widthPixels, dm.heightPixels)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val shortSide = minOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (shortSide / (sample * 2) >= tw) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            val orientation = try {
                c.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                else -> {}
            }
            if (!m.isIdentity) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            val scale = maxOf(tw.toFloat() / bmp.width, th.toFloat() / bmp.height)
            val cw = minOf(bmp.width, (tw / scale).toInt())
            val ch = minOf(bmp.height, (th / scale).toInt())
            val cropped = Bitmap.createBitmap(
                bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch
            )
            val result = Bitmap.createScaledBitmap(cropped, tw, th, true)

            bgFile(c).outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            prefs(c).edit()
                .putBoolean("bg_on", true)
                .putLong("bg_ver", System.currentTimeMillis())
                .apply()
            cacheBmp = null
            cacheVer = -1L
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Põe a imagem (com um véu escuro/claro por cima) como fundo da janela. */
    fun applyBackground(a: Activity) {
        val bmp = loadBackground(a) ?: return
        val veil = ColorUtils.setAlphaComponent(
            if (isNight(a)) Color.BLACK else Color.WHITE,
            getDim(a) * 255 / 100
        )
        a.window.setBackgroundDrawable(
            LayerDrawable(arrayOf<Drawable>(BitmapDrawable(a.resources, bmp), ColorDrawable(veil)))
        )
    }

    /** Chamar ANTES de super.onCreate() de cada activity que usa o tema do app. */
    fun apply(a: Activity) {
        val accent = accents.firstOrNull { it.id == getAccentId(a) } ?: accents[0]
        a.theme.applyStyle(accent.styleRes, true)
        if (isNight(a) && isAmoled(a)) {
            a.theme.applyStyle(R.style.OverlayAmoled, true)
        }
    }
}

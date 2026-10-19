package com.example.openplayer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.content.res.ColorStateList
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt

object ThemeManager {

    class Accent(val id: String, val nameRes: Int, val styleRes: Int, val colorRes: Int, val endColorRes: Int)

    val accents = listOf(
        Accent("base", R.string.accent_base, R.style.AccentBase, R.color.accent_base, R.color.accent_base_end),
        Accent("blue", R.string.accent_blue, R.style.AccentBlue, R.color.accent_blue, R.color.accent_blue_end),
        Accent("green", R.string.accent_green, R.style.AccentGreen, R.color.accent_green, R.color.accent_green_end),
        Accent("orange", R.string.accent_orange, R.style.AccentOrange, R.color.accent_orange, R.color.accent_orange_end),
        Accent("pink", R.string.accent_pink, R.style.AccentPink, R.color.accent_pink, R.color.accent_pink_end),
        Accent("purple", R.string.accent_purple, R.style.AccentPurple, R.color.accent_purple, R.color.accent_purple_end),
        Accent("red", R.string.accent_red, R.style.AccentRed, R.color.accent_red, R.color.accent_red_end),
        Accent("teal", R.string.accent_teal, R.style.AccentTeal, R.color.accent_teal, R.color.accent_teal_end),
        Accent("sunset", R.string.accent_sunset, R.style.AccentSunset, R.color.accent_sunset, R.color.accent_sunset_end),
        Accent("ocean", R.string.accent_ocean, R.style.AccentOcean, R.color.accent_ocean, R.color.accent_ocean_end),
        Accent("neon", R.string.accent_neon, R.style.AccentNeon, R.color.accent_neon, R.color.accent_neon_end)
    )

    private fun prefs(c: Context) = c.getSharedPreferences("theme", Context.MODE_PRIVATE)

    /** O app agora é sempre escuro (tema claro removido). */
    fun applyMode(@Suppress("UNUSED_PARAMETER") c: Context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    fun getAccentId(c: Context): String = prefs(c).getString("accent", "base") ?: "base"

    fun setAccent(c: Context, id: String) {
        prefs(c).edit().putString("accent", id).apply()
    }

    fun isAmoled(c: Context): Boolean = prefs(c).getBoolean("amoled", false)

    fun setAmoled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("amoled", on).apply()
    }

    fun signature(c: Context): String =
        getAccentId(c) + "|" + isAmoled(c) + "|" + hasBackground(c) + "|" +
            prefs(c).getLong("bg_ver", 0L) + "|" + getDim(c) + "|" + getBlur(c)

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ---------- Imagem de fundo (da galeria) ----------

    private var cacheBmp: Bitmap? = null
    private var cacheVer = -1L
    private var blurCache: Bitmap? = null
    private var blurKey = ""

    private fun bgFile(c: Context) = File(c.filesDir, "background.jpg")

    fun hasBackground(c: Context): Boolean =
        prefs(c).getBoolean("bg_on", false) && bgFile(c).exists()

    fun getDim(c: Context): Int = prefs(c).getInt("bg_dim", 45)

    fun getBlur(c: Context): Int = prefs(c).getInt("bg_blur", 0)

    fun setBlur(c: Context, value: Int) {
        prefs(c).edit().putInt("bg_blur", value).apply()
    }

    fun currentAccent(c: Context): Accent =
        accents.firstOrNull { it.id == getAccentId(c) } ?: accents[0]

    /** Par de cores do gradiente do tema atual: (início, fim). */
    fun gradientColors(c: Context): Pair<Int, Int> {
        val a = currentAccent(c)
        return Pair(
            androidx.core.content.ContextCompat.getColor(c, a.colorRes),
            androidx.core.content.ContextCompat.getColor(c, a.endColorRes)
        )
    }

    fun accentStyle(c: Context): Int =
        (accents.firstOrNull { it.id == getAccentId(c) } ?: accents[0]).styleRes

    fun setDim(c: Context, value: Int) {
        prefs(c).edit().putInt("bg_dim", value).apply()
    }

    fun removeBackground(c: Context) {
        prefs(c).edit().putBoolean("bg_on", false).putLong("bg_ver", System.currentTimeMillis()).apply()
        bgFile(c).delete()
        cacheBmp = null
        cacheVer = -1L
        blurCache = null
        blurKey = ""
    }

    /** Imagem salva, sem desfoque nem véu. */
    fun loadRawBackground(c: Context): Bitmap? {
        if (!hasBackground(c)) return null
        val ver = prefs(c).getLong("bg_ver", 0L)
        val cachedBase = cacheBmp
        if (cachedBase != null && cacheVer == ver) return cachedBase
        val decoded = BitmapFactory.decodeFile(bgFile(c).absolutePath) ?: return null
        cacheBmp = decoded
        cacheVer = ver
        blurCache = null
        blurKey = ""
        return decoded
    }

    fun loadBackground(c: Context): Bitmap? {
        val base = loadRawBackground(c) ?: return null
        val ver = prefs(c).getLong("bg_ver", 0L)
        val level = getBlur(c)
        if (level <= 0) return base
        val key = "$ver|$level"
        val cached = blurCache
        if (cached != null && blurKey == key) return cached
        val out = makeBlurred(base, level)
        blurCache = out
        blurKey = key
        return out
    }

    /** Prévia pequena (desfoque + véu) para a tela de edição, sem mexer no que já está aplicado. */
    fun renderPreview(c: Context, src: Bitmap, dim: Int, blur: Int): Bitmap {
        val base = if (blur > 0) makeBlurred(src, blur, src.width) else src
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val veil = ColorUtils.setAlphaComponent(
            if (isNight(c)) Color.BLACK else Color.WHITE,
            dim * 255 / 100
        )
        Canvas(out).drawColor(veil)
        return out
    }

    /**
     * Desfoque suave: trabalha numa imagem reduzida (mais detalhe que antes) e o raio máximo
     * é bem menor, então o valor 100 deixa a imagem só levemente ofuscada, sem virar mancha.
     */
    private fun makeBlurred(src: Bitmap, level: Int, sw: Int = 540): Bitmap {
        val sh = (src.height * (sw.toFloat() / src.width)).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        val radius = (level * 0.1f * sw / 540f).roundToInt().coerceIn(1, 10)
        val tmp = IntArray(px.size)
        repeat(3) {
            blurPass(px, tmp, sw, sh, radius, true)
            blurPass(tmp, px, sw, sh, radius, false)
        }
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, sw, 0, 0, sw, sh)
        return out
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[base + i.coerceIn(0, len - 1) * step]
                sa += p ushr 24
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] =
                    ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + (i + r + 1).coerceAtMost(len - 1) * step]
                val sub = src[base + (i - r).coerceAtLeast(0) * step]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }

    /** Lê a imagem escolhida e corta no formato da tela. Ainda NÃO aplica nem guarda nada. */
    fun prepareBackground(c: Context, uri: Uri): Bitmap? {
        return try {
            val dm = c.resources.displayMetrics
            val tw = minOf(dm.widthPixels, dm.heightPixels)
            val th = maxOf(dm.widthPixels, dm.heightPixels)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val shortSide = minOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (shortSide / (sample * 2) >= tw) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

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

            result
        } catch (_: Throwable) {
            null
        }
    }

    /** Guarda a imagem escolhida junto com a intensidade e o desfoque, e passa a usá-la no app. */
    fun commitBackground(c: Context, bmp: Bitmap, dim: Int, blur: Int): Boolean {
        return try {
            bgFile(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            prefs(c).edit()
                .putBoolean("bg_on", true)
                .putLong("bg_ver", System.currentTimeMillis())
                .putInt("bg_dim", dim)
                .putInt("bg_blur", blur)
                .apply()
            cacheBmp = null
            cacheVer = -1L
            blurCache = null
            blurKey = ""
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun darker(c: Int): Int = ColorUtils.blendARGB(c, Color.BLACK, 0.62f)

    /** Fundo gradiente (versão escura das duas cores do tema) para ícones de pasta. */
    fun gradientIconBackground(c: Context, radiusDp: Float = 12f): GradientDrawable {
        val (s, e) = gradientColors(c)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(darker(s), darker(e))).apply {
            cornerRadius = radiusDp * c.resources.displayMetrics.density
        }
    }

    /** Barra deslizante com trilho e bolinha em gradiente. */
    fun styleSeekBar(c: Context, sb: SeekBar) {
        val d = c.resources.displayMetrics.density
        val (s, e) = gradientColors(c)
        val track = GradientDrawable().apply {
            setColor(0x33FFFFFF)
            cornerRadius = 3f * d
        }
        val fill = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(s, e)).apply {
            cornerRadius = 3f * d
        }
        val clip = ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        val layers = LayerDrawable(arrayOf<Drawable>(track, clip))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        val inset = (13f * d).toInt()
        layers.setLayerInset(0, 0, inset, 0, inset)
        layers.setLayerInset(1, 0, inset, 0, inset)
        sb.progressDrawable = layers
        val thumb = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(s, e)).apply {
            shape = GradientDrawable.OVAL
            setSize((22f * d).toInt(), (22f * d).toInt())
        }
        sb.thumb = thumb
        sb.splitTrack = false
    }

    /** Botão com fundo em gradiente (cantos totalmente arredondados) e brilho ao tocar. */
    fun styleGradientButton(c: Context, btn: Button) {
        val d = c.resources.displayMetrics.density
        val (s, e) = gradientColors(c)
        val shape = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(s, e)).apply {
            cornerRadius = 100f * d
        }
        val lum = ColorUtils.calculateLuminance(ColorUtils.blendARGB(s, e, 0.5f))
        btn.backgroundTintList = null
        btn.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
        btn.setTextColor(if (lum > 0.5) Color.BLACK else Color.WHITE)
    }

    /** Interruptor com trilho em gradiente quando ligado e bolinha branca. */
    fun styleSwitch(c: Context, sw: SwitchCompat) {
        val d = c.resources.displayMetrics.density
        val (s, e) = gradientColors(c)
        val trackW = (52f * d).toInt()
        val trackH = (28f * d).toInt()
        val trackOn = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(s, e)).apply {
            cornerRadius = 14f * d
            setSize(trackW, trackH)
        }
        val trackOff = GradientDrawable().apply {
            setColor(0x22FFFFFF)
            setStroke((2f * d).toInt(), 0x77FFFFFF)
            cornerRadius = 14f * d
            setSize(trackW, trackH)
        }
        val track = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), trackOn)
            addState(intArrayOf(), trackOff)
        }
        fun knob(color: Int, size: Float): Drawable {
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setSize((size * d).toInt(), (size * d).toInt())
            }
            return InsetDrawable(dot, ((28f - size) / 2f * d).toInt())
        }
        val thumb = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), knob(Color.WHITE, 22f))
            addState(intArrayOf(), knob(Color.parseColor("#B8B8C2"), 16f))
        }
        sw.thumbTintList = null
        sw.trackTintList = null
        sw.trackDrawable = track
        sw.thumbDrawable = thumb
    }

    /** Sem imagem: uma coloração leve do tema (gradiente suave) no fundo da tela. */
    private fun applyTint(a: Activity) {
        if (isAmoled(a)) return // AMOLED continua preto puro
        if (getAccentId(a) == "base") {
            // Tema base: mesmo fundo em gradiente da tela de abertura (cinza-azulado + índigo)
            a.window.setBackgroundDrawable(ContextCompat.getDrawable(a, R.drawable.base_bg))
            return
        }
        val base = ContextCompat.getColor(a, R.color.app_bg)
        val (s, e) = gradientColors(a)
        val top = ColorUtils.blendARGB(base, s, 0.24f)
        val bottom = ColorUtils.blendARGB(base, e, 0.16f)
        a.window.setBackgroundDrawable(
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, base, bottom))
        )
    }

    /** Põe a imagem (com um véu escuro/claro por cima) como fundo da janela. */
    fun applyBackground(a: Activity) {
        val bmp = loadBackground(a)
        if (bmp == null) {
            applyTint(a)
            return
        }
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

    // ---------- Tela inteira: a imagem aparece também atrás da barra de status ----------

    fun setupWindow(a: Activity, anchor: View) {
        WindowCompat.setDecorFitsSystemWindows(a.window, false)
        a.window.statusBarColor = Color.TRANSPARENT
        a.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) a.window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(a.window, anchor).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    /** Afasta o conteúdo das barras do sistema sem esconder o fundo atrás delas. */
    fun padWithInsets(v: View) {
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun edgeToEdge(a: Activity, root: View) {
        setupWindow(a, root)
        padWithInsets(root)
    }
}

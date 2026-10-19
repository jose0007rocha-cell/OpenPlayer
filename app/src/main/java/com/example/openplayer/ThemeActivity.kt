package com.example.openplayer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.openplayer.databinding.ActivityThemeBinding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ThemeActivity : AppCompatActivity() {

    private lateinit var b: ActivityThemeBinding

    /** Edição em andamento: nada disso vale para o app até tocar no ✓. */
    private class Pending(
        var bitmap: Bitmap?,      // imagem nova escolhida (null = só ajustando a que já está aplicada)
        var dim: Int,
        var blur: Int
    )

    private var pending: Pending? = null
    private var previewSrc: Bitmap? = null
    private val renderPool = Executors.newSingleThreadExecutor()
    private val renderTicket = AtomicInteger(0)

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Thread {
                    val bmp = ThemeManager.prepareBackground(this, uri)
                    runOnUiThread {
                        if (bmp != null) {
                            val p = pending ?: Pending(null, ThemeManager.getDim(this), ThemeManager.getBlur(this))
                            p.bitmap = bmp
                            pending = p
                            holder = p
                            previewSrc = makePreviewSource(bmp)
                            syncEditor()
                        } else {
                            Toast.makeText(this, R.string.bg_error, Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        ThemeManager.applyBackground(this)
        b = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(b.root)
        ThemeManager.edgeToEdge(this, b.root)

        // Se a tela foi recriada (ex.: troca de cor), continua a edição de onde parou
        pending = holder
        pending?.bitmap?.let { previewSrc = makePreviewSource(it) }
        if (previewSrc == null) {
            ThemeManager.loadRawBackground(this)?.let { previewSrc = makePreviewSource(it) }
        }

        b.btnBack.setOnClickListener { finish() }

        b.bgPreview.setOnClickListener { pickImage.launch("image/*") }
        b.btnBgPick.setOnClickListener { pickImage.launch("image/*") }
        b.btnBgRemove.setOnClickListener {
            holder = null
            ThemeManager.removeBackground(this)
            recreate()
        }

        ThemeManager.styleSeekBar(this, b.sliderDim)
        listen(b.sliderDim) { value ->
            edit().dim = value
            syncEditor(updateSliders = false)
        }
        ThemeManager.styleSeekBar(this, b.sliderBlur)
        listen(b.sliderBlur) { value ->
            edit().blur = value
            syncEditor(updateSliders = false)
        }

        // ✓ no canto: só aqui o fundo passa a valer no app
        val (g1, g2) = ThemeManager.gradientColors(this)
        b.btnApply.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(g1, g2)).apply {
            shape = GradientDrawable.OVAL
        }
        b.btnApply.setColorFilter(
            if (ColorUtils.calculateLuminance(ColorUtils.blendARGB(g1, g2, 0.5f)) > 0.5) Color.BLACK else Color.WHITE
        )
        b.btnApply.setOnClickListener { applyEdit() }

        syncEditor()

        ThemeManager.styleSwitch(this, b.switchAmoled)
        ThemeManager.styleGradientButton(this, b.btnBgPick)
        b.switchAmoled.isChecked = ThemeManager.isAmoled(this)
        b.switchAmoled.setOnCheckedChangeListener { _, on ->
            ThemeManager.setAmoled(this, on)
            recreate()
        }

        buildAccentGrid()
    }

    override fun onDestroy() {
        if (isFinishing) holder = null // saiu sem aplicar: descarta a edição
        renderPool.shutdownNow()
        super.onDestroy()
    }

    /** Edição atual (cria uma a partir do que já está aplicado, se ainda não existir). */
    private fun edit(): Pending {
        val p = pending ?: Pending(null, ThemeManager.getDim(this), ThemeManager.getBlur(this))
        pending = p
        holder = p
        return p
    }

    private fun makePreviewSource(src: Bitmap): Bitmap {
        val w = 270
        val h = (src.height * (w.toFloat() / src.width)).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun applyEdit() {
        val p = pending ?: return
        b.btnApply.isEnabled = false
        Thread {
            val bmp = p.bitmap
            val ok = if (bmp != null) {
                ThemeManager.commitBackground(this, bmp, p.dim, p.blur)
            } else {
                ThemeManager.setDim(this, p.dim)
                ThemeManager.setBlur(this, p.blur)
                true
            }
            runOnUiThread {
                b.btnApply.isEnabled = true
                if (ok) {
                    pending = null
                    holder = null
                    ThemeManager.applyBackground(this)
                    ThemeManager.loadRawBackground(this)?.let { previewSrc = makePreviewSource(it) }
                    syncEditor()
                    Toast.makeText(this, R.string.bg_applied, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.bg_error, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** Barra em passos de 5: avisa o valor (0..100) só quando o usuário mexe. */
    private fun listen(sb: SeekBar, onChange: (Int) -> Unit) {
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress * 5)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
    }

    /** Atualiza prévia, barras e o botão ✓ conforme a edição em andamento. */
    private fun syncEditor(updateSliders: Boolean = true) {
        val p = pending
        val hasSaved = ThemeManager.hasBackground(this)
        val hasImage = p?.bitmap != null || hasSaved
        val dim = p?.dim ?: ThemeManager.getDim(this)
        val blur = p?.blur ?: ThemeManager.getBlur(this)

        if (hasImage) {
            b.bgPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            b.btnBgRemove.visibility = if (hasSaved) View.VISIBLE else View.GONE
            for (v in listOf(b.dimLabel, b.sliderDim, b.blurLabel, b.sliderBlur)) v.visibility = View.VISIBLE
            if (updateSliders) {
                b.sliderDim.progress = dim.coerceIn(0, 90) / 5
                b.sliderBlur.progress = blur.coerceIn(0, 100) / 5
            }
            renderPreviewAsync(dim, blur)
        } else {
            b.bgPreview.scaleType = ImageView.ScaleType.CENTER
            b.bgPreview.setImageResource(R.drawable.ic_add_image)
            b.btnBgRemove.visibility = View.GONE
            for (v in listOf(b.dimLabel, b.sliderDim, b.blurLabel, b.sliderBlur)) v.visibility = View.GONE
        }

        val dirty = p != null && (
            p.bitmap != null ||
                p.dim != ThemeManager.getDim(this) ||
                p.blur != ThemeManager.getBlur(this)
            )
        b.btnApply.visibility = if (dirty) View.VISIBLE else View.GONE
    }

    private fun renderPreviewAsync(dim: Int, blur: Int) {
        val src = previewSrc ?: return
        val ticket = renderTicket.incrementAndGet()
        try {
            renderPool.execute {
                val out = ThemeManager.renderPreview(this, src, dim, blur)
                runOnUiThread {
                    if (ticket == renderTicket.get() && !isDestroyed) b.bgPreview.setImageBitmap(out)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildAccentGrid() {
        val current = ThemeManager.getAccentId(this)
        b.accentGrid.removeAllViews()
        for (accent in ThemeManager.accents) {
            val color = ContextCompat.getColor(this, accent.colorRes)
            val colorEnd = ContextCompat.getColor(this, accent.endColorRes)
            val textColor =
                if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
            val swatch = TextView(this).apply {
                text = if (accent.id == current) "✓" else ""
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(textColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setOrientation(GradientDrawable.Orientation.TL_BR)
                    setColors(intArrayOf(color, colorEnd))
                }
                contentDescription = getString(accent.nameRes)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(36)
                    height = dp(36)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                setOnClickListener {
                    ThemeManager.setAccent(this@ThemeActivity, accent.id)
                    recreate()
                }
            }
            b.accentGrid.addView(swatch)
        }
    }

    private companion object {
        /** Guarda a edição enquanto a tela é recriada (troca de cor etc.). */
        private var holder: Pending? = null
    }
}

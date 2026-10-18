package com.example.openplayer

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

class ThemeActivity : AppCompatActivity() {

    private lateinit var b: ActivityThemeBinding

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Thread {
                    val ok = ThemeManager.saveBackground(this, uri)
                    runOnUiThread {
                        if (ok) {
                            ThemeManager.applyBackground(this)
                            refreshBackground()
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

        b.btnBack.setOnClickListener { finish() }

        b.bgPreview.setOnClickListener { pickImage.launch("image/*") }
        b.btnBgPick.setOnClickListener { pickImage.launch("image/*") }
        b.btnBgRemove.setOnClickListener {
            ThemeManager.removeBackground(this)
            recreate()
        }
        ThemeManager.styleSeekBar(this, b.sliderDim)
        b.sliderDim.progress = ThemeManager.getDim(this).coerceIn(0, 90) / 5
        listen(b.sliderDim) { value ->
            ThemeManager.setDim(this, value)
            ThemeManager.applyBackground(this)
        }
        ThemeManager.styleSeekBar(this, b.sliderBlur)
        b.sliderBlur.progress = ThemeManager.getBlur(this).coerceIn(0, 100) / 5
        listen(b.sliderBlur) { value ->
            ThemeManager.setBlur(this, value)
            ThemeManager.applyBackground(this)
            ThemeManager.loadBackground(this)?.let { b.bgPreview.setImageBitmap(it) }
        }
        refreshBackground()

        b.switchAmoled.isChecked = ThemeManager.isAmoled(this)
        b.switchAmoled.setOnCheckedChangeListener { _, on ->
            ThemeManager.setAmoled(this, on)
            recreate()
        }

        buildAccentGrid()
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

    private fun refreshBackground() {
        val bmp = ThemeManager.loadBackground(this)
        if (bmp != null) {
            b.bgPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            b.bgPreview.setImageBitmap(bmp)
            b.btnBgRemove.visibility = View.VISIBLE
            b.dimLabel.visibility = View.VISIBLE
            b.sliderDim.visibility = View.VISIBLE
            b.blurLabel.visibility = View.VISIBLE
            b.sliderBlur.visibility = View.VISIBLE
        } else {
            b.bgPreview.scaleType = ImageView.ScaleType.CENTER
            b.bgPreview.setImageResource(R.drawable.ic_add_image)
            b.btnBgRemove.visibility = View.GONE
            b.dimLabel.visibility = View.GONE
            b.sliderDim.visibility = View.GONE
            b.blurLabel.visibility = View.GONE
            b.sliderBlur.visibility = View.GONE
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
}

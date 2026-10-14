package com.example.openplayer

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
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

        b.bgPreview.setOnClickListener { pickImage.launch("image/*") }
        b.btnBgPick.setOnClickListener { pickImage.launch("image/*") }
        b.btnBgRemove.setOnClickListener {
            ThemeManager.removeBackground(this)
            recreate()
        }
        b.sliderDim.value = ThemeManager.getDim(this).toFloat().coerceIn(0f, 90f)
        b.sliderDim.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                ThemeManager.setDim(this, value.toInt())
                ThemeManager.applyBackground(this)
            }
        }
        refreshBackground()

        val checked = when (ThemeManager.getMode(this)) {
            ThemeManager.MODE_LIGHT -> R.id.rbLight
            ThemeManager.MODE_DARK -> R.id.rbDark
            else -> R.id.rbSystem
        }
        b.modeGroup.check(checked)
        b.modeGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rbLight -> ThemeManager.MODE_LIGHT
                R.id.rbDark -> ThemeManager.MODE_DARK
                else -> ThemeManager.MODE_SYSTEM
            }
            ThemeManager.setMode(this, mode)
        }

        b.switchAmoled.isChecked = ThemeManager.isAmoled(this)
        b.switchAmoled.setOnCheckedChangeListener { _, on ->
            ThemeManager.setAmoled(this, on)
            recreate()
        }

        buildAccentGrid()
    }

    private fun refreshBackground() {
        val bmp = ThemeManager.loadBackground(this)
        if (bmp != null) {
            b.bgPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            b.bgPreview.setImageBitmap(bmp)
            b.btnBgRemove.visibility = View.VISIBLE
            b.dimLabel.visibility = View.VISIBLE
            b.sliderDim.visibility = View.VISIBLE
        } else {
            b.bgPreview.scaleType = ImageView.ScaleType.CENTER
            b.bgPreview.setImageResource(R.drawable.ic_add_image)
            b.btnBgRemove.visibility = View.GONE
            b.dimLabel.visibility = View.GONE
            b.sliderDim.visibility = View.GONE
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildAccentGrid() {
        val current = ThemeManager.getAccentId(this)
        b.accentGrid.removeAllViews()
        for (accent in ThemeManager.accents) {
            val color = ContextCompat.getColor(this, accent.colorRes)
            val textColor =
                if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
            val swatch = TextView(this).apply {
                text = if (accent.id == current) "✓" else ""
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(textColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                contentDescription = getString(accent.nameRes)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(56)
                    height = dp(56)
                    setMargins(dp(8), dp(8), dp(8), dp(8))
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

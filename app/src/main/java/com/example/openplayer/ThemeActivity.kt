package com.example.openplayer

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.openplayer.databinding.ActivityThemeBinding

class ThemeActivity : AppCompatActivity() {

    private lateinit var b: ActivityThemeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        b = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(b.root)

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

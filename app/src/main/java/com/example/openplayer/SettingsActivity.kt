package com.example.openplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.openplayer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        ThemeManager.applyBackground(this)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        ThemeManager.edgeToEdge(this, b.root)

        b.btnBack.setOnClickListener { finish() }

        b.switchSubs.isChecked = AppSettings.subtitlesOn(this)
        b.switchSubs.setOnCheckedChangeListener { _, on -> AppSettings.setSubtitlesOn(this, on) }

        b.rowRotation.setOnClickListener {
            choose(R.string.settings_rotation, R.array.rotation_options, AppSettings.rotation(this)) {
                AppSettings.setRotation(this, it)
                refresh()
            }
        }
        b.rowSubSize.setOnClickListener {
            choose(R.string.settings_sub_size, R.array.sub_size_options, AppSettings.subSize(this)) {
                AppSettings.setSubSize(this, it)
                refresh()
            }
        }
        b.rowSubStyle.setOnClickListener {
            choose(R.string.settings_sub_style, R.array.sub_style_options, AppSettings.subStyle(this)) {
                AppSettings.setSubStyle(this, it)
                refresh()
            }
        }
        refresh()
    }

    private fun refresh() {
        b.rotationValue.text = resources.getStringArray(R.array.rotation_options)[AppSettings.rotation(this)]
        b.subSizeValue.text = resources.getStringArray(R.array.sub_size_options)[AppSettings.subSize(this)]
        b.subStyleValue.text = resources.getStringArray(R.array.sub_style_options)[AppSettings.subStyle(this)]
    }

    private fun choose(title: Int, itemsRes: Int, current: Int, onPick: (Int) -> Unit) {
        PopupDialog.show(
            this,
            getString(title),
            listOf(PopupDialog.Group(null, resources.getStringArray(itemsRes).toList(), current, onPick)),
            true
        )
    }
}

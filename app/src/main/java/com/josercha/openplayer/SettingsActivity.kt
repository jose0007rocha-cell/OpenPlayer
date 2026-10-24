package com.josercha.openplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.josercha.openplayer.databinding.ActivitySettingsBinding

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

        ThemeManager.styleSwitch(this, b.switchSubs)
        b.switchSubs.isChecked = AppSettings.subtitlesOn(this)
        b.switchSubs.setOnCheckedChangeListener { _, on -> AppSettings.setSubtitlesOn(this, on) }

        ThemeManager.styleSwitch(this, b.switchBgPlay)
        b.switchBgPlay.isChecked = AppSettings.backgroundPlay(this)
        b.switchBgPlay.setOnCheckedChangeListener { _, on -> AppSettings.setBackgroundPlay(this, on) }

        b.rowRotation.setOnClickListener {
            choose(R.string.settings_rotation, R.array.rotation_options, AppSettings.rotation(this)) {
                AppSettings.setRotation(this, it)
                refresh()
            }
        }
        b.rowSubRender.setOnClickListener { chooseSubRender() }
        b.aboutName.text = "OpenPlayer V" + AboutDialog.installedVersion(this)
        b.rowAbout.setOnClickListener { AboutDialog.show(this) }
        refresh()
    }

    private fun refresh() {
        b.rotationValue.text = resources.getStringArray(R.array.rotation_options)[AppSettings.rotation(this)]
        b.subRenderValue.text = resources.getStringArray(R.array.sub_render_options)[AppSettings.subRender(this)]
    }

    /** "Renderização da legenda": por enquanto só "Mantenha o estilo integrado" (a legenda como veio no arquivo). */
    private fun chooseSubRender() {
        PopupDialog.show(
            this,
            getString(R.string.subs_render),
            listOf(
                PopupDialog.Group(
                    null,
                    resources.getStringArray(R.array.sub_render_options).toList(),
                    AppSettings.subRender(this),
                    listOf(getString(R.string.sub_render_integrated_desc))
                ) {
                    AppSettings.setSubRender(this, it)
                    refresh()
                }
            ),
            true
        )
    }

    private fun choose(title: Int, itemsRes: Int, current: Int, onPick: (Int) -> Unit) {
        PopupDialog.show(
            this,
            getString(title),
            listOf(PopupDialog.Group(null, resources.getStringArray(itemsRes).toList(), current, onPick = onPick)),
            true
        )
    }
}

package com.example.openplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.example.openplayer.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var themeSig = ""

    // Os fragments recarregam a lista no onResume, depois que o diálogo fecha.
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestPermission() {
        permLauncher.launch(VideoRepository.permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        ThemeManager.applyBackground(this)
        themeSig = ThemeManager.signature(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (ThemeManager.hasBackground(this)) {
            val surface = MaterialColors.getColor(
                b.bottomNav, com.google.android.material.R.attr.colorSurface
            )
            b.bottomNav.setBackgroundColor(ColorUtils.setAlphaComponent(surface, 0xD9))
        }

        b.btnUrl.setOnClickListener { askUrl() }
        b.btnTheme.setOnClickListener { startActivity(Intent(this, ThemeActivity::class.java)) }

        b.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_folders -> FolderListFragment()
                R.id.nav_videos -> VideoListFragment.newInstance(false)
                else -> VideoListFragment.newInstance(true)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
            true
        }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = R.id.nav_folders

        if (!VideoRepository.hasPermission(this)) requestPermission()
    }

    override fun onResume() {
        super.onResume()
        if (themeSig != ThemeManager.signature(this)) recreate()
    }

    private fun askUrl() {
        val input = EditText(this).apply {
            hint = getString(R.string.url_hint)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.open_url)
            .setView(input)
            .setPositiveButton(R.string.play) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) openVideo(Uri.parse(text))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

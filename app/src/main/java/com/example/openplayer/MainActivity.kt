package com.example.openplayer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.openplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val adapter = VideoAdapter { openVideo(it.uri) }

    private val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadVideos() else showEmpty(getString(R.string.need_permission), true)
        }

    private var themeSig = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        themeSig = ThemeManager.signature(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.btnUrl.setOnClickListener { askUrl() }
        b.btnTheme.setOnClickListener { startActivity(Intent(this, ThemeActivity::class.java)) }
        b.btnGrant.setOnClickListener { permLauncher.launch(permission) }

        if (hasPermission()) loadVideos() else permLauncher.launch(permission)
    }

    override fun onResume() {
        super.onResume()
        if (themeSig != ThemeManager.signature(this)) recreate()
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun showEmpty(text: String, showButton: Boolean) {
        b.emptyText.text = text
        b.btnGrant.visibility = if (showButton) View.VISIBLE else View.GONE
        b.emptyBox.visibility = View.VISIBLE
        b.list.visibility = View.GONE
    }

    private fun loadVideos() {
        Thread {
            val result = ArrayList<Video>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )
            try {
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(iId)
                        )
                        result.add(
                            Video(uri, c.getString(iName) ?: "?", c.getLong(iDur), c.getLong(iSize))
                        )
                    }
                }
            } catch (_: Exception) {
            }
            runOnUiThread {
                if (result.isEmpty()) {
                    showEmpty(getString(R.string.no_videos), false)
                } else {
                    b.emptyBox.visibility = View.GONE
                    b.list.visibility = View.VISIBLE
                    adapter.submit(result)
                }
            }
        }.start()
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

    private fun openVideo(uri: Uri) {
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
            }
        )
    }
}

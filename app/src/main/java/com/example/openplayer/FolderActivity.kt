package com.example.openplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.openplayer.databinding.ActivityFolderBinding

class FolderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "folder_id"
        const val EXTRA_NAME = "folder_name"
    }

    private lateinit var b: ActivityFolderBinding
    private val adapter = VideoAdapter { openVideo(it.uri) }
    private var folderId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        ThemeManager.applyBackground(this)
        b = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(b.root)
        ThemeManager.edgeToEdge(this, b.root)

        folderId = intent.getLongExtra(EXTRA_ID, 0L)
        b.folderTitle.text = intent.getStringExtra(EXTRA_NAME) ?: ""
        b.btnBack.setOnClickListener { finish() }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        if (!VideoRepository.hasPermission(this)) return
        val ctx = applicationContext
        Thread {
            val list = VideoRepository.load(ctx).filter { it.folderId == folderId }
            runOnUiThread { adapter.submit(list) }
        }.start()
    }
}

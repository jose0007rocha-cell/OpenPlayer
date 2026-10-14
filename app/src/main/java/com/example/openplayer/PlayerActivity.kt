package com.example.openplayer

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.openplayer.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var uri: Uri? = null
    private val prefs by lazy { getSharedPreferences("positions", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, b.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        uri = intent?.data
        if (uri == null) finish()
    }

    override fun onStart() {
        super.onStart()
        val u = uri ?: return
        val p = ExoPlayer.Builder(this).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, R.string.playback_error, Toast.LENGTH_LONG).show()
            }
        })
        b.playerView.player = p
        p.setMediaItem(MediaItem.fromUri(u))
        val saved = prefs.getLong(u.toString(), 0L)
        if (saved > 0) p.seekTo(saved)
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    override fun onStop() {
        super.onStop()
        val p = player
        val u = uri
        if (p != null && u != null) {
            val dur = p.duration
            val pos = p.currentPosition
            val key = u.toString()
            if (dur > 0 && pos > dur - 3000) prefs.edit().remove(key).apply()
            else prefs.edit().putLong(key, pos).apply()
        }
        b.playerView.player = null
        p?.release()
        player = null
    }
}

package com.example.openplayer

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerActivity : AppCompatActivity() {

    private companion object {
        const val MODE_NONE = 0
        const val MODE_BRIGHTNESS = 1
        const val MODE_VOLUME = 2
        const val MODE_SEEK = 3
        const val SEEK_FULL_WIDTH_MS = 120_000L
    }

    private lateinit var b: ActivityPlayerBinding
    private lateinit var audio: AudioManager
    private var player: ExoPlayer? = null
    private var uri: Uri? = null
    private val prefs by lazy { getSharedPreferences("positions", MODE_PRIVATE) }

    private var gestureMode = MODE_NONE
    private var startBrightness = 0.5f
    private var startVolume = 0f
    private var seekStartPos = 0L
    private var seekTarget = 0L

    private val hideIndicator = Runnable { b.indicator.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, b.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setupGestures()

        uri = intent?.data
        if (uri == null) finish()
    }

    // ---------- Gestos: esquerda = brilho, direita = volume, horizontal = avançar/voltar ----------

    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (b.playerView.isControllerFullyVisible) b.playerView.hideController()
                else b.playerView.showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return true
                val w = b.playerView.width.toFloat()
                when {
                    e.x < w / 3f -> seekBy(-10_000L)
                    e.x > w * 2f / 3f -> seekBy(10_000L)
                    else -> p.playWhenReady = !p.playWhenReady
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
            ): Boolean {
                if (e1 == null) return false
                val w = b.playerView.width.toFloat()
                val h = b.playerView.height.toFloat()
                if (w <= 0f || h <= 0f) return false
                val totalX = e2.x - e1.x
                val totalY = e2.y - e1.y

                if (gestureMode == MODE_NONE) {
                    gestureMode = when {
                        abs(totalY) > abs(totalX) ->
                            if (e1.x < w / 2f) MODE_BRIGHTNESS else MODE_VOLUME
                        else -> MODE_SEEK
                    }
                    when (gestureMode) {
                        MODE_BRIGHTNESS -> startBrightness = currentBrightness()
                        MODE_VOLUME -> startVolume =
                            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                        MODE_SEEK -> seekStartPos = player?.currentPosition ?: 0L
                    }
                }

                when (gestureMode) {
                    MODE_BRIGHTNESS -> {
                        val v = (startBrightness - totalY / (h * 0.8f)).coerceIn(0.01f, 1f)
                        val lp = window.attributes
                        lp.screenBrightness = v
                        window.attributes = lp
                        showIndicator(R.drawable.ic_brightness, (v * 100).roundToInt(), "${(v * 100).roundToInt()}%")
                    }
                    MODE_VOLUME -> {
                        val v = (startVolume - totalY / (h * 0.8f)).coerceIn(0f, 1f)
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
                        showIndicator(R.drawable.ic_volume, (v * 100).roundToInt(), "${(v * 100).roundToInt()}%")
                    }
                    MODE_SEEK -> {
                        val dur = player?.duration ?: 0L
                        var t = seekStartPos + (totalX / w * SEEK_FULL_WIDTH_MS).toLong()
                        t = if (dur > 0) t.coerceIn(0L, dur) else t.coerceAtLeast(0L)
                        seekTarget = t
                        val delta = (t - seekStartPos) / 1000
                        val sign = if (delta >= 0) "+" else "-"
                        showIndicator(null, null, "${formatTime(t)}  ($sign${abs(delta)}s)")
                    }
                }
                return true
            }
        })

        b.playerView.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    if (gestureMode == MODE_SEEK) player?.seekTo(seekTarget)
                    gestureMode = MODE_NONE
                }
                MotionEvent.ACTION_CANCEL -> gestureMode = MODE_NONE
            }
            true
        }
    }

    private fun currentBrightness(): Float {
        val v = window.attributes.screenBrightness
        if (v >= 0f) return v
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val dur = p.duration
        var t = p.currentPosition + deltaMs
        t = if (dur > 0) t.coerceIn(0L, dur) else t.coerceAtLeast(0L)
        p.seekTo(t)
        val secs = deltaMs / 1000
        showIndicator(null, null, if (secs > 0) "+${secs}s" else "${secs}s")
    }

    private fun showIndicator(icon: Int?, progress: Int?, text: String) {
        b.indIcon.visibility = if (icon != null) View.VISIBLE else View.GONE
        if (icon != null) b.indIcon.setImageResource(icon)
        b.indBar.visibility = if (progress != null) View.VISIBLE else View.GONE
        if (progress != null) b.indBar.progress = progress
        b.indText.text = text
        b.indicator.visibility = View.VISIBLE
        b.indicator.removeCallbacks(hideIndicator)
        b.indicator.postDelayed(hideIndicator, 800)
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    // ---------- Reprodução ----------

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

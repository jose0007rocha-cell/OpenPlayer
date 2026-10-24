package com.josercha.openplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import com.josercha.openplayer.databinding.ActivityPlayerBinding
import com.google.android.material.color.MaterialColors
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
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

        // Todas as telas do player dividem o mesmo ExoPlayer (no serviço). Só a tela mais nova é a "dona":
        // uma tela antiga que está sendo fechada não pode soltar a imagem da nova.
        var ownerToken = 0
    }

    private lateinit var b: ActivityPlayerBinding
    private lateinit var audio: AudioManager
    private var player: Player? = null // MediaController ligado ao PlaybackService
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var uri: Uri? = null
    private var myToken = 0

    private var gestureMode = MODE_NONE
    private var startBrightness = 0.5f
    private var startVolume = 0f
    private var seekStartPos = 0L
    private var seekTarget = 0L
    private var keepPlaying = true // lembra se estava tocando ou pausado ao sair da tela

    private val hideIndicator = Runnable { b.indicator.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        applyThemeColors()
        requestedOrientation = AppSettings.orientationFlag(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, b.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setupGestures()
        setupSubtitleButton()
        b.playerView.setKeepContentOnPlayerReset(true)
        b.playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

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
                        try {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
                        } catch (_: SecurityException) {
                            // alguns aparelhos (modo não perturbe) bloqueiam a mudança de volume
                        }
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

        b.playerView.setOnTouchListener { _, ev: MotionEvent ->
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

    private val errorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@PlayerActivity, R.string.playback_error, Toast.LENGTH_LONG).show()
            // Arquivo apagado/movido ou sem permissão: não adianta ficar na tela preta
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
            ) finish()
        }
    }

    /** Conecta ao PlaybackService (o ExoPlayer vive lá) e liga a tela ao player dele. */
    override fun onStart() {
        super.onStart()
        val u = uri ?: return
        if (controllerFuture != null) return
        b.playerView.player = null // ligação velha (de antes de parar) fora; a nova entra em attach()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            // A tela pode ter sido parada antes de a conexão terminar
            if (controllerFuture !== future) return@addListener
            val controller = try {
                future.get()
            } catch (_: Exception) {
                controllerFuture = null
                Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show()
                finish()
                return@addListener
            }
            attach(controller, uri ?: u)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun attach(c: MediaController, u: Uri) {
        myToken = ++ownerToken
        player = c
        c.addListener(errorListener)
        b.playerView.player = c
        loadVideo(c, u)
    }

    /** Liga a tela ao vídeo: continua o que já toca no serviço ou começa o novo. */
    private fun loadVideo(c: Player, u: Uri) {
        applySubtitleTracks(c) // também garante o vídeo ligado

        val sameVideo = c.currentMediaItem?.mediaId == u.toString() &&
            c.playbackState != Player.STATE_IDLE
        if (sameVideo) {
            // Voltou para um vídeo que continuou tocando em segundo plano
            if (!AppSettings.backgroundPlay(this)) c.playWhenReady = keepPlaying
            // A tela nova ainda não recebeu nenhum quadro: um pequeno "seek" no mesmo ponto força o desenho
            b.playerView.postDelayed({
                if (player === c && c.playbackState == Player.STATE_READY) c.seekTo(c.currentPosition)
            }, 350)
        } else {
            val item = MediaItem.Builder()
                .setUri(u)
                .setMediaId(u.toString())
                .setSubtitleConfigurations(findSubtitles(u))
                .build()
            val saved = Positions.get(this, u)
            c.setMediaItem(item, if (saved > 0) saved else C.TIME_UNSET)
            c.prepare()
            c.playWhenReady = keepPlaying
        }
    }

    /**
     * Tocar na notificação (ou abrir outro vídeo por um app externo) com o player já aberto chega aqui,
     * em vez de criar uma segunda tela do player por cima da primeira.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val u = intent.data ?: return
        if (u == uri) return
        uri = u
        player?.let { loadVideo(it, u) }
    }

    @OptIn(UnstableApi::class)
    /** Procura legendas ao lado do vídeo (filme.srt, filme.pt.srt, .vtt, .ass...). */
    private fun findSubtitles(u: Uri): List<MediaItem.SubtitleConfiguration> {
        val result = ArrayList<MediaItem.SubtitleConfiguration>()
        try {
            val path = if (u.scheme == "file") u.path else queryPath(u)
            if (path.isNullOrEmpty()) return result
            val video = File(path)
            val dir = video.parentFile ?: return result
            val base = video.nameWithoutExtension
            val files = dir.listFiles() ?: return result
            for (f in files.sortedBy { it.name }) {
                if (!f.isFile) continue
                val mime = when (f.extension.lowercase()) {
                    "srt" -> MimeTypes.APPLICATION_SUBRIP
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> continue
                }
                val name = f.nameWithoutExtension
                if (!name.startsWith(base, ignoreCase = true)) continue
                val rest = name.substring(base.length)
                if (rest.isNotEmpty() && rest[0] !in ".-_ ") continue
                val lang = rest.trim('.', '-', '_', ' ').takeIf { it.length in 2..3 }
                result.add(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f))
                        .setMimeType(mime)
                        .setLanguage(lang)
                        .setSelectionFlags(if (result.isEmpty()) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun queryPath(u: Uri): String? = try {
        contentResolver.query(u, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    // ---------- Botão de legenda (ativa / desativa) ----------

    @OptIn(UnstableApi::class)
    private fun setupSubtitleButton() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val lp = b.btnSubs.layoutParams as android.widget.FrameLayout.LayoutParams
            val d = resources.displayMetrics.density
            lp.topMargin = cut.top + (12 * d).toInt()
            lp.marginEnd = cut.right + (16 * d).toInt()
            b.btnSubs.layoutParams = lp
            insets
        }
        refreshSubtitleIcon()
        b.btnSubs.setOnClickListener {
            val on = !AppSettings.subtitlesOn(this)
            AppSettings.setSubtitlesOn(this, on)
            player?.let { applySubtitleTracks(it) }
            refreshSubtitleIcon()
            showIndicator(null, null, getString(if (on) R.string.subs_now_on else R.string.subs_now_off))
        }
        b.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                b.btnSubs.visibility = if (visibility == View.VISIBLE) View.VISIBLE else View.GONE
            }
        )
        b.btnSubs.visibility = if (b.playerView.isControllerFullyVisible) View.VISIBLE else View.GONE
    }

    private fun refreshSubtitleIcon() {
        val on = AppSettings.subtitlesOn(this)
        b.btnSubs.setImageResource(if (on) R.drawable.ic_subtitles else R.drawable.ic_subtitles_off)
        b.btnSubs.imageAlpha = if (on) 255 else 150
    }

    // ---------- Ícones e barra de progresso na cor do tema ----------

    @OptIn(UnstableApi::class)
    private fun applyThemeColors() {
        val accent = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.WHITE
        )
        // Nota: O Media3 não permite mudar a cor do TimeBar via código facilmente, 
        // então deixamos a cor padrão do tema para evitar erros de compilação.
        b.indBar.progressTintList = ColorStateList.valueOf(accent)
        b.indIcon.setColorFilter(accent)
        b.btnSubs.setColorFilter(accent)
    }

    private fun applySubtitleTracks(p: Player) {
        val enabled = AppSettings.subtitlesOn(this)
        // Com a tela aberta o vídeo sempre fica ligado (no fundo ele é desligado só para poupar bateria)
        val params = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
        if (enabled) {
            params.setPreferredTextLanguage(Locale.getDefault().language)
            params.setSelectUndeterminedTextLanguage(true)
        }
        p.trackSelectionParameters = params.build()
    }

    override fun onStop() {
        super.onStop()
        val p = player
        val u = uri
        val owner = myToken == ownerToken
        if (p != null && owner) {
            keepPlaying = p.playWhenReady
            if (u != null) Positions.save(this, u, p.currentPosition, p.duration)
            b.playerView.player = null
            p.removeListener(errorListener)
            when {
                // Saiu do player (botão voltar ou erro): para tudo e encerra o serviço
                isFinishing -> {
                    p.stop()
                    p.clearMediaItems()
                }
                // Foi para o início/outro app com o som ligado: continua tocando (sem tela)
                AppSettings.backgroundPlay(this) && p.playWhenReady -> Unit
                // Segundo plano desligado: pausa (e lembra se estava tocando)
                else -> p.pause()
            }
        } else if (p != null) {
            // Uma tela mais nova já assumiu o player: esta só solta a conexão, sem mexer no vídeo dela
            p.removeListener(errorListener)
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player = null
        if (isFinishing && owner) stopService(Intent(this, PlaybackService::class.java))
    }
}

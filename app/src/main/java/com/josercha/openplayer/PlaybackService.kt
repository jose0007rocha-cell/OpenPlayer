package com.josercha.openplayer

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Serviço de reprodução: o ExoPlayer mora aqui. Ele dá a notificação de mídia (play, pause, avançar),
 * os controles da tela de bloqueio e mantém o som tocando com o app em segundo plano.
 * A PlayerActivity se conecta a ele por um MediaController.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val io: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    private companion object {
        const val ROOT_ID = "root"
        const val FOLDER_PREFIX = "folder:"
    }

    private val callback = object : MediaLibrarySession.Callback {

        // ---------- Navegação (Android Auto): raiz > pastas > vídeos ----------

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            io.submit(Callable<LibraryResult<ImmutableList<MediaItem>>> {
                val all = children(parentId)
                val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
                val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
                LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
            })

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            io.submit(Callable<LibraryResult<MediaItem>> {
                val item = findItem(mediaId)
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
                }
            })

        /** Completa o item (título, pasta e capa) antes de ele ir para o player. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            io.submit(Callable { mediaItems.map { enrich(it) }.toMutableList() })
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) savePosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateSessionActivity(mediaItem)
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel)
                .build()
        )

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(this, renderers)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        player.setSeekParameters(SeekParameters.EXACT)
        player.addListener(playerListener)

        session = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** App fechado nos recentes: se nada está tocando, o serviço encerra. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            p?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        session?.run {
            player.release()
            release()
        }
        session = null
        io.shutdown()
        super.onDestroy()
    }

    // ---------- Biblioteca para o Android Auto ----------

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(getString(R.string.app_name))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun folderItem(f: Folder): MediaItem = MediaItem.Builder()
        .setMediaId(FOLDER_PREFIX + f.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(f.name)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    /** O id do vídeo é o próprio endereço dele; o onAddMediaItems usa isso para tocar. */
    private fun videoItem(v: Video): MediaItem = MediaItem.Builder()
        .setMediaId(v.uri.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(v.name.substringBeforeLast('.'))
                .setArtist(v.folderName)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                .build()
        )
        .build()

    private fun children(parentId: String): List<MediaItem> {
        // Sem a permissão de vídeos, a lista vem vazia (o usuário libera no celular)
        if (!VideoRepository.hasPermission(this)) return emptyList()
        val videos = VideoRepository.load(this)
        return when {
            parentId == ROOT_ID -> VideoRepository.folders(videos).map { folderItem(it) }
            parentId.startsWith(FOLDER_PREFIX) -> {
                val id = parentId.removePrefix(FOLDER_PREFIX).toLongOrNull() ?: return emptyList()
                videos.filter { it.folderId == id }.map { videoItem(it) }
            }
            else -> emptyList()
        }
    }

    private fun findItem(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) return rootItem()
        if (!VideoRepository.hasPermission(this)) return null
        val videos = VideoRepository.load(this)
        if (mediaId.startsWith(FOLDER_PREFIX)) {
            val id = mediaId.removePrefix(FOLDER_PREFIX).toLongOrNull() ?: return null
            return VideoRepository.folders(videos).firstOrNull { it.id == id }?.let { folderItem(it) }
        }
        return videos.firstOrNull { it.uri.toString() == mediaId }?.let { videoItem(it) }
    }

    // ---------- Posição e notificação ----------

    private fun savePosition() {
        val p = session?.player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        if (id.isEmpty()) return
        Positions.save(this, Uri.parse(id), p.currentPosition, p.duration)
    }

    /** Tocar na notificação abre o player no vídeo que está tocando. */
    private fun updateSessionActivity(item: MediaItem?) {
        val id = item?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        val intent = Intent(this, PlayerActivity::class.java).apply {
            data = Uri.parse(id)
            // Reaproveita a tela do player que já existe (CLEAR_TOP + SINGLE_TOP), sem criar outra por cima
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session?.setSessionActivity(pi)
    }

    // ---------- Metadados (título, pasta, capa) ----------

    private fun enrich(item: MediaItem): MediaItem {
        val uri = item.localConfiguration?.uri
            ?: item.mediaId.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
            ?: return item
        val b = item.buildUpon()
        if (item.localConfiguration == null) b.setUri(uri)
        if (item.mediaId == MediaItem.DEFAULT_MEDIA_ID) b.setMediaId(uri.toString())

        val md = item.mediaMetadata
        if (md.title != null && md.artworkData != null) return b.build()

        val (title, folder) = describe(uri)
        val mb = md.buildUpon()
        if (md.title == null) mb.setTitle(title)
        if (md.artist == null && folder != null) mb.setArtist(folder)
        if (md.artworkData == null) {
            thumbnail(uri)?.let { mb.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        }
        return b.setMediaMetadata(mb.build()).build()
    }

    /** Nome (sem a extensão) e pasta do vídeo. */
    private fun describe(uri: Uri): Pair<String?, String?> {
        var name: String? = null
        var folder: String? = null
        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_DISPLAY_NAME),
                null, null, null
            )?.use {
                if (it.moveToFirst()) {
                    val n = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val f = it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    if (n >= 0) name = it.getString(n)
                    if (f >= 0) folder = it.getString(f)
                }
            }
        } catch (_: Exception) {
        }
        val title = (name ?: uri.lastPathSegment)?.substringBeforeLast('.')
        return Pair(title, folder)
    }

    /** Capa em JPEG pequeno, tirada do próprio vídeo. Links da internet ficam sem capa. */
    private fun thumbnail(uri: Uri): ByteArray? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = try {
                contentResolver.loadThumbnail(uri, Size(512, 288), null)
            } catch (_: Exception) {
                null
            }
        }
        if (bmp == null) bmp = frameOf(uri)
        val b = bmp ?: return null
        return try {
            val max = 512f
            val scale = minOf(1f, max / maxOf(b.width, b.height))
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
            } else {
                b
            }
            ByteArrayOutputStream().use {
                small.compress(Bitmap.CompressFormat.JPEG, 85, it)
                it.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun frameOf(uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }
}

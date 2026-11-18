package com.kadri.stalkerplayerpro.player

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.kadri.stalkerplayerpro.data.network.HttpClientProvider
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale

enum class ActiveEngineType(val label: String) {
    EXOPLAYER("ExoPlayer (Media3)"),
    LIBVLC("LibVLC (Software/Surround)")
}

data class AudioTrackInfo(
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val vlcTrackId: Int = -1
)

/**
 * محرك التشغيل المزدوج للتلفاز:
 * 1. يبدأ بـ ExoPlayer (Media3) للأداء العتادي السريع واستهلاك الذاكرة المنخفض.
 * 2. يكتشف تلقائياً ترميزات الصوت المحيطية غير المدعومة عتادياً (AC3 / EAC3 / DTS / TrueHD)
 *    أو أخطاء الفك العتادي، ويحول البث فوراً إلى LibVLC في نفس موضع التوقف.
 */
@OptIn(UnstableApi::class)
class DualPlayerController(
    private val context: Context,
    private val preferredAudioLang: String = "ar"
) {
    var activeEngine by mutableStateOf(ActiveEngineType.EXOPLAYER)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isBuffering by mutableStateOf(true)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var audioTracks by mutableStateOf<List<AudioTrackInfo>>(emptyList())
        private set

    var engineStatusNotice by mutableStateOf<String?>(null)
        private set

    private var currentUrl: String = ""
    private var pendingSeekMs: Long = 0L
    private var autoSwitchedForUrl: Boolean = false

    private var exoPlayer: ExoPlayer? = null
    private var libVlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private var attachedVlcLayout: VLCVideoLayout? = null

    fun playStream(url: String, startPositionMs: Long = 0L, forceVlc: Boolean = false) {
        if (url.isBlank()) return
        currentUrl = url
        pendingSeekMs = startPositionMs.coerceAtLeast(0L)
        autoSwitchedForUrl = forceVlc
        isBuffering = true
        engineStatusNotice = null

        if (forceVlc) {
            startLibVlc(url, pendingSeekMs)
        } else {
            startExoPlayer(url, pendingSeekMs)
        }
    }

    fun switchEngineManually() {
        if (currentUrl.isBlank()) return
        val pos = getLivePositionMs()
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            autoSwitchedForUrl = true
            startLibVlc(currentUrl, pos)
        } else {
            autoSwitchedForUrl = false
            startExoPlayer(currentUrl, pos)
        }
    }

    private fun startExoPlayer(url: String, seekMs: Long) {
        releaseVlcOnly()
        activeEngine = ActiveEngineType.EXOPLAYER

        val exo = exoPlayer ?: buildExoPlayer().also { exoPlayer = it }
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exo.setMediaItem(mediaItem)
        if (seekMs > 0L) {
            exo.seekTo(seekMs)
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun buildExoPlayer(): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(HttpClientProvider.client)
            .setUserAgent("Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 MAG250")

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(preferredAudioLang)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractExoAudioTracks(tracks)
                if (!autoSwitchedForUrl && hasUnsupportedSurroundAudio(tracks)) {
                    autoSwitchedForUrl = true
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    engineStatusNotice = "AC3/EAC3/DTS -> LibVLC"
                    startLibVlc(currentUrl, pos)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!autoSwitchedForUrl && currentUrl.isNotBlank()) {
                    autoSwitchedForUrl = true
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    engineStatusNotice = "Fallback -> LibVLC"
                    startLibVlc(currentUrl, pos)
                } else {
                    isBuffering = false
                }
            }
        })
        return player
    }

    private fun hasUnsupportedSurroundAudio(tracks: Tracks): Boolean {
        var foundAudioGroup = false
        var hasAnySupportedAudio = false
        var hasUnsupportedSurround = false

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                foundAudioGroup = true
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val mime = format.sampleMimeType?.lowercase(Locale.US).orEmpty()
                    val isSurround = mime == MimeTypes.AUDIO_AC3 ||
                            mime == MimeTypes.AUDIO_E_AC3 ||
                            mime == MimeTypes.AUDIO_E_AC3_JOC ||
                            mime == MimeTypes.AUDIO_DTS ||
                            mime == MimeTypes.AUDIO_DTS_HD ||
                            mime == MimeTypes.AUDIO_DTS_X ||
                            mime == MimeTypes.AUDIO_TRUEHD ||
                            mime.contains("ac3") ||
                            mime.contains("dts")

                    if (group.isTrackSupported(i)) {
                        hasAnySupportedAudio = true
                    } else if (isSurround) {
                        hasUnsupportedSurround = true
                    }
                }
            }
        }
        return foundAudioGroup && hasUnsupportedSurround && !hasAnySupportedAudio
    }

    private fun extractExoAudioTracks(tracks: Tracks) {
        val list = ArrayList<AudioTrackInfo>()
        tracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIdx)
                    val lang = fmt.language ?: "und"
                    val label = fmt.label?.takeIf { it.isNotBlank() }
                        ?: "${lang.uppercase(Locale.US)} (${fmt.sampleMimeType?.substringAfter('/') ?: "Audio"})"
                    list.add(
                        AudioTrackInfo(
                            id = "exo_${gIdx}_$tIdx",
                            label = label,
                            language = lang,
                            isSelected = group.isTrackSelected(tIdx),
                            groupIndex = gIdx,
                            trackIndex = tIdx
                        )
                    )
                }
            }
        }
        audioTracks = list
    }

    private fun startLibVlc(url: String, seekMs: Long) {
        releaseExoOnly()
        activeEngine = ActiveEngineType.LIBVLC
        isBuffering = true

        val vlc = libVlc ?: LibVLC(
            context,
            arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "--network-caching=1500"
            )
        ).also { libVlc = it }

        val mp = vlcPlayer ?: MediaPlayer(vlc).also { player ->
            vlcPlayer = player
            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> {
                        if (event.buffering >= 100f) isBuffering = false
                    }
                    MediaPlayer.Event.Playing -> {
                        isPlaying = true
                        isBuffering = false
                        durationMs = player.length.coerceAtLeast(0L)
                        if (pendingSeekMs > 0L) {
                            player.time = pendingSeekMs
                            pendingSeekMs = 0L
                        }
                        refreshVlcAudioTracks(player)
                    }
                    MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        currentPositionMs = event.timeChanged.coerceAtLeast(0L)
                    }
                }
            }
        }

        attachedVlcLayout?.let { layout ->
            if (!mp.vlcVout.areViewsAttached()) {
                mp.attachViews(layout, null, false, false)
            }
        }

        pendingSeekMs = seekMs
        val media = Media(vlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1500")
        }
        mp.media = media
        media.release()
        mp.play()
    }

    private fun refreshVlcAudioTracks(player: MediaPlayer) {
        val currentId = player.audioTrack
        val tracks = player.audioTracks ?: return
        val list = tracks.filter { it.id >= 0 }.map { td ->
            AudioTrackInfo(
                id = "vlc_${td.id}",
                label = td.name ?: "Track ${td.id}",
                language = td.name ?: "",
                isSelected = (td.id == currentId),
                vlcTrackId = td.id
            )
        }
        audioTracks = list
        // محاولة اختيار اللغة المفضلة تلقائياً
        val prefTrack = list.find { it.language.contains(preferredAudioLang, ignoreCase = true) }
        if (prefTrack != null && !prefTrack.isSelected) {
            player.audioTrack = prefTrack.vlcTrackId
        }
    }

    fun selectAudioTrack(track: AudioTrackInfo) {
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            val exo = exoPlayer ?: return
            val group = exo.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            exo.trackSelectionParameters = exo.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                .build()
        } else {
            val mp = vlcPlayer ?: return
            if (track.vlcTrackId >= 0) {
                mp.audioTrack = track.vlcTrackId
                refreshVlcAudioTracks(mp)
            }
        }
    }

    fun togglePlayPause() {
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        } else {
            vlcPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    fun seekBy(deltaMs: Long) {
        val target = (getLivePositionMs() + deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        } else {
            vlcPlayer?.time = positionMs.coerceAtLeast(0L)
        }
        currentPositionMs = positionMs.coerceAtLeast(0L)
    }

    fun jumpToLiveEdge() {
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            exoPlayer?.seekToDefaultPosition()
        } else {
            val len = vlcPlayer?.length ?: 0L
            if (len > 0L) vlcPlayer?.time = len
        }
    }

    fun syncPositionTick(): Long {
        val pos = getLivePositionMs()
        currentPositionMs = pos
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            exoPlayer?.let { dur ->
                if (dur.duration > 0L) durationMs = dur.duration
            }
        }
        return pos
    }

    fun getLivePositionMs(): Long {
        return if (activeEngine == ActiveEngineType.EXOPLAYER) {
            exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: currentPositionMs
        } else {
            vlcPlayer?.time?.coerceAtLeast(0L) ?: currentPositionMs
        }
    }

    fun attachExoView(playerView: PlayerView) {
        playerView.player = exoPlayer
    }

    fun attachVlcLayout(layout: VLCVideoLayout) {
        attachedVlcLayout = layout
        vlcPlayer?.let { mp ->
            if (!mp.vlcVout.areViewsAttached()) {
                mp.attachViews(layout, null, false, false)
            }
        }
    }

    fun detachVlcLayout() {
        vlcPlayer?.let { mp ->
            if (mp.vlcVout.areViewsAttached()) {
                mp.detachViews()
            }
        }
        attachedVlcLayout = null
    }

    private fun releaseExoOnly() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun releaseVlcOnly() {
        detachVlcLayout()
        vlcPlayer?.stop()
        vlcPlayer?.release()
        vlcPlayer = null
        libVlc?.release()
        libVlc = null
    }

    fun releaseAll() {
        releaseExoOnly()
        releaseVlcOnly()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DualVideoSurface(
    controller: DualPlayerController,
    modifier: Modifier = Modifier
) {
    if (controller.activeEngine == ActiveEngineType.EXOPLAYER) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    controller.attachExoView(this)
                }
            },
            update = { view ->
                controller.attachExoView(view)
            }
        )
    } else {
        DisposableEffect(Unit) {
            onDispose { controller.detachVlcLayout() }
        }
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    controller.attachVlcLayout(this)
                }
            },
            update = { layout ->
                controller.attachVlcLayout(layout)
            }
        )
    }
}

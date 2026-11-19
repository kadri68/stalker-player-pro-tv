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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import com.kadri.stalkerplayerpro.data.network.HttpClientProvider
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale

enum class ActiveEngineType(val label: String) {
    EXOPLAYER("ExoPlayer (Media3)"),
    LIBVLC("LibVLC (All Codecs)")
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
    private var hasRenderedFirstVideoFrame: Boolean = false
    private var exoPlayingWithoutVideoStartMs: Long = 0L

    private var exoPlayer: ExoPlayer? = null
    private var libVlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private var attachedVlcLayout: VLCVideoLayout? = null

    fun playStream(url: String, startPositionMs: Long = 0L, forceVlc: Boolean = false) {
        if (url.isBlank()) return
        currentUrl = url
        pendingSeekMs = startPositionMs.coerceAtLeast(0L)
        autoSwitchedForUrl = forceVlc
        hasRenderedFirstVideoFrame = false
        exoPlayingWithoutVideoStartMs = 0L
        isBuffering = true
        engineStatusNotice = null

        val lower = url.lowercase(Locale.US)
        val requiresDirectVlc = forceVlc ||
                lower.startsWith("rtmp://") ||
                lower.startsWith("udp://") ||
                lower.startsWith("mms://") ||
                lower.endsWith(".avi") ||
                lower.endsWith(".flv") ||
                lower.endsWith(".wmv")

        if (requiresDirectVlc) {
            autoSwitchedForUrl = true
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
        hasRenderedFirstVideoFrame = false
        exoPlayingWithoutVideoStartMs = 0L

        val exo = exoPlayer ?: buildExoPlayer().also { exoPlayer = it }
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
        val lower = url.lowercase(Locale.US)
        when {
            lower.contains(".m3u8") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.contains(".mpd") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            lower.contains(".ism") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
        }

        exo.setMediaItem(mediaItemBuilder.build())
        if (seekMs > 0L) {
            exo.seekTo(seekMs)
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun buildExoPlayer(): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(HttpClientProvider.client)
            .setUserAgent("Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 MAG250")

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        // تخفيض زمن بدء التشغيل الأولي إلى 650ms لفتح القنوات والأفلام فوراً
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(6_000, 35_000, 650, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setLoadControl(loadControl)
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
                if (playing && !hasRenderedFirstVideoFrame && exoPlayingWithoutVideoStartMs == 0L) {
                    exoPlayingWithoutVideoStartMs = System.currentTimeMillis()
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstVideoFrame = true
                exoPlayingWithoutVideoStartMs = 0L
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    hasRenderedFirstVideoFrame = true
                    exoPlayingWithoutVideoStartMs = 0L
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractExoAudioTracks(tracks)
                if (!autoSwitchedForUrl && (hasUnsupportedSurroundAudio(tracks) || hasUnsupportedVideoTrack(tracks))) {
                    autoSwitchedForUrl = true
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    engineStatusNotice = "Auto -> LibVLC"
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

    private fun hasUnsupportedVideoTrack(tracks: Tracks): Boolean {
        var foundVideoGroup = false
        var hasSupportedVideo = false
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                foundVideoGroup = true
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        hasSupportedVideo = true
                        break
                    }
                }
            }
        }
        return foundVideoGroup && !hasSupportedVideo
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
                "--http-reconnect",
                "--network-caching=1200",
                "--live-caching=1200",
                "--file-caching=1200",
                "--avcodec-hw=any",
                "--codec=all"
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
            addOption(":network-caching=1200")
            addOption(":http-reconnect")
            addOption(":demux=any")
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
            val exo = exoPlayer
            if (exo != null) {
                if (exo.duration > 0L) durationMs = exo.duration

                if (!autoSwitchedForUrl && exo.isPlaying && !hasRenderedFirstVideoFrame && currentUrl.isNotBlank()) {
                    if (exoPlayingWithoutVideoStartMs == 0L) {
                        exoPlayingWithoutVideoStartMs = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - exoPlayingWithoutVideoStartMs >= 3000L) {
                        autoSwitchedForUrl = true
                        engineStatusNotice = "Video Codec -> LibVLC"
                        startLibVlc(currentUrl, pos)
                        return pos
                    }
                }
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
        runCatching {
            exoPlayer?.stop()
            exoPlayer?.release()
        }
        exoPlayer = null
    }

    private fun releaseVlcOnly() {
        runCatching {
            detachVlcLayout()
            vlcPlayer?.stop()
            vlcPlayer?.release()
            libVlc?.release()
        }
        vlcPlayer = null
        libVlc = null
    }

    fun releaseAll() {
        currentUrl = ""
        isPlaying = false
        isBuffering = false
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
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
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
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
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

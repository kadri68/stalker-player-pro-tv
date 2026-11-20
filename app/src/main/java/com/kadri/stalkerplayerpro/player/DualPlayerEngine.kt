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

data class EmbeddedSubtitleTrackInfo(
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val vlcSpuId: Int = -1
)

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
    private val preferredAudioLang: String = "en",
    private val preferredSubtitleLang: String = "ar",
    initialSubColor: Long = 0xFFFFD600,
    initialSubSizeSp: Float = 36f,
    initialSubBgAlpha: Float = 0.65f
) {
    var subTextColor by mutableLongStateOf(initialSubColor)
        private set
    var subFontSizeSp by mutableStateOf(initialSubSizeSp)
        private set
    var subBgAlpha by mutableStateOf(initialSubBgAlpha)
        private set

    fun updateSubtitleAppearance(color: Long, sizeSp: Float, bgAlpha: Float) {
        subTextColor = color
        subFontSizeSp = sizeSp
        subBgAlpha = bgAlpha
    }
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

    var embeddedSubtitleTracks by mutableStateOf<List<EmbeddedSubtitleTrackInfo>>(emptyList())
        private set

    var engineStatusNotice by mutableStateOf<String?>(null)
        private set

    var isReconnecting by mutableStateOf(false)
        private set

    var liveEmbeddedCueText by mutableStateOf("")
        private set

    var hasExternalSubtitlesActive by mutableStateOf(false)

    fun needsStreamRecovery(): Boolean {
        if (isManuallyPaused) return false
        return if (activeEngine == ActiveEngineType.EXOPLAYER) {
            val exo = exoPlayer
            exo == null || exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED || exo.playerError != null || (!exo.isPlaying && !isBuffering)
        } else {
            val mp = vlcPlayer
            val st = mp?.playerState ?: -1
            mp == null || st == org.videolan.libvlc.interfaces.IMedia.State.Stopped || st == org.videolan.libvlc.interfaces.IMedia.State.Ended || st == org.videolan.libvlc.interfaces.IMedia.State.Error || (!mp.isPlaying && !isBuffering)
        }
    }

    private var currentUrl: String = ""
    private var isLiveStream: Boolean = false
    private var isManuallyPaused: Boolean = false
    private var lastReconnectAttemptMs: Long = 0L
    private var engineNoticeShownAtMs: Long = 0L
    private var selectedVlcSpuId: Int? = null
    private var hasAppliedInitialVlcAudioPref: Boolean = false
    private var hasAppliedInitialExoTextPref: Boolean = false
    private var hasAppliedInitialVlcSpuPref: Boolean = false
    private var pendingSeekMs: Long = 0L
    private var autoSwitchedForUrl: Boolean = false
    private var hasRenderedFirstVideoFrame: Boolean = false
    private var exoPlayingWithoutVideoStartMs: Long = 0L

    private var exoPlayer: ExoPlayer? = null
    private var libVlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private var attachedVlcLayout: VLCVideoLayout? = null

    fun playStream(url: String, startPositionMs: Long = 0L, forceVlc: Boolean = false, isLive: Boolean = false) {
        if (url.isBlank()) return
        currentUrl = url
        isLiveStream = isLive
        isManuallyPaused = false
        isReconnecting = false
        hasAppliedInitialVlcAudioPref = false
        hasAppliedInitialExoTextPref = false
        hasAppliedInitialVlcSpuPref = false
        embeddedSubtitleTracks = emptyList()
        liveEmbeddedCueText = ""
        hasExternalSubtitlesActive = false
        selectedVlcSpuId = null
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
                    refreshExoTextTracks(player)
                    isReconnecting = false
                    if (engineStatusNotice?.contains("Reconnecting") == true) {
                        engineStatusNotice = null
                    }
                } else if (playbackState == Player.STATE_ENDED && isLiveStream && !isManuallyPaused && currentUrl.isNotBlank()) {
                    isReconnecting = true
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing && !hasRenderedFirstVideoFrame && exoPlayingWithoutVideoStartMs == 0L) {
                    exoPlayingWithoutVideoStartMs = System.currentTimeMillis()
                }
            }

            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                val combined = cueGroup.cues
                    .mapNotNull { it.text?.toString() }
                    .joinToString("\n")
                liveEmbeddedCueText = sanitizeSubtitleText(combined)
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
                    exoPlayer?.let { refreshExoTextTracks(it) }
                if (!autoSwitchedForUrl && (hasUnsupportedSurroundAudio(tracks) || hasUnsupportedVideoTrack(tracks))) {
                    autoSwitchedForUrl = true
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    engineStatusNotice = "Auto -> LibVLC"; engineNoticeShownAtMs = System.currentTimeMillis()
                    startLibVlc(currentUrl, pos)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!autoSwitchedForUrl && currentUrl.isNotBlank()) {
                    autoSwitchedForUrl = true
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    engineStatusNotice = "Fallback -> LibVLC"; engineNoticeShownAtMs = System.currentTimeMillis()
                    startLibVlc(currentUrl, pos)
                } else if (isLiveStream && !isManuallyPaused && currentUrl.isNotBlank()) {
                    isReconnecting = true
                    isBuffering = true
                    engineStatusNotice = "إعادة الاتصال بالبث... / Reconnecting..."
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

        val vlcRgb = (subTextColor and 0x00FFFFFFL).toInt()
        val vlcRelSize = when {
            subFontSizeSp >= 80f -> 7
            subFontSizeSp >= 66f -> 8
            subFontSizeSp >= 52f -> 10
            subFontSizeSp >= 40f -> 12
            subFontSizeSp >= 30f -> 15
            else -> 19
        }
        val vlcBgAlpha = (subBgAlpha.coerceIn(0f, 1f) * 255f).toInt()
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
                "--codec=all",
                "--sub-track=0",
                "--sub-margin=36",
                "--freetype-bold",
                "--freetype-color=$vlcRgb",
                "--freetype-rel-fontsize=$vlcRelSize",
                "--freetype-background-color=0",
                "--freetype-background-opacity=$vlcBgAlpha"
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
                        isReconnecting = false
                        if (engineStatusNotice?.contains("Reconnecting") == true) {
                            engineStatusNotice = null
                        }
                        durationMs = player.length.coerceAtLeast(0L)
                        if (pendingSeekMs > 0L) {
                            player.time = pendingSeekMs
                            pendingSeekMs = 0L
                        }
                        refreshVlcAudioTracks(player)
                    refreshVlcSpuTracks(player)
                    }
                    MediaPlayer.Event.Paused -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached, MediaPlayer.Event.EncounteredError -> {
                        isPlaying = false
                        if (isLiveStream && !isManuallyPaused && currentUrl.isNotBlank()) {
                            isReconnecting = true
                            isBuffering = true
                            engineStatusNotice = "إعادة الاتصال بالبث... / Reconnecting..."
                        } else {
                            isBuffering = false
                        }
                    }
                    MediaPlayer.Event.ESAdded, MediaPlayer.Event.Vout -> {
                        refreshVlcAudioTracks(player)
                        refreshVlcSpuTracks(player)
                        val activeSub = embeddedSubtitleTracks.find { it.isSelected }
                        if (activeSub != null && activeSub.vlcSpuId >= 0 && player.spuTrack != activeSub.vlcSpuId) {
                            player.spuTrack = activeSub.vlcSpuId
                        }
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        currentPositionMs = event.timeChanged.coerceAtLeast(0L)
                    }
                }
            }
        }

        attachedVlcLayout?.let { layout ->
            if (!mp.vlcVout.areViewsAttached()) {
                mp.attachViews(layout, null, true, false)
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
        if (!hasAppliedInitialVlcAudioPref) {
            hasAppliedInitialVlcAudioPref = true
            val prefTrack = list.find { matchesSubLang(it.language, it.label, preferredAudioLang) }
            if (prefTrack != null && !prefTrack.isSelected) {
                player.audioTrack = prefTrack.vlcTrackId
                audioTracks = list.map { it.copy(isSelected = it.id == prefTrack.id) }
            }
        }
    }

    private fun matchesSubLang(rawLang: String, rawLabel: String, pref: String): Boolean {
        val l = rawLang.lowercase(Locale.US)
        val n = rawLabel.lowercase(Locale.US)
        val p = pref.lowercase(Locale.US)
        if (p.contains("ar") && (l.startsWith("ar") || n.contains("arab") || n.contains("عرب"))) return true
        if (p.contains("en") && (l.startsWith("en") || n.contains("engl") || n.contains("انجل") || n.contains("إنجل"))) return true
        if (p.contains("fr") && (l.startsWith("fr") || n.contains("fren") || n.contains("fran") || n.contains("فرنس"))) return true
        return l.startsWith(p.substringBefore(",").trim())
    }

    private fun refreshExoTextTracks(player: ExoPlayer) {
        val list = ArrayList<EmbeddedSubtitleTrackInfo>()
        player.currentTracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIdx)
                    val lang = fmt.language.orEmpty()
                    val langName = if (lang.isNotBlank()) Locale.forLanguageTag(lang).displayLanguage.takeIf { it.isNotBlank() && it != lang } ?: lang.uppercase(Locale.US) else ""
                    val rawLabel = fmt.label?.takeIf { it.isNotBlank() } ?: langName.ifBlank { "Subtitle ${list.size + 1}" }
                    val fullLabel = if (langName.isNotBlank() && !rawLabel.contains(langName, ignoreCase = true)) "$rawLabel ($langName)" else rawLabel
                    list.add(
                        EmbeddedSubtitleTrackInfo(
                            id = "exo_sub_${gIdx}_${tIdx}",
                            label = fullLabel,
                            language = lang,
                            isSelected = group.isTrackSelected(tIdx),
                            groupIndex = gIdx,
                            trackIndex = tIdx
                        )
                    )
                }
            }
        }
        embeddedSubtitleTracks = list
        if (!hasAppliedInitialExoTextPref && !hasExternalSubtitlesActive && list.isNotEmpty()) {
            hasAppliedInitialExoTextPref = true
            if (preferredSubtitleLang.equals("off", ignoreCase = true)) {
                selectEmbeddedSubtitleTrack(null)
            } else {
                val target = list.find { matchesSubLang(it.language, it.label, preferredSubtitleLang) }
                    ?: list.find { it.isSelected }
                    ?: list.first()
                selectEmbeddedSubtitleTrack(target)
            }
        }
    }

    private fun refreshVlcSpuTracks(player: MediaPlayer) {
        val currentId = player.spuTrack
        val tracks = player.spuTracks ?: return
        val list = tracks.filter { it.id >= 0 }.map { td ->
            EmbeddedSubtitleTrackInfo(
                id = "vlc_sub_${td.id}",
                label = td.name ?: "Subtitle ${td.id}",
                language = td.name ?: "",
                isSelected = (td.id == currentId),
                vlcSpuId = td.id
            )
        }
        embeddedSubtitleTracks = list
        if (!hasAppliedInitialVlcSpuPref && !hasExternalSubtitlesActive && list.isNotEmpty()) {
            hasAppliedInitialVlcSpuPref = true
            if (preferredSubtitleLang.equals("off", ignoreCase = true)) {
                player.spuTrack = -1
                embeddedSubtitleTracks = list.map { it.copy(isSelected = false) }
            } else {
                val target = list.find { matchesSubLang(it.language, it.label, preferredSubtitleLang) }
                    ?: list.find { it.isSelected }
                    ?: list.first()
                player.spuTrack = target.vlcSpuId
                embeddedSubtitleTracks = list.map { it.copy(isSelected = it.id == target.id) }
            }
        }
    }

    fun selectEmbeddedSubtitleTrack(track: EmbeddedSubtitleTrackInfo?) {
        liveEmbeddedCueText = ""
        hasAppliedInitialExoTextPref = true
        hasAppliedInitialVlcSpuPref = true
        if (track != null) {
            hasExternalSubtitlesActive = false
        }
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            val exo = exoPlayer ?: return
            if (track == null) {
                exo.trackSelectionParameters = exo.trackSelectionParameters
                    .buildUpon()
                    .setPreferredTextLanguage(null)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                embeddedSubtitleTracks = embeddedSubtitleTracks.map { it.copy(isSelected = false) }
            } else {
                val group = exo.currentTracks.groups.getOrNull(track.groupIndex) ?: return
                exo.trackSelectionParameters = exo.trackSelectionParameters
                    .buildUpon()
                    .setPreferredTextLanguage(null)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                    .build()
                embeddedSubtitleTracks = embeddedSubtitleTracks.map { it.copy(isSelected = it.id == track.id) }
            }
        } else {
            val mp = vlcPlayer ?: return
            hasAppliedInitialVlcSpuPref = true
            if (track == null) {
                mp.spuTrack = -1
                embeddedSubtitleTracks = embeddedSubtitleTracks.map { it.copy(isSelected = false) }
            } else if (track.vlcSpuId >= 0) {
                attachedVlcLayout?.let { layout ->
                    if (!mp.vlcVout.areViewsAttached()) {
                        mp.attachViews(layout, null, true, false)
                    }
                }
                mp.spuTrack = track.vlcSpuId
                embeddedSubtitleTracks = embeddedSubtitleTracks.map { it.copy(isSelected = it.id == track.id) }
            }
        }
    }

    fun selectAudioTrack(track: AudioTrackInfo) {
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            val exo = exoPlayer ?: return
            val group = exo.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            exo.trackSelectionParameters = exo.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguage(null)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                .build()
            audioTracks = audioTracks.map { it.copy(isSelected = it.id == track.id) }
        } else {
            val mp = vlcPlayer ?: return
            if (track.vlcTrackId >= 0) {
                hasAppliedInitialVlcAudioPref = true
                mp.audioTrack = track.vlcTrackId
                audioTracks = audioTracks.map { it.copy(isSelected = it.id == track.id) }
            }
        }
    }

    fun togglePlayPause() {
        if (activeEngine == ActiveEngineType.EXOPLAYER) {
            val exo = exoPlayer
            if (exo != null && exo.isPlaying) {
                isManuallyPaused = true
                isReconnecting = false
                exo.pause()
            } else if (currentUrl.isNotBlank()) {
                val wasManualPause = isManuallyPaused
                isManuallyPaused = false
                val needsReconnect = exo == null ||
                    !wasManualPause ||
                    exo.playbackState == Player.STATE_IDLE ||
                    exo.playbackState == Player.STATE_ENDED ||
                    exo.playerError != null
                if (needsReconnect) {
                    val resumePos = if (isLiveStream) 0L else getLivePositionMs()
                    isBuffering = true
                    startExoPlayer(currentUrl, resumePos)
                } else {
                    exo.play()
                }
            }
        } else {
            val mp = vlcPlayer
            if (mp != null && mp.isPlaying) {
                isManuallyPaused = true
                isReconnecting = false
                mp.pause()
            } else if (currentUrl.isNotBlank()) {
                val wasManualPause = isManuallyPaused
                isManuallyPaused = false
                val st = mp?.playerState ?: -1
                val needsReconnect = mp == null ||
                    !wasManualPause ||
                    st == org.videolan.libvlc.interfaces.IMedia.State.Stopped ||
                    st == org.videolan.libvlc.interfaces.IMedia.State.Ended ||
                    st == org.videolan.libvlc.interfaces.IMedia.State.Error
                if (needsReconnect) {
                    val resumePos = if (isLiveStream) 0L else getLivePositionMs()
                    isBuffering = true
                    startLibVlc(currentUrl, resumePos)
                } else {
                    mp.play()
                }
            }
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
        if (engineStatusNotice != null && !isReconnecting && System.currentTimeMillis() - engineNoticeShownAtMs >= 3000L) {
            engineStatusNotice = null
        }
        if (isLiveStream && currentUrl.isNotBlank() && !isManuallyPaused) {
            val exoNeedsReconnect = activeEngine == ActiveEngineType.EXOPLAYER &&
                (isReconnecting || exoPlayer?.playbackState == Player.STATE_IDLE || exoPlayer?.playbackState == Player.STATE_ENDED)
            val vlcNeedsReconnect = activeEngine == ActiveEngineType.LIBVLC &&
                (isReconnecting || (vlcPlayer?.isPlaying == false && !isBuffering))
            if ((exoNeedsReconnect || vlcNeedsReconnect) && System.currentTimeMillis() - lastReconnectAttemptMs >= 1500L) {
                lastReconnectAttemptMs = System.currentTimeMillis()
                isReconnecting = true
                isBuffering = true
                engineStatusNotice = "إعادة الاتصال بالبث... / Reconnecting..."
                if (activeEngine == ActiveEngineType.EXOPLAYER) startExoPlayer(currentUrl, 0L)
                else startLibVlc(currentUrl, 0L)
                return pos
            }
        }
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

    private fun ensureVlcSubtitleOverlay(layout: VLCVideoLayout) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is android.view.SurfaceView && i > 0) {
                runCatching {
                    child.setZOrderMediaOverlay(true)
                    child.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                }
            }
        }
    }

    fun attachVlcLayout(layout: VLCVideoLayout) {
        attachedVlcLayout = layout
        ensureVlcSubtitleOverlay(layout)
        vlcPlayer?.let { mp ->
            if (!mp.vlcVout.areViewsAttached()) {
                mp.attachViews(layout, null, true, false)
            }
            val activeSub = embeddedSubtitleTracks.find { it.isSelected }
            if (activeSub != null && activeSub.vlcSpuId >= 0) {
                mp.spuTrack = activeSub.vlcSpuId
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
        isLiveStream = false
        isManuallyPaused = false
        isReconnecting = false
        isPlaying = false
        isBuffering = false
        releaseExoOnly()
        releaseVlcOnly()
    }
}

private fun applySubtitleStyleToView(
    subtitleView: androidx.media3.ui.SubtitleView?,
    colorLong: Long,
    sizeSp: Float,
    bgAlpha: Float
) {
    subtitleView?.visibility = android.view.View.GONE
}

@OptIn(UnstableApi::class)
@Composable
fun DualVideoSurface(
    controller: DualPlayerController,
    subTextColor: Long = controller.subTextColor,
    subFontSizeSp: Float = controller.subFontSizeSp,
    subBgAlpha: Float = controller.subBgAlpha,
    modifier: Modifier = Modifier
) {
    if (controller.activeEngine == ActiveEngineType.EXOPLAYER) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    applySubtitleStyleToView(subtitleView, subTextColor, subFontSizeSp, subBgAlpha)
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
                applySubtitleStyleToView(view.subtitleView, subTextColor, subFontSizeSp, subBgAlpha)
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

package com.kadri.stalkerplayerpro.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvFocusTrap
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvHorizontalStepItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.FavoriteEntity
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.local.WatchHistoryEntity
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.data.model.SubtitleCue
import com.kadri.stalkerplayerpro.data.model.SubtitleSearchResult
import com.kadri.stalkerplayerpro.data.network.StalkerApiClient
import com.kadri.stalkerplayerpro.data.network.SubtitleRepository
import com.kadri.stalkerplayerpro.data.network.XtreamApiClient
import com.kadri.stalkerplayerpro.ui.components.TvActionMenuDialog
import com.kadri.stalkerplayerpro.ui.components.TvMenuOption
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.screens.resolvePlayableUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun TvPlayerScreen(
    initialRequest: PlaybackRequest,
    dao: AppDao,
    settings: TvUserSettings,
    settingsStore: SettingsDataStore,
    isAr: Boolean,
    isInPipMode: Boolean = false,
    onEnterPip: (() -> Unit)? = null,
    onExitPlayer: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val componentActivity = activity as? ComponentActivity
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()
    val liveSettings by settingsStore.settingsFlow.collectAsState(initial = settings)

    var currentRequest by remember { mutableStateOf(initialRequest) }
    var currentChannelIdx by remember { mutableIntStateOf(initialRequest.currentChannelIndex) }
    var previousChannelIdx by remember { mutableIntStateOf(initialRequest.currentChannelIndex) }
    var currentEpgTitle by remember { mutableStateOf(initialRequest.subtitleQueryHint) }

    val favorites by dao.observeFavorites(currentRequest.portal.id, currentRequest.contentType)
        .collectAsState(initial = emptyList())
    val watchHistory by dao.observeWatchHistory(currentRequest.portal.id)
        .collectAsState(initial = emptyList())

    val isCurrentFavorite = remember(favorites, currentRequest.itemId) {
        favorites.any { it.itemId == currentRequest.itemId }
    }

    val controller = remember {
        DualPlayerController(
            context = context,
            preferredAudioLang = settings.preferredAudioLang,
            preferredSubtitleLang = settings.preferredSubtitleLang,
            embeddedSubtitlesAutoEnabled = settings.embeddedSubtitlesAutoEnabled,
            initialSubColor = settings.subTextColor,
            initialSubSizeSp = settings.subFontSizeSp,
            initialSubBgAlpha = settings.subBgAlpha
        )
    }
    val rootFocusRequester = remember { FocusRequester() }
    val bottomBarInitialFocusRequester = remember { FocusRequester() }

    var showControls by remember { mutableStateOf(true) }
    var controlInteractTick by remember { mutableIntStateOf(0) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showHistoryDrawer by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showSubtitleSearchDialog by remember { mutableStateOf(false) }
    var showSubtitleSyncBar by remember { mutableStateOf(false) }
    var showSubtitleStyleDialog by remember { mutableStateOf(false) }
    var showAudioTracksDialog by remember { mutableStateOf(false) }
    var showEmbeddedSubtitlesDialog by remember { mutableStateOf(false) }
    var showEngineHudBadge by remember { mutableStateOf(true) }
    LaunchedEffect(controller.activeEngine, controller.engineStatusNotice) {
        showEngineHudBadge = true
        delay(3000L)
        showEngineHudBadge = false
    }

    var surfaceWidthPx by remember { mutableFloatStateOf(1080f) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var volumeFraction by remember {
        val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(curV.toFloat() / maxV.toFloat())
    }
    var brightnessFraction by remember {
        val initialB = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        mutableFloatStateOf(initialB)
    }
    var activeGestureHud by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var gestureHudTick by remember { mutableIntStateOf(0) }

    var digitBuffer by remember { mutableStateOf("") }

    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var subColor by remember { mutableLongStateOf(settings.subTextColor) }
    var subSizeSp by remember { mutableFloatStateOf(settings.subFontSizeSp) }
    var subBgAlpha by remember { mutableFloatStateOf(settings.subBgAlpha) }

    var clockText by remember { mutableStateOf(LocaleUtils.formatClock()) }

    val anyOverlayModal = showChannelDrawer || showHistoryDrawer || showQuickMenu ||
            showSubtitleSearchDialog || showSubtitleStyleDialog || showAudioTracksDialog || showEmbeddedSubtitlesDialog

    // إيقاف المشغل وتحريره نهائياً عند إغلاق نافذة PiP أو الضغط على زر الهوم (ON_STOP)
    DisposableEffect(componentActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val stillInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        (componentActivity?.isInPictureInPictureMode == true)
                if (!stillInPip) {
                    controller.releaseAll()
                    onExitPlayer()
                }
            }
        }
        componentActivity?.lifecycle?.addObserver(observer)
        onDispose {
            componentActivity?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(gestureHudTick) {
        if (activeGestureHud != null) {
            delay(1300)
            activeGestureHud = null
        }
    }

    LaunchedEffect(liveSettings.subTextColor, liveSettings.subFontSizeSp, liveSettings.subBgAlpha) {
        subColor = liveSettings.subTextColor
        subSizeSp = liveSettings.subFontSizeSp
        subBgAlpha = liveSettings.subBgAlpha
        controller.updateSubtitleAppearance(liveSettings.subTextColor, liveSettings.subFontSizeSp, liveSettings.subBgAlpha)
    }

    var pendingRestoreEmbeddedSub by remember { mutableStateOf<SavedMovieSubtitle?>(null) }

    LaunchedEffect(controller.embeddedSubtitleTracks, pendingRestoreEmbeddedSub) {
        val pending = pendingRestoreEmbeddedSub ?: return@LaunchedEffect
        val tracks = controller.embeddedSubtitleTracks
        if (tracks.isEmpty()) return@LaunchedEffect
        if (pending.mode == "OFF") {
            controller.selectEmbeddedSubtitleTrack(null)
            pendingRestoreEmbeddedSub = null
        } else if (pending.mode == "EMBEDDED") {
            val target = tracks.firstOrNull { it.label == pending.embeddedTrackLabel && pending.embeddedTrackLabel.isNotBlank() }
                ?: tracks.firstOrNull { it.language.equals(pending.embeddedTrackLang, ignoreCase = true) && pending.embeddedTrackLang.isNotBlank() }
                ?: tracks.getOrNull(pending.embeddedTrackIndex)
            if (target != null) {
                subtitleCues = emptyList()
                controller.hasExternalSubtitlesActive = false
                controller.selectEmbeddedSubtitleTrack(target)
                Toast.makeText(
                    context,
                    if (isAr) "تم استئناف الترجمة المدمجة: ${target.label}" else "Resumed embedded subtitle: ${target.label}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingRestoreEmbeddedSub = null
        }
    }

    LaunchedEffect(subtitleOffsetMs) {
        if (currentRequest.contentType != "LIVE" && subtitleCues.isNotEmpty()) {
            val subCacheKey = SubtitleCacheStore.buildKey(
                currentRequest.portal.id,
                currentRequest.contentType,
                currentRequest.itemId,
                currentRequest.title
            )
            SubtitleCacheStore.updateExternalOffset(context, subCacheKey, subtitleOffsetMs)
        }
    }

    LaunchedEffect(currentRequest.itemId, currentRequest.streamCmdOrUrl) {
        val subCacheKey = SubtitleCacheStore.buildKey(
            currentRequest.portal.id,
            currentRequest.contentType,
            currentRequest.itemId,
            currentRequest.title
        )
        val savedSub = if (currentRequest.contentType != "LIVE") {
            SubtitleCacheStore.loadSubtitle(context, subCacheKey)
        } else null

        subtitleCues = if (savedSub?.mode == "EXTERNAL") savedSub.cues else emptyList()
        subtitleOffsetMs = if (savedSub?.mode == "EXTERNAL") savedSub.offsetMs else 0L
        pendingRestoreEmbeddedSub = if (savedSub?.mode == "EMBEDDED" || savedSub?.mode == "OFF") savedSub else null
        showControls = true
        controlInteractTick++

        val resolvedType = if (currentRequest.contentType == "LIVE") "itv" else "vod"
        val playableUrl = resolvePlayableUrl(currentRequest.portal, currentRequest.streamCmdOrUrl, resolvedType)
        controller.playStream(playableUrl, currentRequest.resumePositionMs, isLive = (currentRequest.contentType == "LIVE"))

        if (savedSub != null && savedSub.mode == "EXTERNAL" && savedSub.cues.isNotEmpty()) {
            controller.activateExternalSubtitlesMode()
            Toast.makeText(
                context,
                if (isAr) "تم استئناف الترجمة المحفوظة (${savedSub.cues.size} سطر)" else "Resumed saved subtitle (${savedSub.cues.size} cues)",
                Toast.LENGTH_SHORT
            ).show()
        } else if (savedSub?.mode == "OFF") {
            controller.hasExternalSubtitlesActive = true
            controller.selectEmbeddedSubtitleTrack(null)
        }

        // تسجيل القناة أو الفيلم في سجل المشاهدة للوصول السريع
        dao.upsertWatchHistory(
            WatchHistoryEntity(
                portalId = currentRequest.portal.id,
                contentType = currentRequest.contentType,
                itemId = currentRequest.itemId,
                seriesId = currentRequest.seriesId,
                title = currentRequest.title,
                posterUrl = currentRequest.posterOrLogo,
                streamCmdOrUrl = currentRequest.streamCmdOrUrl,
                containerExtension = currentRequest.containerExtension,
                positionMs = currentRequest.resumePositionMs,
                durationMs = controller.durationMs
            )
        )

        if (currentRequest.contentType == "LIVE") {
            val epgList = when (currentRequest.portal.protocol) {
                "XTREAM" -> XtreamApiClient.getShortEpg(currentRequest.portal, currentRequest.itemId)
                "STALKER" -> StalkerApiClient.getShortEpg(currentRequest.portal, currentRequest.itemId)
                else -> emptyList()
            }
            val nowTitle = epgList.firstOrNull()?.title.orEmpty()
            if (nowTitle.isNotBlank()) {
                currentEpgTitle = nowTitle
            }
        }
    }

    LaunchedEffect(Unit) {
        var tickCount = 0
        while (true) {
            delay(500)
            val pos = controller.syncPositionTick()
            tickCount++
            if (tickCount % 20 == 0) {
                clockText = LocaleUtils.formatClock()
                if (currentRequest.contentType != "LIVE" && pos > 5_000L) {
                    dao.upsertWatchHistory(
                        WatchHistoryEntity(
                            portalId = currentRequest.portal.id,
                            contentType = currentRequest.contentType,
                            itemId = currentRequest.itemId,
                            seriesId = currentRequest.seriesId,
                            title = currentRequest.title,
                            posterUrl = currentRequest.posterOrLogo,
                            streamCmdOrUrl = currentRequest.streamCmdOrUrl,
                            containerExtension = currentRequest.containerExtension,
                            positionMs = pos,
                            durationMs = controller.durationMs
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(showControls, anyOverlayModal, showSubtitleSyncBar, controlInteractTick) {
        if (showControls && !anyOverlayModal && !showSubtitleSyncBar) {
            delay(8000)
            showControls = false
        }
    }

    LaunchedEffect(showControls, anyOverlayModal, isInPipMode) {
        if (!anyOverlayModal && !isInPipMode) {
            delay(70)
            if (showControls) {
                runCatching { bottomBarInitialFocusRequester.requestFocus() }
            } else {
                runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isNotEmpty()) {
            delay(1500)
            val targetNum = digitBuffer.toIntOrNull()
            digitBuffer = ""
            if (targetNum != null && currentRequest.channelList.isNotEmpty()) {
                val foundIndex = currentRequest.channelList.indexOfFirst { it.number == targetNum }
                    .takeIf { it != -1 }
                    ?: (targetNum - 1).takeIf { it in currentRequest.channelList.indices }

                if (foundIndex != null && foundIndex != currentChannelIdx) {
                    previousChannelIdx = currentChannelIdx
                    currentChannelIdx = foundIndex
                    val targetCh = currentRequest.channelList[foundIndex]
                    currentEpgTitle = targetCh.epgNowTitle
                    currentRequest = currentRequest.copy(
                        itemId = targetCh.id,
                        title = targetCh.name,
                        subtitleQueryHint = targetCh.epgNowTitle,
                        posterOrLogo = targetCh.logoUrl,
                        streamCmdOrUrl = targetCh.streamCmdOrUrl,
                        currentChannelIndex = foundIndex
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val finalPos = controller.getLivePositionMs()
            if (currentRequest.contentType != "LIVE" && finalPos > 5_000L) {
                scope.launch {
                    dao.upsertWatchHistory(
                        WatchHistoryEntity(
                            portalId = currentRequest.portal.id,
                            contentType = currentRequest.contentType,
                            itemId = currentRequest.itemId,
                            seriesId = currentRequest.seriesId,
                            title = currentRequest.title,
                            posterUrl = currentRequest.posterOrLogo,
                            streamCmdOrUrl = currentRequest.streamCmdOrUrl,
                            containerExtension = currentRequest.containerExtension,
                            positionMs = finalPos,
                            durationMs = controller.durationMs
                        )
                    )
                }
            }
            controller.releaseAll()
        }
    }

    fun switchToChannelAtIndex(newIndex: Int) {
        val list = currentRequest.channelList
        if (list.isEmpty()) return
        val wrapped = ((newIndex % list.size) + list.size) % list.size
        if (wrapped == currentChannelIdx) return
        previousChannelIdx = currentChannelIdx
        currentChannelIdx = wrapped
        val targetCh = list[wrapped]
        currentEpgTitle = targetCh.epgNowTitle
        currentRequest = currentRequest.copy(
            itemId = targetCh.id,
            title = targetCh.name,
            subtitleQueryHint = targetCh.epgNowTitle,
            posterOrLogo = targetCh.logoUrl,
            streamCmdOrUrl = targetCh.streamCmdOrUrl,
            currentChannelIndex = wrapped
        )
    }

    fun handleSmartPlayPause() {
        if (controller.isPlaying) {
            controller.togglePlayPause()
        } else if (controller.needsStreamRecovery()) {
            val resumeAt = if (currentRequest.contentType == "LIVE") 0L else controller.getLivePositionMs()
            scope.launch {
                val resolvedType = if (currentRequest.contentType == "LIVE") "itv" else "vod"
                val freshUrl = resolvePlayableUrl(currentRequest.portal, currentRequest.streamCmdOrUrl, resolvedType)
                if (freshUrl.isNotBlank()) {
                    controller.playStream(freshUrl, resumeAt, isLive = (currentRequest.contentType == "LIVE"))
                } else {
                    controller.togglePlayPause()
                }
            }
        } else {
            controller.togglePlayPause()
        }
    }

    fun toggleCurrentFavorite() {
        scope.launch {
            if (isCurrentFavorite) {
                dao.removeFavorite(currentRequest.portal.id, currentRequest.contentType, currentRequest.itemId)
                Toast.makeText(
                    context,
                    if (isAr) "تمت الإزالة من المفضلة" else "Removed from Favorites",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                dao.addFavorite(
                    FavoriteEntity(
                        portalId = currentRequest.portal.id,
                        contentType = currentRequest.contentType,
                        itemId = currentRequest.itemId,
                        title = currentRequest.title,
                        logoOrPoster = currentRequest.posterOrLogo,
                        streamCmdOrUrl = currentRequest.streamCmdOrUrl,
                        categoryId = ""
                    )
                )
                Toast.makeText(
                    context,
                    if (isAr) "تمت الإضافة إلى المفضلة ★" else "Added to Favorites ★",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    BackHandler {
        when {
            showSubtitleSyncBar -> showSubtitleSyncBar = false
            showControls -> showControls = false
            else -> {
                controller.releaseAll()
                onExitPlayer()
            }
        }
    }

    val isLive = currentRequest.contentType == "LIVE"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { surfaceWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (anyOverlayModal || isInPipMode) return@onPreviewKeyEvent false

                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    val digit = extractDigitChar(event.key)

                    if (isLive && digit != null) {
                        if (digit == '0' && digitBuffer.isEmpty()) {
                            if (previousChannelIdx in currentRequest.channelList.indices) {
                                switchToChannelAtIndex(previousChannelIdx)
                            }
                        } else {
                            digitBuffer += digit
                        }
                        return@onPreviewKeyEvent true
                    }

                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            if (isLive) {
                                switchToChannelAtIndex(currentChannelIdx + 1)
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            if (isLive) {
                                switchToChannelAtIndex(currentChannelIdx - 1)
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (isLive && !showControls) {
                                showChannelDrawer = true
                                return@onPreviewKeyEvent true
                            } else if (!isLive && !showControls) {
                                controller.seekBy(-10_000L)
                                showControls = true
                                controlInteractTick++
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionRight -> {
                            // الزر المقابل لقائمة القنوات يفتح سجل المشاهدة للوصول السريع
                            if (isLive && !showControls) {
                                showHistoryDrawer = true
                                return@onPreviewKeyEvent true
                            } else if (!isLive && !showControls) {
                                controller.seekBy(10_000L)
                                showControls = true
                                controlInteractTick++
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!showControls) {
                                showControls = true
                                controlInteractTick++
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.Menu -> {
                            showQuickMenu = true
                            return@onPreviewKeyEvent true
                        }
                    }
                } else if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount >= 4) {
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                        showQuickMenu = true
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        // 1. سطح عرض الفيديو
        DualVideoSurface(
            controller = controller,
            subTextColor = subColor,
            subFontSizeSp = subSizeSp,
            subBgAlpha = subBgAlpha,
            modifier = Modifier.fillMaxSize()
        )

        // إخفاء كافة أزرار التحكم والقوائم أثناء وضع PiP المصغر
        if (isInPipMode) {
            return@Box
        }

        // 2. طبقة اللمس والسحب على جانبي الشاشة (الصوت يميناً والسطوع يساراً)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            controlInteractTick++
                        }
                    )
                }
                .pointerInput(surfaceWidthPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / 480f
                            val isRightSide = dragStartX > (surfaceWidthPx / 2f)
                            if (isRightSide) {
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                val targetVol = (volumeFraction * maxVol).roundToInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                activeGestureHud = true to (volumeFraction * 100f).roundToInt()
                                gestureHudTick++
                            } else {
                                brightnessFraction = (brightnessFraction + delta).coerceIn(0.02f, 1f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessFraction
                                    win.attributes = lp
                                }
                                activeGestureHud = false to (brightnessFraction * 100f).roundToInt()
                                gestureHudTick++
                            }
                        }
                    )
                }
        )

        // 3. مؤشر الصوت والسطوع الوسطي عند السحب باللمس
        activeGestureHud?.let { (isVolume, percent) ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isVolume) Icons.Default.VolumeUp else Icons.Default.BrightnessHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    LtrText(
                        text = "$percent%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. أزرار تبديل القنوات الجانبية باللمس أثناء البث المباشر
        if (isLive && showControls && !anyOverlayModal && currentRequest.channelList.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlInteractTick++
                                if (isAr) switchToChannelAtIndex(currentChannelIdx + 1)
                                else switchToChannelAtIndex(currentChannelIdx - 1)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlInteractTick++
                                if (isAr) switchToChannelAtIndex(currentChannelIdx - 1)
                                else switchToChannelAtIndex(currentChannelIdx + 1)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // 5. طبقة الترجمة الموحدة (للترجمة الخارجية والمدمجة النظيفة)
        SubtitleOverlayRenderer(
            cues = subtitleCues,
            videoPositionMs = controller.currentPositionMs,
            subtitleOffsetMs = subtitleOffsetMs,
            textColor = Color(subColor),
            fontSizeSp = subSizeSp,
            bgAlpha = subBgAlpha,
            liveEmbeddedText = controller.liveEmbeddedCueText
        )

        // 6. مؤشر التحميل المؤقت
        if (controller.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 7. إشعار التحويل التلقائي لـ LibVLC
        if (showEngineHudBadge) controller.engineStatusNotice?.let { notice ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                LtrText(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 8. الساعة الرقمية بأرقام (0-9)
        if (settings.showClock) {
            val clockAlignment = when (settings.clockCorner) {
                1 -> Alignment.TopStart
                2 -> Alignment.BottomEnd
                3 -> Alignment.BottomStart
                else -> Alignment.TopEnd
            }
            Box(
                modifier = Modifier
                    .align(clockAlignment)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                LtrText(
                    text = clockText,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 9. شارة رقم القناة المدخل بالأرقام
        if (digitBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                LtrText(
                    text = "CH: $digitBuffer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // 10. شريط تزامن الترجمة
        if (showSubtitleSyncBar && !anyOverlayModal) {
            SubtitleSyncFloatingBar(
                offsetMs = subtitleOffsetMs,
                isAr = isAr,
                onAdjustOffset = { delta -> subtitleOffsetMs += delta },
                onClose = { showSubtitleSyncBar = false },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
        }

        // 11. شريط التحكم السفلي المتكامل
        if (showControls && !anyOverlayModal) {
            PlayerBottomControlBar(
                request = currentRequest,
                epgTitle = currentEpgTitle,
                controller = controller,
                isFavorite = isCurrentFavorite,
                isAr = isAr,
                initialFocusRequester = bottomBarInitialFocusRequester,
                onUserInteract = { controlInteractTick++ },
                onBackClick = {
                    controller.releaseAll()
                    onExitPlayer()
                },
                onPlayPauseClick = { handleSmartPlayPause() },
                onPrevChannel = {
                    controlInteractTick++
                    switchToChannelAtIndex(currentChannelIdx - 1)
                },
                onNextChannel = {
                    controlInteractTick++
                    switchToChannelAtIndex(currentChannelIdx + 1)
                },
                onToggleFavorite = {
                    controlInteractTick++
                    toggleCurrentFavorite()
                },
                onOpenHistory = {
                    controlInteractTick++
                    showHistoryDrawer = true
                },
                onOpenSubtitles = {
                    controlInteractTick++
                    showSubtitleSearchDialog = true
                },
                onOpenSubtitleStyle = {
                    controlInteractTick++
                    showSubtitleStyleDialog = true
                },
                onOpenSync = {
                    controlInteractTick++
                    showSubtitleSyncBar = !showSubtitleSyncBar
                },
                onOpenAudioTracks = {
                    controlInteractTick++
                    showAudioTracksDialog = true
                },
                onOpenMenu = {
                    controlInteractTick++
                    showQuickMenu = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 12. القائمة الجانبية للقنوات (زر اليسار)
        if (showChannelDrawer && currentRequest.channelList.isNotEmpty()) {
            LiveSideChannelDrawer(
                channels = currentRequest.channelList,
                currentIndex = currentChannelIdx,
                isAr = isAr,
                onSelectChannel = { idx ->
                    showChannelDrawer = false
                    switchToChannelAtIndex(idx)
                },
                onDismiss = { showChannelDrawer = false }
            )
        }

        // 13. القائمة الجانبية لسجل المشاهدة (الزر المقابل لقائمة القنوات)
        if (showHistoryDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                LiveSideHistoryDrawer(
                    history = watchHistory,
                    isAr = isAr,
                    onSelectEntry = { entry ->
                        showHistoryDrawer = false
                        val chIdx = currentRequest.channelList.indexOfFirst { it.id == entry.itemId }
                        if (entry.contentType == "LIVE" && chIdx >= 0) {
                            switchToChannelAtIndex(chIdx)
                        } else {
                            currentRequest = currentRequest.copy(
                                contentType = entry.contentType,
                                itemId = entry.itemId,
                                seriesId = entry.seriesId,
                                title = entry.title,
                                subtitleQueryHint = entry.title,
                                posterOrLogo = entry.posterUrl,
                                streamCmdOrUrl = entry.streamCmdOrUrl,
                                containerExtension = entry.containerExtension,
                                resumePositionMs = entry.positionMs
                            )
                        }
                    },
                    onDismiss = { showHistoryDrawer = false }
                )
            }
        }

        // 14. قائمة خيارات المشغل السريعة
        if (showQuickMenu) {
            val menuOptions = buildList {
                add(
                    TvMenuOption(
                        id = "watch_history",
                        label = if (isAr) "سجل المشاهدة السريع" else "Recent Watch History",
                        icon = Icons.Default.History
                    )
                )
                add(
                    TvMenuOption(
                        id = "fav_toggle",
                        label = if (isCurrentFavorite) {
                            if (isAr) "إزالة من المفضلة ★" else "Remove from Favorites ★"
                        } else {
                            if (isAr) "إضافة إلى المفضلة ☆" else "Add to Favorites ☆"
                        },
                        icon = if (isCurrentFavorite) Icons.Default.Star else Icons.Default.StarOutline
                    )
                )
                add(
                    TvMenuOption(
                        id = "embedded_subs",
                        label = if (isAr) "الترجمة المدمجة مع الفيديو (${controller.embeddedSubtitleTracks.size})" else "Embedded Subtitles (${controller.embeddedSubtitleTracks.size})",
                        icon = Icons.Default.ClosedCaption
                    )
                )
                add(
                    TvMenuOption(
                        id = "sub_search",
                        label = if (isAr) "بحث وتحميل ترجمة خارجية" else "Search External Subtitles",
                        icon = Icons.Default.Subtitles
                    )
                )
                add(
                    TvMenuOption(
                        id = "sub_style",
                        label = if (isAr) "لون وحجم خط الترجمة" else "Subtitle Color & Font Size",
                        icon = Icons.Default.Palette
                    )
                )
                add(
                    TvMenuOption(
                        id = "sub_sync",
                        label = if (isAr) "تزامن الترجمة (${LocaleUtils.formatOffsetSeconds(subtitleOffsetMs)})" else "Subtitle Sync (${LocaleUtils.formatOffsetSeconds(subtitleOffsetMs)})",
                        icon = Icons.Default.Tune
                    )
                )
                add(
                    TvMenuOption(
                        id = "audio_track",
                        label = if (isAr) "اختيار مسار الصوت / المعلق" else "Audio Tracks / Commentary",
                        icon = Icons.Default.Audiotrack
                    )
                )
                add(
                    TvMenuOption(
                        id = "switch_engine",
                        label = if (isAr) "تبديل المحرك (الحالي: ${controller.activeEngine.label})" else "Switch Engine (${controller.activeEngine.label})",
                        icon = Icons.Default.SettingsEthernet
                    )
                )
                add(
                    TvMenuOption(
                        id = "clock_corner",
                        label = if (isAr) "تغيير زاوية الساعة الرقمية" else "Change Clock Corner",
                        icon = Icons.Default.AccessTime
                    )
                )
            }
            TvActionMenuDialog(
                title = if (isAr) "خيارات المشغل" else "Player Options",
                options = menuOptions,
                onSelect = { opt ->
                    when (opt.id) {
                        "pip_mode" -> {
                            showControls = false
                            onEnterPip?.invoke()
                        }
                        "watch_history" -> showHistoryDrawer = true
                        "fav_toggle" -> toggleCurrentFavorite()
                        "embedded_subs" -> showEmbeddedSubtitlesDialog = true
                        "sub_search" -> showSubtitleSearchDialog = true
                        "sub_style" -> showSubtitleStyleDialog = true
                        "sub_sync" -> showSubtitleSyncBar = true
                        "audio_track" -> showAudioTracksDialog = true
                        "switch_engine" -> controller.switchEngineManually()
                        "clock_corner" -> scope.launch {
                            val nextCorner = (settings.clockCorner + 1) % 4
                            settingsStore.updateClockCorner(nextCorner)
                        }
                    }
                },
                onDismiss = { showQuickMenu = false }
            )
        }

        if (showSubtitleSearchDialog) {
            val initialQuery = remember(currentRequest, currentEpgTitle) {
                if (currentRequest.contentType == "LIVE") {
                    currentEpgTitle.ifBlank { currentRequest.title }
                } else {
                    currentRequest.subtitleQueryHint.ifBlank { currentRequest.title }
                }
            }
            SubtitleSearchDialog(
                initialQuery = initialQuery,
                settings = liveSettings,
                settingsStore = settingsStore,
                isAr = isAr,
                onSubtitleLoaded = { loadedCues ->
                    controller.activateExternalSubtitlesMode()
                    subtitleCues = loadedCues
                    subtitleOffsetMs = 0L
                    if (currentRequest.contentType != "LIVE") {
                        val subCacheKey = SubtitleCacheStore.buildKey(
                            currentRequest.portal.id,
                            currentRequest.contentType,
                            currentRequest.itemId,
                            currentRequest.title
                        )
                        SubtitleCacheStore.saveSubtitle(context, subCacheKey, loadedCues, 0L)
                    }
                    showSubtitleSearchDialog = false
                    Toast.makeText(
                        context,
                        if (isAr) "تم تحميل وحفظ الترجمة لهذا الفيلم (${loadedCues.size} سطر)" else "Loaded & saved subtitle (${loadedCues.size} cues)",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showSubtitleSearchDialog = false }
            )
        }

        if (showSubtitleStyleDialog) {
            SubtitleAppearanceDialog(
                currentColor = subColor,
                currentSizeSp = subSizeSp,
                currentBgAlpha = subBgAlpha,
                isAr = isAr,
                onApply = { c, s, a ->
                    subColor = c
                    subSizeSp = s
                    subBgAlpha = a
                    controller.updateSubtitleAppearance(c, s, a)
                    scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { settingsStore.updateSubtitleStyle(c, s, a) } }
                },
                onDismiss = { showSubtitleStyleDialog = false }
            )
        }

        if (showEmbeddedSubtitlesDialog) {
            val closeEmbDlgReq = remember { FocusRequester() }
            val embListState = rememberLazyListState()
            val embItemReqs = remember(controller.embeddedSubtitleTracks.size) { mutableStateMapOf<Int, FocusRequester>() }
            TvModalDialog(
                title = if (isAr) "الترجمة المدمجة في الفيديو" else "Embedded Video Subtitles",
                onDismiss = { showEmbeddedSubtitlesDialog = false },
                maxWidth = 500.dp,
                closeBtnRequester = closeEmbDlgReq
            ) { firstRequester ->
                if (controller.embeddedSubtitleTracks.isEmpty()) {
                    Text(
                        text = if (isAr) "لا توجد ترجمة مدمجة داخل ملف الفيديو، يمكنك البحث عن ترجمة خارجية." else "No embedded subtitles in this stream. Try External Subtitles search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val noneSelected = controller.embeddedSubtitleTracks.none { it.isSelected } || controller.hasExternalSubtitlesActive
                    val totalEmbRows = controller.embeddedSubtitleTracks.size + 1
                    embItemReqs[0] = firstRequester
                    LazyColumn(
                        state = embListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item(key = "emb_off_row") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvVerticalStepItem(
                                        index = 0, totalCount = totalEmbRows, listState = embListState,
                                        requesters = embItemReqs, scope = scope,
                                        onExitTop = { runCatching { closeEmbDlgReq.requestFocus() }.isSuccess }
                                    )
                                    .tvFocusItem(
                                        focusRequester = firstRequester,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = if (noneSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                        onClick = {
                                            subtitleCues = emptyList()
                                            controller.selectEmbeddedSubtitleTrack(null)
                                            if (currentRequest.contentType != "LIVE") {
                                                val k = SubtitleCacheStore.buildKey(currentRequest.portal.id, currentRequest.contentType, currentRequest.itemId, currentRequest.title)
                                                SubtitleCacheStore.saveSubtitlesOff(context, k)
                                            }
                                            showEmbeddedSubtitlesDialog = false
                                        }
                                    )
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (noneSelected) (if (isAr) "✓ إيقاف الترجمة المدمجة" else "✓ Off (Disabled)") else (if (isAr) "إيقاف الترجمة المدمجة" else "Off (Disabled)"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        itemsIndexed(
                            items = controller.embeddedSubtitleTracks,
                            key = { idx, tr -> "emb_tr_${tr.id}_$idx" }
                        ) { tIdx, track ->
                            val rowIdx = tIdx + 1
                            val rReq = embItemReqs.getOrPut(rowIdx) { FocusRequester() }
                            val isTrackActive = track.isSelected && !controller.hasExternalSubtitlesActive
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvVerticalStepItem(
                                        index = rowIdx, totalCount = totalEmbRows, listState = embListState,
                                        requesters = embItemReqs, scope = scope
                                    )
                                    .tvFocusItem(
                                        focusRequester = rReq,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = if (isTrackActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                        onClick = {
                                            subtitleCues = emptyList()
                                            subtitleOffsetMs = 0L
                                            controller.hasExternalSubtitlesActive = false
                                            controller.selectEmbeddedSubtitleTrack(track)
                                            if (currentRequest.contentType != "LIVE") {
                                                val k = SubtitleCacheStore.buildKey(currentRequest.portal.id, currentRequest.contentType, currentRequest.itemId, currentRequest.title)
                                                SubtitleCacheStore.saveEmbeddedSelection(context, k, tIdx, track.label, track.language)
                                            }
                                            Toast.makeText(
                                                context,
                                                if (isAr) "تم تفعيل الترجمة المدمجة: ${track.label}" else "Embedded Subtitle: ${track.label}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showEmbeddedSubtitlesDialog = false
                                        }
                                    )
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isTrackActive) "✓ ${track.label}" else track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAudioTracksDialog) {
            val closeAudDlgReq = remember { FocusRequester() }
            val audListState = rememberLazyListState()
            val audReqs = remember(controller.audioTracks.size) { mutableStateMapOf<Int, FocusRequester>() }
            TvModalDialog(
                title = if (isAr) "مسارات الصوت المتاحة" else "Available Audio Tracks",
                onDismiss = { showAudioTracksDialog = false },
                maxWidth = 460.dp,
                closeBtnRequester = closeAudDlgReq
            ) { firstRequester ->
                if (controller.audioTracks.isEmpty()) {
                    Text(
                        text = if (isAr) "مسار صوتي واحد افتراضي في هذا البث" else "Single default audio track in this stream",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    audReqs[0] = firstRequester
                    LazyColumn(
                        state = audListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = controller.audioTracks,
                            key = { idx, tr -> "aud_${tr.id}_$idx" }
                        ) { index, track ->
                            val aReq = if (index == 0) firstRequester else audReqs.getOrPut(index) { FocusRequester() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvVerticalStepItem(
                                        index = index, totalCount = controller.audioTracks.size, listState = audListState,
                                        requesters = audReqs, scope = scope,
                                        onExitTop = { runCatching { closeAudDlgReq.requestFocus() }.isSuccess }
                                    )
                                    .tvFocusItem(
                                        focusRequester = aReq,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = if (track.isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        onClick = {
                                            controller.selectAudioTrack(track)
                                            showAudioTracksDialog = false
                                        }
                                    )
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (track.isSelected) "✓ ${track.label}" else track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomControlBar(
    request: PlaybackRequest,
    epgTitle: String,
    controller: DualPlayerController,
    isFavorite: Boolean,
    isAr: Boolean,
    initialFocusRequester: FocusRequester,
    onUserInteract: () -> Unit,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenHistory: () -> Unit,
    onEnterPip: (() -> Unit)? = null,
    onOpenSubtitles: () -> Unit,
    onOpenSubtitleStyle: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalTvExtraColors.current
    val isLive = request.contentType == "LIVE"
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragMs by remember { mutableLongStateOf(0L) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(extraColors.panelOverlay)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (epgTitle.isNotBlank()) {
                    Text(
                        text = epgTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            LtrText(
                text = controller.activeEngine.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (!isLive && controller.durationMs > 0L) {
            val activePos = if (isDraggingSlider) sliderDragMs else controller.currentPositionMs
            val progress = (activePos.toFloat() / controller.durationMs.toFloat()).coerceIn(0f, 1f)
            val remainingMs = (controller.durationMs - activePos).coerceAtLeast(0L)

            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = progress,
                onValueChange = { frac ->
                    isDraggingSlider = true
                    sliderDragMs = (frac * controller.durationMs).toLong()
                    onUserInteract()
                },
                onValueChangeFinished = {
                    controller.seekTo(sliderDragMs)
                    isDraggingSlider = false
                    onUserInteract()
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LtrText(
                    text = LocaleUtils.formatDuration(activePos),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                LtrText(
                    text = "-${LocaleUtils.formatDuration(remainingMs)} / ${LocaleUtils.formatDuration(controller.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerControlChip(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = if (isAr) "رجوع" else "Back",
                onFocused = onUserInteract,
                onClick = onBackClick
            )

            if (isLive && request.channelList.size > 1) {
                PlayerControlChip(
                    icon = Icons.Default.SkipPrevious,
                    label = "CH-",
                    onFocused = onUserInteract,
                    onClick = onPrevChannel
                )
            } else {
                PlayerControlChip(
                    icon = Icons.Default.Replay10,
                    label = "-15s",
                    onFocused = onUserInteract,
                    onClick = {
                        onUserInteract()
                        controller.seekBy(-15_000L)
                    }
                )
            }

            PlayerControlChip(
                icon = if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (controller.isPlaying) (if (isAr) "إيقاف" else "Pause") else (if (isAr) "تشغيل" else "Play"),
                focusRequester = initialFocusRequester,
                onFocused = onUserInteract,
                onClick = {
                    onUserInteract()
                    onPlayPauseClick()
                }
            )

            if (isLive && request.channelList.size > 1) {
                PlayerControlChip(
                    icon = Icons.Default.SkipNext,
                    label = "CH+",
                    onFocused = onUserInteract,
                    onClick = onNextChannel
                )
            } else {
                PlayerControlChip(
                    icon = Icons.Default.Forward10,
                    label = "+15s",
                    onFocused = onUserInteract,
                    onClick = {
                        onUserInteract()
                        controller.seekBy(15_000L)
                    }
                )
            }

            PlayerControlChip(
                icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                label = if (isAr) "المفضلة" else "Fav",
                iconTint = if (isFavorite) Color(0xFFFFD600) else Color.White,
                unfocusedBg = if (isFavorite) Color(0xFFFFD600).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.16f),
                onFocused = onUserInteract,
                onClick = onToggleFavorite
            )

            if (!com.kadri.stalkerplayerpro.isTvDevice(androidx.compose.ui.platform.LocalContext.current)) {
                PlayerControlChip(
                    icon = Icons.Default.History,
                    label = if (isAr) "السجل" else "History",
                    onFocused = onUserInteract,
                    onClick = onOpenHistory
                )
            }

            if (isLive) {
                PlayerControlChip(
                    icon = Icons.Default.FiberManualRecord,
                    label = "LIVE",
                    onFocused = onUserInteract,
                    onClick = {
                        onUserInteract()
                        controller.jumpToLiveEdge()
                    }
                )
            }

            PlayerControlChip(
                icon = Icons.Default.Audiotrack,
                label = if (isAr) "الصوت" else "Audio",
                onFocused = onUserInteract,
                onClick = onOpenAudioTracks
            )

            PlayerControlChip(
                icon = Icons.Default.Subtitles,
                label = if (isAr) "الترجمة" else "Subtitles",
                onFocused = onUserInteract,
                onClick = onOpenSubtitles
            )

            PlayerControlChip(
                icon = Icons.Default.Tune,
                label = if (isAr) "التزامن" else "Sync",
                onFocused = onUserInteract,
                onClick = onOpenSync
            )

            PlayerControlChip(
                icon = Icons.Default.SettingsEthernet,
                label = "MENU",
                onFocused = onUserInteract,
                onClick = onOpenMenu
            )
        }
    }
}

@Composable
private fun PlayerControlChip(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    unfocusedBg: Color = Color.White.copy(alpha = 0.16f),
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusItem(
                focusRequester = focusRequester,
                shape = RoundedCornerShape(8.dp),
                unfocusedBackgroundColor = unfocusedBg,
                onClick = onClick
            )
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        LtrText(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun SubtitleSyncFloatingBar(
    offsetMs: Long,
    isAr: Boolean,
    onAdjustOffset: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalTvExtraColors.current
    Row(
        modifier = modifier
            .background(extraColors.panelOverlay, RoundedCornerShape(12.dp))
            .border(1.5.dp, extraColors.focusRing, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isAr) "تزامن الترجمة:" else "Subtitle Sync:",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )

        listOf(-500L to "-0.5s", -100L to "-0.1s", 100L to "+0.1s", 500L to "+0.5s").forEach { (delta, lbl) ->
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { onAdjustOffset(delta) }
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                LtrText(
                    text = lbl,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        LtrText(
            text = LocaleUtils.formatOffsetSeconds(offsetMs),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .tvFocusItem(
                    shape = RoundedCornerShape(8.dp),
                    unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                    onClick = onClose
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = if (isAr) "حفظ" else "Save",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun LiveSideChannelDrawer(
    channels: List<com.kadri.stalkerplayerpro.data.model.ChannelItem>,
    currentIndex: Int,
    isAr: Boolean,
    onSelectChannel: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val safeStartIndex = currentIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeStartIndex)
    val drawerScope = rememberCoroutineScope()
    val chDrawerReqs = remember(channels.size) { mutableStateMapOf<Int, FocusRequester>() }
    val extraColors = LocalTvExtraColors.current

    LaunchedEffect(safeStartIndex) {
        kotlinx.coroutines.delay(60)
        runCatching {
            listState.scrollToItem(safeStartIndex)
            chDrawerReqs[safeStartIndex]?.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        TvFocusTrap(
            onDismiss = onDismiss,
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(extraColors.panelOverlay)
                .padding(14.dp)
        ) { firstRequester ->
            chDrawerReqs[safeStartIndex] = firstRequester

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (isAr) "قائمة القنوات" else "Channels List",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = channels,
                        key = { idx, ch -> "drawer_${ch.uniqueKey}_$idx" }
                    ) { index, ch ->
                        val isCurrent = index == safeStartIndex
                        val chReq = if (isCurrent) firstRequester else chDrawerReqs.getOrPut(index) { FocusRequester() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvVerticalStepItem(
                                    index = index,
                                    totalCount = channels.size,
                                    listState = listState,
                                    requesters = chDrawerReqs,
                                    scope = drawerScope
                                )
                                .tvFocusItem(
                                    focusRequester = chReq,
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedBackgroundColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    },
                                    onClick = { onSelectChannel(index) }
                                )
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LtrText(
                                text = LocaleUtils.formatInt(ch.number),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(42.dp)
                            )
                            Text(
                                text = ch.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSideHistoryDrawer(
    history: List<WatchHistoryEntity>,
    isAr: Boolean,
    onSelectEntry: (WatchHistoryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val extraColors = LocalTvExtraColors.current
    val histDrawerState = rememberLazyListState()
    val histDrawerScope = rememberCoroutineScope()
    val histDrawerReqs = remember(history.size) { mutableStateMapOf<Int, FocusRequester>() }
    TvFocusTrap(
        onDismiss = onDismiss,
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(extraColors.panelOverlay)
            .padding(14.dp)
    ) { firstRequester ->
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (isAr) "سجل المشاهدة السريع" else "Watch History",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (history.isEmpty()) {
                Text(
                    text = if (isAr) "لا توجد قنوات أو أفلام مشاهدة مؤخراً" else "No recent watch history",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                LazyColumn(
                    state = histDrawerState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = history,
                        key = { idx, entry -> "player_hist_${entry.id}_${entry.itemId}_$idx" }
                    ) { index, entry ->
                        val hReq = if (index == 0) firstRequester else histDrawerReqs.getOrPut(index) { FocusRequester() }
                        if (index == 0) histDrawerReqs[0] = firstRequester
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvVerticalStepItem(
                                    index = index, totalCount = history.size, listState = histDrawerState,
                                    requesters = histDrawerReqs, scope = histDrawerScope
                                )
                                .tvFocusItem(
                                    focusRequester = hReq,
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedBackgroundColor = Color.White.copy(alpha = 0.08f),
                                    onClick = { onSelectEntry(entry) }
                                )
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LtrText(
                                text = if (entry.contentType == "LIVE") "[LIVE]" else "[VOD]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SubtitleSearchDialog(
    initialQuery: String,
    settings: TvUserSettings,
    settingsStore: SettingsDataStore,
    isAr: Boolean,
    onSubtitleLoaded: (List<SubtitleCue>) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchBoxText by remember(initialQuery) { mutableStateOf(cleanMovieTitleForSubtitleSearch(initialQuery)) }
    var selectedSubLang by remember {
        mutableStateOf(settings.preferredSubtitleLang.takeIf { it.isNotBlank() && it != "off" } ?: "ar")
    }
    var results by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    fun executeDualSearch() {
        searchBoxText = searchBoxText.trim()
        if (searchBoxText.isBlank()) return
        SubtitleRepository.lastSearchDiagnostic = ""
        if (settings.openSubApiKey.isBlank() && settings.subdlApiKey.isBlank()) {
            statusMsg = if (isAr) {
                "مفاتيح API فارغة! افتح الإعدادات > حسابات الترجمة وأدخل مفتاح OpenSubtitles أو Subdl."
            } else {
                "API keys are empty! Open Settings > Subtitle API Accounts to enter your key."
            }
            return
        }
        scope.launch {
            isSearching = true
            statusMsg = ""

            val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(searchBoxText)
            val extractedYear = yearMatch?.value?.toIntOrNull()
            val cleanTitle = if (yearMatch != null) {
                searchBoxText.replace(yearMatch.value, "").replace(Regex("""[\(\)\[\]]"""), "").trim()
            } else {
                searchBoxText.trim()
            }

            var jwt = settings.openSubJwtToken
            var activeBaseUrl = settings.openSubBaseUrl
            if (jwt.isBlank() && settings.openSubApiKey.isNotBlank() && settings.openSubUsername.isNotBlank()) {
                val loginRes = SubtitleRepository.loginOpenSubtitlesDetailed(
                    settings.openSubApiKey,
                    settings.openSubUsername,
                    settings.openSubPassword
                )
                if (loginRes.success && loginRes.token.isNotBlank()) {
                    jwt = loginRes.token
                    activeBaseUrl = loginRes.baseUrl
                    settingsStore.updateOpenSubSession(loginRes.token, loginRes.baseUrl)
                }
            }

            val osList = if (settings.subtitleEngine == "SUBDL") emptyList() else SubtitleRepository.searchOpenSubtitles(
                apiKey = settings.openSubApiKey,
                jwtToken = jwt,
                rawQuery = cleanTitle,
                year = extractedYear,
                languages = selectedSubLang,
                baseUrl = activeBaseUrl
            )
            val subdlList = if (settings.subtitleEngine == "OPENSUBTITLES") emptyList() else SubtitleRepository.searchSubdl(
                apiKey = settings.subdlApiKey,
                rawQuery = cleanTitle,
                year = extractedYear,
                languages = selectedSubLang.uppercase(Locale.US)
            )

            results = osList + subdlList
            if (results.isEmpty()) {
                val diag = SubtitleRepository.lastSearchDiagnostic
                statusMsg = if (isAr) {
                    "لم يتم العثور على ترجمات لـ \"$cleanTitle\". ${if (diag.isNotBlank()) "($diag)" else "جرّب تعديل الاسم أو اللغة."}"
                } else {
                    "No subtitles found for \"$cleanTitle\". ${if (diag.isNotBlank()) "($diag)" else "Try adjusting title or language."}"
                }
            }
            isSearching = false
        }
    }

    val closeSubDlgReq = remember { FocusRequester() }
    val searchFieldReq = remember { FocusRequester() }
    val searchBtnReq = remember { FocusRequester() }
    val langChoices = remember {
        listOf(
            "ar" to (if (isAr) "العربية" else "Arabic"),
            "en" to "English",
            "fr" to "Français",
            "ar,en" to "AR + EN",
            "ar,en,fr" to (if (isAr) "الكل" else "All")
        )
    }
    val langReqs = remember { List(langChoices.size) { FocusRequester() } }
    val resultsListState = rememberLazyListState()
    val resultsReqs = remember(results.size) { mutableStateMapOf<Int, FocusRequester>() }

    LaunchedEffect(Unit) {
        if (searchBoxText.isNotBlank()) {
            executeDualSearch()
        }
    }

    TvModalDialog(
        title = if (isAr) "البحث عن ترجمة (OpenSubtitles / Subdl)" else "Search Subtitles",
        onDismiss = onDismiss,
        maxWidth = 640.dp,
        closeBtnRequester = closeSubDlgReq
    ) { firstRequester ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val osOk = settings.openSubApiKey.isNotBlank()
            val subdlOk = settings.subdlApiKey.isNotBlank()
            Text(
                text = if (osOk) "OpenSubtitles: ✓" else (if (isAr) "OpenSubtitles: بدون مفتاح" else "OpenSubtitles: No Key"),
                style = MaterialTheme.typography.labelSmall,
                color = if (osOk) Color(0xFF00E676) else Color(0xFFFFB300),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (subdlOk) "Subdl: ✓" else (if (isAr) "Subdl: بدون مفتاح" else "Subdl: No Key"),
                style = MaterialTheme.typography.labelSmall,
                color = if (subdlOk) Color(0xFF00E676) else Color(0xFFFFB300),
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchBoxText,
                onValueChange = { searchBoxText = it },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFieldReq)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            ev.key == Key.DirectionUp -> runCatching { closeSubDlgReq.requestFocus() }.isSuccess
                            ev.key == Key.DirectionDown -> runCatching { langReqs[0].requestFocus() }.isSuccess
                            (if (isAr) ev.key == Key.DirectionLeft else ev.key == Key.DirectionRight) -> runCatching { firstRequester.requestFocus() }.isSuccess
                            else -> false
                        }
                    }
            )
            Row(
                modifier = Modifier
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            ev.key == Key.DirectionUp -> runCatching { closeSubDlgReq.requestFocus() }.isSuccess
                            ev.key == Key.DirectionDown -> runCatching { langReqs[0].requestFocus() }.isSuccess
                            (if (isAr) ev.key == Key.DirectionRight else ev.key == Key.DirectionLeft) -> runCatching { searchFieldReq.requestFocus() }.isSuccess
                            else -> false
                        }
                    }
                    .tvFocusItem(
                        focusRequester = firstRequester,
                        shape = RoundedCornerShape(10.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                        onClick = { executeDualSearch() }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAr) "بحث" else "Search",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isAr) "لغة الترجمة:" else "Language:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            langChoices.forEachIndexed { idx, (code, label) ->
                val active = selectedSubLang == code
                Box(
                    modifier = Modifier
                        .tvHorizontalStepItem(
                            index = idx,
                            totalCount = langChoices.size,
                            requesters = langReqs,
                            isAr = isAr,
                            onUp = { runCatching { firstRequester.requestFocus() }.isSuccess },
                            onDown = {
                                if (results.isNotEmpty()) {
                                    runCatching { resultsReqs[0]?.requestFocus() }.isSuccess
                                } else false
                            }
                        )
                        .tvFocusItem(
                            focusRequester = langReqs[idx],
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                selectedSubLang = code
                                scope.launch { settingsStore.updatePreferredSubtitleLang(code) }
                                executeDualSearch()
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (statusMsg.isNotBlank()) {
            Text(
                text = statusMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            LazyColumn(
                state = resultsListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(230.dp)
            ) {
                itemsIndexed(
                    items = results,
                    key = { idx, item -> "sub_${item.source}_${item.id}_$idx" }
                ) { index, subItem ->
                    val rReq = resultsReqs.getOrPut(index) { FocusRequester() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvVerticalStepItem(
                                index = index,
                                totalCount = results.size,
                                listState = resultsListState,
                                requesters = resultsReqs,
                                scope = scope,
                                onExitTop = { runCatching { langReqs[0].requestFocus() }.isSuccess }
                            )
                            .tvFocusItem(
                                focusRequester = rReq,
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                onClick = {
                                    scope.launch {
                                        isSearching = true
                                        val cues = SubtitleRepository.downloadAndParseSubtitle(
                                            item = subItem,
                                            osApiKey = settings.openSubApiKey,
                                            osJwtToken = settings.openSubJwtToken
                                        )
                                        isSearching = false
                                        if (cues.isNotEmpty()) {
                                            onSubtitleLoaded(cues)
                                        } else {
                                            statusMsg = if (isAr) "فشل تحميل ملف الترجمة" else "Failed to download subtitle"
                                        }
                                    }
                                }
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LtrText(
                            text = "[${subItem.language}]",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = subItem.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LtrText(
                            text = subItem.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleAppearanceDialog(
    currentColor: Long,
    currentSizeSp: Float,
    currentBgAlpha: Float,
    isAr: Boolean,
    onApply: (Long, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(currentColor) }
    var selectedSize by remember { mutableFloatStateOf(currentSizeSp) }
    var selectedAlpha by remember { mutableFloatStateOf(currentBgAlpha) }

    val colorChoices = listOf(
        0xFFFFFFFF to (if (isAr) "أبيض" else "White"),
        0xFFFFD600 to (if (isAr) "أصفر" else "Yellow"),
        0xFF00E5FF to (if (isAr) "سماوي" else "Cyan"),
        0xFF00E676 to (if (isAr) "أخضر" else "Green"),
        0xFFFF9100 to (if (isAr) "برتقالي" else "Orange")
    )
    val sizePresets = listOf(24f to "24sp", 36f to "36sp", 48f to "48sp", 64f to "64sp", 78f to "78sp", 90f to "90sp")
    val alphaChoices = listOf(0f to "0%", 0.45f to "45%", 0.65f to "65%", 0.9f to "90%")

    val closeAppReq = remember { FocusRequester() }
    val cancelAppReq = remember { FocusRequester() }
    val saveAppReq = remember { FocusRequester() }
    val minusAppReq = remember { FocusRequester() }
    val plusAppReq = remember { FocusRequester() }
    val stepAppReqs = remember { listOf(minusAppReq, plusAppReq) }

    val cReqs = remember { List(colorChoices.size) { FocusRequester() } }
    val pReqs = remember { List(sizePresets.size) { FocusRequester() } }
    val aReqs = remember { List(alphaChoices.size) { FocusRequester() } }

    TvModalDialog(
        title = if (isAr) "تخصيص لون وحجم الترجمة" else "Subtitle Color & Font Size",
        onDismiss = onDismiss,
        maxWidth = 520.dp,
        closeBtnRequester = closeAppReq,
        scrollableBody = true,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Box(
                    modifier = Modifier
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                ev.key == Key.DirectionUp -> runCatching { aReqs[0].requestFocus() }.isSuccess
                                (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight) -> runCatching { saveAppReq.requestFocus() }.isSuccess
                                else -> false
                            }
                        }
                        .tvFocusItem(
                            focusRequester = cancelAppReq,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "إلغاء" else "Cancel",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Box(
                    modifier = Modifier
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                ev.key == Key.DirectionUp -> runCatching { aReqs[0].requestFocus() }.isSuccess
                                (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft) -> runCatching { cancelAppReq.requestFocus() }.isSuccess
                                else -> false
                            }
                        }
                        .tvFocusItem(
                            focusRequester = saveAppReq,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                onApply(selectedColor, selectedSize, selectedAlpha)
                                onDismiss()
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "حفظ" else "Save",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) { firstRequester ->
        val actualColorReqs = remember(firstRequester) {
            List(colorChoices.size) { i -> if (i == 0) firstRequester else cReqs[i] }
        }

        Text(
            text = if (isAr) "لون النص:" else "Text Color:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colorChoices.forEachIndexed { idx, (hex, label) ->
                val active = selectedColor == hex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvHorizontalStepItem(
                            index = idx, totalCount = colorChoices.size, requesters = actualColorReqs, isAr = isAr,
                            onUp = { runCatching { closeAppReq.requestFocus() }.isSuccess },
                            onDown = { runCatching { minusAppReq.requestFocus() }.isSuccess }
                        )
                        .tvFocusItem(
                            focusRequester = actualColorReqs[idx],
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedColor = hex }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else Color(hex),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isAr) "حجم الخط:" else "Font Size:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isAr) "الحجم الحالي: ${selectedSize.toInt()}sp (حتى 90sp)" else "Current Size: ${selectedSize.toInt()}sp (up to 90sp)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .tvHorizontalStepItem(
                        index = 0, totalCount = 2, requesters = stepAppReqs, isAr = isAr,
                        onUp = { runCatching { actualColorReqs[0].requestFocus() }.isSuccess },
                        onDown = { runCatching { pReqs[0].requestFocus() }.isSuccess }
                    )
                    .tvFocusItem(
                        focusRequester = minusAppReq,
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { selectedSize = (selectedSize - 4f).coerceAtLeast(18f) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { LtrText("A-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier
                    .tvHorizontalStepItem(
                        index = 1, totalCount = 2, requesters = stepAppReqs, isAr = isAr,
                        onUp = { runCatching { actualColorReqs[0].requestFocus() }.isSuccess },
                        onDown = { runCatching { pReqs[0].requestFocus() }.isSuccess }
                    )
                    .tvFocusItem(
                        focusRequester = plusAppReq,
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { selectedSize = (selectedSize + 4f).coerceAtMost(90f) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { LtrText("A+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sizePresets.forEachIndexed { idx, (spVal, lbl) ->
                val active = selectedSize == spVal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvHorizontalStepItem(
                            index = idx, totalCount = sizePresets.size, requesters = pReqs, isAr = isAr,
                            onUp = { runCatching { minusAppReq.requestFocus() }.isSuccess },
                            onDown = { runCatching { aReqs[idx.coerceIn(0, aReqs.lastIndex)].requestFocus() }.isSuccess }
                        )
                        .tvFocusItem(
                            focusRequester = pReqs[idx],
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedSize = spVal }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LtrText(
                        text = lbl,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isAr) "شفافية الخلفية:" else "Background Opacity:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            alphaChoices.forEachIndexed { idx, (alphaVal, lbl) ->
                val active = kotlin.math.abs(selectedAlpha - alphaVal) < 0.08f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvHorizontalStepItem(
                            index = idx, totalCount = alphaChoices.size, requesters = aReqs, isAr = isAr,
                            onUp = { runCatching { pReqs[idx.coerceIn(0, pReqs.lastIndex)].requestFocus() }.isSuccess },
                            onDown = { runCatching { saveAppReq.requestFocus() }.isSuccess }
                        )
                        .tvFocusItem(
                            focusRequester = aReqs[idx],
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedAlpha = alphaVal }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LtrText(
                        text = lbl,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private fun extractDigitChar(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

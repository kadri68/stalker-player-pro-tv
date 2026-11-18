package com.kadri.stalkerplayerpro.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvFocusTrap
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.local.WatchHistoryEntity
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.data.model.SubtitleCue
import com.kadri.stalkerplayerpro.data.model.SubtitleSearchResult
import com.kadri.stalkerplayerpro.data.network.StalkerApiClient
import com.kadri.stalkerplayerpro.data.network.SubtitleRepository
import com.kadri.stalkerplayerpro.ui.components.TvActionMenuDialog
import com.kadri.stalkerplayerpro.ui.components.TvMenuOption
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.screens.resolvePlayableUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvPlayerScreen(
    initialRequest: PlaybackRequest,
    dao: AppDao,
    settings: TvUserSettings,
    settingsStore: SettingsDataStore,
    isAr: Boolean,
    onExitPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extraColors = LocalTvExtraColors.current

    var currentRequest by remember { mutableStateOf(initialRequest) }
    var currentChannelIdx by remember { mutableIntStateOf(initialRequest.currentChannelIndex) }
    var previousChannelIdx by remember { mutableIntStateOf(initialRequest.currentChannelIndex) }
    var currentEpgTitle by remember { mutableStateOf(initialRequest.subtitleQueryHint) }

    val controller = remember { DualPlayerController(context, settings.preferredAudioLang) }
    val rootFocusRequester = remember { FocusRequester() }

    // حالات واجهة التحكم والقوائم
    var showControls by remember { mutableStateOf(true) }
    var controlInteractTick by remember { mutableIntStateOf(0) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showSubtitleSearchDialog by remember { mutableStateOf(false) }
    var showSubtitleSyncBar by remember { mutableStateOf(false) }
    var showSubtitleStyleDialog by remember { mutableStateOf(false) }
    var showAudioTracksDialog by remember { mutableStateOf(false) }

    // حالة إدخال أرقام القنوات (0-9)
    var digitBuffer by remember { mutableStateOf("") }

    // حالة الترجمة الخارجية والإزاحة المستقلة للترجمة فقط
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var subColor by remember { mutableLongStateOf(settings.subTextColor) }
    var subSizeSp by remember { mutableFloatStateOf(settings.subFontSizeSp) }
    var subBgAlpha by remember { mutableFloatStateOf(settings.subBgAlpha) }

    // حالة الساعة الرقمية
    var clockText by remember { mutableStateOf(LocaleUtils.formatClock()) }

    val anyOverlayModal = showChannelDrawer || showQuickMenu || showSubtitleSearchDialog ||
            showSubtitleStyleDialog || showAudioTracksDialog

    // تشغيل البث وحفظ التقدم عند الخروج
    LaunchedEffect(currentRequest.itemId, currentRequest.streamCmdOrUrl) {
        subtitleCues = emptyList()
        subtitleOffsetMs = 0L
        showControls = true

        val resolvedType = if (currentRequest.contentType == "LIVE") "itv" else "vod"
        val playableUrl = resolvePlayableUrl(currentRequest.portal, currentRequest.streamCmdOrUrl, resolvedType)
        controller.playStream(playableUrl, currentRequest.resumePositionMs)

        // في البث المباشر: جلب اسم البرنامج الحالي من EPG تلقائياً للبحث عن الترجمة
        if (currentRequest.contentType == "LIVE") {
            val epgList = when (currentRequest.portal.protocol) {
                "XTREAM" -> com.kadri.stalkerplayerpro.data.network.XtreamApiClient.getShortEpg(currentRequest.portal, currentRequest.itemId)
                "STALKER" -> StalkerApiClient.getShortEpg(currentRequest.portal, currentRequest.itemId)
                else -> emptyList()
            }
            val nowTitle = epgList.firstOrNull()?.title.orEmpty()
            if (nowTitle.isNotBlank()) {
                currentEpgTitle = nowTitle
            }
        }
    }

    // تحديث موقع التشغيل والساعة وحفظ موضع التوقف تلقائياً (Auto-Resume)
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

    // إخفاء شريط التحكم تلقائياً بعد 5 ثوانٍ
    LaunchedEffect(showControls, anyOverlayModal, showSubtitleSyncBar, controlInteractTick) {
        if (showControls && !anyOverlayModal && !showSubtitleSyncBar) {
            delay(7500)
            showControls = false
        }
    }

    // تنفيذ الانتقال للقناة عند إدخال أرقام (1..9)
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

    // إعادة التركيز لسطح المشغل عند إغلاق القوائم
    LaunchedEffect(anyOverlayModal) {
        if (!anyOverlayModal) {
            delay(60)
            runCatching { rootFocusRequester.requestFocus() }
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

    BackHandler {
        when {
            showSubtitleSyncBar -> showSubtitleSyncBar = false
            showControls -> showControls = false
            else -> onExitPlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (anyOverlayModal) return@onPreviewKeyEvent false

                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    val isLive = currentRequest.contentType == "LIVE"
                    val digit = extractDigitChar(event.key)

                    // 1. أزرار الأرقام (0-9) في البث المباشر
                    if (isLive && digit != null) {
                        if (digit == '0' && digitBuffer.isEmpty()) {
                            // زر 0 للعودة الفورية للقناة السابقة
                            if (previousChannelIdx in currentRequest.channelList.indices) {
                                switchToChannelAtIndex(previousChannelIdx)
                            }
                        } else {
                            digitBuffer += digit
                        }
                        return@onPreviewKeyEvent true
                    }

                    when (event.key) {
                        // 2. أزرار UP / DOWN للتبديل المباشر بين القنوات
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

                        // 3. زر LEFT لفتح قائمة القنوات الجانبية في البث المباشر (أو الترجيع في الأفلام)
                        Key.DirectionLeft -> {
                            if (isLive && !showControls) {
                                showChannelDrawer = true
                                return@onPreviewKeyEvent true
                            } else if (!isLive && !showControls) {
                                controller.seekBy(-10_000L)
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                        }

                        Key.DirectionRight -> {
                            if (!isLive && !showControls) {
                                controller.seekBy(10_000L)
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                        }

                        // 4. زر OK لإظهار / إخفاء شريط التحكم
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!showControls) {
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                        }

                        // 5. زر MENU لفتح قائمة خيارات المشغل السريعة
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
        // 1. طبقة عرض الفيديو (ExoPlayer / LibVLC)
        DualVideoSurface(
            controller = controller,
            modifier = Modifier.fillMaxSize()
        )

        // 2. طبقة الترجمة الخارجية المستقلة (مع إزاحة التوقيت الخاصة بالترجمة فقط)
        SubtitleOverlayRenderer(
            cues = subtitleCues,
            videoPositionMs = controller.currentPositionMs,
            subtitleOffsetMs = subtitleOffsetMs,
            textColor = Color(subColor),
            fontSizeSp = subSizeSp,
            bgAlpha = subBgAlpha
        )

        // 3. مؤشر التحميل المؤقت
        if (controller.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 4. الساعة الرقمية بأرقام لاتينية (0-9) في الزاوية القابلة للتغيير
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
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                LtrText(
                    text = clockText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. شارة رقم القناة المدخل بالأرقام (0-9)
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

        // 6. شريط تقديم وتأخير تزامن الترجمة فقط (-0.5s / -0.1s / +0.1s / +0.5s)
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

        // 7. شريط التحكم السفلي (OSD) مع دعم Timeshift
        if (showControls && !anyOverlayModal) {
            PlayerBottomControlBar(
                request = currentRequest,
                epgTitle = currentEpgTitle,
                controller = controller,
                isAr = isAr,
                onOpenSubtitles = { showSubtitleSearchDialog = true },
                onOpenSync = { showSubtitleSyncBar = !showSubtitleSyncBar },
                onOpenMenu = { controlInteractTick++; showQuickMenu = true },
                onBackClick = onExitPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 8. القائمة الجانبية للقنوات (تُفتح بزر LEFT وتمرر تلقائياً للقناة الحالية)
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

        // 9. قائمة خيارات المشغل السريعة (MENU / Long Press)
        if (showQuickMenu) {
            val menuOptions = listOf(
                TvMenuOption(
                    id = "sub_search",
                    label = if (isAr) "بحث وتحميل ترجمة خارجية" else "Search External Subtitles",
                    icon = Icons.Default.Subtitles
                ),
                TvMenuOption(
                    id = "sub_sync",
                    label = if (isAr) "تزامن الترجمة (${LocaleUtils.formatOffsetSeconds(subtitleOffsetMs)})" else "Subtitle Sync (${LocaleUtils.formatOffsetSeconds(subtitleOffsetMs)})",
                    icon = Icons.Default.Tune
                ),
                TvMenuOption(
                    id = "sub_style",
                    label = if (isAr) "مظهر الترجمة (اللون / الحجم / الخلفية)" else "Subtitle Appearance",
                    icon = Icons.Default.ClosedCaption
                ),
                TvMenuOption(
                    id = "audio_track",
                    label = if (isAr) "اختيار لغة الصوت" else "Audio Tracks",
                    icon = Icons.Default.Audiotrack
                ),
                TvMenuOption(
                    id = "switch_engine",
                    label = if (isAr) "تبديل المحرك (الحالي: ${controller.activeEngine.label})" else "Switch Engine (${controller.activeEngine.label})",
                    icon = Icons.Default.SettingsEthernet
                ),
                TvMenuOption(
                    id = "clock_corner",
                    label = if (isAr) "تغيير زاوية الساعة الرقمية" else "Change Clock Corner",
                    icon = Icons.Default.AccessTime
                )
            )
            TvActionMenuDialog(
                title = if (isAr) "خيارات المشغل" else "Player Options",
                options = menuOptions,
                onSelect = { opt ->
                    when (opt.id) {
                        "sub_search" -> showSubtitleSearchDialog = true
                        "sub_sync" -> showSubtitleSyncBar = true
                        "sub_style" -> showSubtitleStyleDialog = true
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

        // 10. نافذة البحث في OpenSubtitles v1 و Subdl (مع إبقاء سنة الفيلم أو اسم برنامج EPG)
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
                settings = settings,
                isAr = isAr,
                onSubtitleLoaded = { loadedCues ->
                    subtitleCues = loadedCues
                    subtitleOffsetMs = 0L
                    showSubtitleSearchDialog = false
                    Toast.makeText(
                        context,
                        if (isAr) "تم تحميل الترجمة (${loadedCues.size} سطر)" else "Loaded ${loadedCues.size} cues",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showSubtitleSearchDialog = false }
            )
        }

        // 11. نافذة تخصيص مظهر الترجمة
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
                    scope.launch { settingsStore.updateSubtitleStyle(c, s, a) }
                },
                onDismiss = { showSubtitleStyleDialog = false }
            )
        }

        // 12. نافذة اختيار مسار الصوت
        if (showAudioTracksDialog) {
            TvModalDialog(
                title = if (isAr) "مسارات الصوت المتاحة" else "Available Audio Tracks",
                onDismiss = { showAudioTracksDialog = false },
                maxWidth = 460.dp
            ) { firstRequester ->
                if (controller.audioTracks.isEmpty()) {
                    Text(
                        text = if (isAr) "مسار صوتي واحد افتراضي" else "Single default audio track",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(
                            items = controller.audioTracks,
                            key = { idx, tr -> "aud_${tr.id}_$idx" }
                        ) { index, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusItem(
                                        focusRequester = if (index == 0) firstRequester else null,
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
    isAr: Boolean,
    onOpenSubtitles: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenMenu: () -> Unit,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extraColors = LocalTvExtraColors.current
    val isLive = request.contentType == "LIVE"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(extraColors.panelOverlay)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.tvFocusItem(shape = RoundedCornerShape(8.dp), unfocusedBackgroundColor = Color.White.copy(alpha = 0.16f), onClick = onBackClick).padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (epgTitle.isNotBlank()) {
                    Text(
                        text = epgTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            LtrText(
                text = controller.activeEngine.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // شريط التقدم للأفلام والمسلسلات
        if (!isLive && controller.durationMs > 0L) {
            val progress = (controller.currentPositionMs.toFloat() / controller.durationMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LtrText(
                    text = LocaleUtils.formatDuration(controller.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                LtrText(
                    text = LocaleUtils.formatDuration(controller.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // أزرار التحكم بالريموت (تشغيل/إيقاف، Timeshift، الترجمة، الخيارات)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayerControlChip(
                icon = Icons.Default.Replay10,
                label = "-15s",
                onClick = { controller.seekBy(-15_000L) }
            )
            PlayerControlChip(
                icon = if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (controller.isPlaying) (if (isAr) "إيقاف" else "Pause") else (if (isAr) "تشغيل" else "Play"),
                onClick = { controller.togglePlayPause() }
            )
            PlayerControlChip(
                icon = Icons.Default.Forward10,
                label = "+15s",
                onClick = { controller.seekBy(15_000L) }
            )
            if (isLive) {
                PlayerControlChip(
                    icon = Icons.Default.FiberManualRecord,
                    label = "LIVE",
                    onClick = { controller.jumpToLiveEdge() }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            PlayerControlChip(
                icon = Icons.Default.Subtitles,
                label = if (isAr) "الترجمة" else "Subtitles",
                onClick = onOpenSubtitles
            )
            PlayerControlChip(
                icon = Icons.Default.Tune,
                label = if (isAr) "التزامن" else "Sync",
                onClick = onOpenSync
            )
            PlayerControlChip(
                icon = Icons.Default.SettingsEthernet,
                label = "MENU",
                onClick = onOpenMenu
            )
        }
    }
}

@Composable
private fun PlayerControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .tvFocusItem(
                shape = RoundedCornerShape(8.dp),
                unfocusedBackgroundColor = Color.White.copy(alpha = 0.14f),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        LtrText(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/**
 * شريط تقديم وتأخير التزامن الخاص بملف الترجمة فقط (-0.5s / -0.1s / +0.1s / +0.5s)
 */
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
            style = MaterialTheme.typography.titleMedium,
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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isAr) "تم" else "Done",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * القائمة الجانبية للقنوات أثناء البث (تفتح بزر LEFT وتمرر تلقائياً للقناة الحالية)
 */
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
    val extraColors = LocalTvExtraColors.current

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusItem(
                                    focusRequester = if (isCurrent) firstRequester else null,
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

/**
 * نافذة البحث والتحميل المباشر للترجمة الخارجية (OpenSubtitles v1 + Subdl)
 * تحافظ على سنة إنتاج الفيلم في مربع البحث مع بحث احتياطي تلقائي بدون السنة عند الحاجة
 */
@Composable
private fun SubtitleSearchDialog(
    initialQuery: String,
    settings: TvUserSettings,
    isAr: Boolean,
    onSubtitleLoaded: (List<SubtitleCue>) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchBoxText by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    fun executeDualSearch() {
        if (searchBoxText.isBlank()) return
        scope.launch {
            isSearching = true
            statusMsg = ""

            // استخراج السنة إن وجدت لتمريرها للمحرك مع الإبقاء عليها ظاهرة في مربع البحث
            val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(searchBoxText)
            val extractedYear = yearMatch?.value?.toIntOrNull()
            val cleanTitle = if (yearMatch != null) {
                searchBoxText.replace(yearMatch.value, "").replace(Regex("""[\(\)\[\]]"""), "").trim()
            } else {
                searchBoxText.trim()
            }

            // تجديد توكن OpenSubtitles تلقائياً إذا توفرت بيانات الدخول
            var jwt = settings.openSubJwtToken
            if (jwt.isBlank() && settings.openSubApiKey.isNotBlank() && settings.openSubUsername.isNotBlank()) {
                jwt = SubtitleRepository.loginOpenSubtitles(
                    settings.openSubApiKey,
                    settings.openSubUsername,
                    settings.openSubPassword
                ).orEmpty()
            }

            val osList = SubtitleRepository.searchOpenSubtitles(
                apiKey = settings.openSubApiKey,
                jwtToken = jwt,
                rawQuery = cleanTitle,
                year = extractedYear
            )
            val subdlList = SubtitleRepository.searchSubdl(
                apiKey = settings.subdlApiKey,
                rawQuery = cleanTitle,
                year = extractedYear
            )

            results = osList + subdlList
            if (results.isEmpty()) {
                statusMsg = if (isAr) {
                    "لم يتم العثور على ترجمات. تأكد من مفاتيح API في الإعدادات أو عدّل اسم البحث."
                } else {
                    "No subtitles found. Verify API keys in Settings or adjust title."
                }
            }
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        if (searchBoxText.isNotBlank()) {
            executeDualSearch()
        }
    }

    TvModalDialog(
        title = if (isAr) "البحث عن ترجمة (OpenSubtitles / Subdl)" else "Search Subtitles",
        onDismiss = onDismiss,
        maxWidth = 640.dp
    ) { firstRequester ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchBoxText,
                onValueChange = { searchBoxText = it },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
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

        Spacer(modifier = Modifier.height(10.dp))

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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) {
                itemsIndexed(
                    items = results,
                    key = { idx, item -> "sub_${item.source}_${item.id}_$idx" }
                ) { _, subItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusItem(
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
        0xFF00E676 to (if (isAr) "أخضر" else "Green")
    )

    TvModalDialog(
        title = if (isAr) "تخصيص مظهر الترجمة" else "Subtitle Appearance",
        onDismiss = onDismiss,
        maxWidth = 500.dp
    ) { firstRequester ->
        Text(
            text = if (isAr) "لون النص:" else "Text Color:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colorChoices.forEachIndexed { idx, (hex, label) ->
                val active = selectedColor == hex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            focusRequester = if (idx == 0) firstRequester else null,
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
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isAr) "حجم الخط:" else "Font Size:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20f to "20sp", 24f to "24sp", 28f to "28sp", 34f to "34sp").forEach { (spVal, lbl) ->
                val active = selectedSize == spVal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
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
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isAr) "شفافية الخلفية:" else "Background Opacity:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f to "0%", 0.45f to "45%", 0.65f to "65%", 0.9f to "90%").forEach { (alphaVal, lbl) ->
                val active = selectedAlpha == alphaVal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
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

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        shape = RoundedCornerShape(10.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onApply(selectedColor, selectedSize, selectedAlpha)
                            onDismiss()
                        }
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isAr) "حفظ" else "Save",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun extractDigitChar(key: Key): Char? {
    return when (key) {
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
}

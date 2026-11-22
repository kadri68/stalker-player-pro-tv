package com.kadri.stalkerplayerpro

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kadri.stalkerplayerpro.core.theme.AppThemeId
import com.kadri.stalkerplayerpro.core.theme.StalkerTvTheme
import com.kadri.stalkerplayerpro.core.theme.TvFontScale
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.player.TvPlayerScreen
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.screens.BUILTIN_FREE_AR_M3U_URL
import com.kadri.stalkerplayerpro.ui.screens.GlobalFavoritesScreen
import com.kadri.stalkerplayerpro.ui.screens.LiveTvScreen
import com.kadri.stalkerplayerpro.ui.screens.PortalsScreen
import com.kadri.stalkerplayerpro.ui.screens.SettingsScreen
import com.kadri.stalkerplayerpro.ui.screens.VodScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_PIP_CLOSE = "com.kadri.stalkerplayerpro.ACTION_PIP_CLOSE"
        @Volatile
        var isLaunchingExternalPicker: Boolean = false
    }

    private var isInPipModeState by mutableStateOf(false)
    private var isPlayerActiveForPip by mutableStateOf(false)
    private var forceStopPlaybackSignal by mutableIntStateOf(0)

    private val pipCloseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_CLOSE) {
                forceStopPlaybackSignal++
                isPlayerActiveForPip = false
                isInPipModeState = false
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        ContextCompat.registerReceiver(
            this,
            pipCloseReceiver,
            IntentFilter(ACTION_PIP_CLOSE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val app = application as StalkerApp

        setContent {
            val context = LocalContext.current
            val isTv = remember { isTvDevice(context) }
            val settings by app.settingsStore.settingsFlow.collectAsState(initial = TvUserSettings())
            val isAr = settings.language == "ar"
            val layoutDir = if (isAr) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                StalkerTvTheme(
                    themeId = AppThemeId.fromId(settings.themeId),
                    fontScale = TvFontScale.fromId(settings.fontScaleId, isTvDefault = isTv)
                ) {
                    MainTvRoot(
                        app = app,
                        settings = settings,
                        isAr = isAr,
                        isTv = isTv,
                        isInPipMode = isInPipModeState,
                        forceStopSignal = forceStopPlaybackSignal,
                        onPlayerActiveChanged = { active ->
                            isPlayerActiveForPip = active
                            updateAutoPipParams(active)
                        },
                        onEnterPip = { requestPipMode() },
                        onFinishApp = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isLaunchingExternalPicker = false
    }

    override fun onStart() {
        super.onStart()
        isInPipModeState = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipCloseReceiver) }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLaunchingExternalPicker) return
        if (isPlayerActiveForPip && !isInPipModeState) {
            requestPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val previouslyInPip = isInPipModeState
        isInPipModeState = isInPictureInPictureMode
        // لا يتم إنهاء التطبيق إلا إذا كان المستخدم بالفعل داخل وضع صورة داخل صورة (PiP) وأغلق نافذة PiP من النظام
        if (previouslyInPip && !isInPictureInPictureMode && lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED) {
            forceStopPlaybackSignal++
            isPlayerActiveForPip = false
            finishAndRemoveTask()
        }
    }

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val closeIntent = Intent(ACTION_PIP_CLOSE).setPackage(packageName)
        val closePendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val closeIcon = AndroidIcon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel)
        val closeAction = RemoteAction(closeIcon, "Close", "Close Player", closePendingIntent)

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(closeAction))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updateAutoPipParams(autoEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                buildPipParams(autoEnter)?.let { setPictureInPictureParams(it) }
            }
        }
    }

    private fun requestPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                buildPipParams(true)?.let {
                    if (enterPictureInPictureMode(it)) {
                        isInPipModeState = true
                    }
                }
            }
        }
    }
}

fun isTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTouch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return isTvMode || hasLeanback || !hasTouch
}

private enum class TvTab(
    val index: Int,
    val titleAr: String,
    val titleEn: String,
    val icon: ImageVector
) {
    LIVE(0, "البث المباشر", "Live TV", Icons.Default.LiveTv),
    MOVIES(1, "الأفلام", "Movies", Icons.Default.Movie),
    SERIES(2, "المسلسلات", "Series", Icons.Default.VideoLibrary),
    FAVORITES(3, "المفضلة", "Favorites", Icons.Default.Star),
    PORTALS(4, "البوابات", "Portals", Icons.Default.Dns),
    SETTINGS(5, "الإعدادات", "Settings", Icons.Default.Settings)
}

@Composable
private fun MainTvRoot(
    app: StalkerApp,
    settings: TvUserSettings,
    isAr: Boolean,
    isTv: Boolean,
    isInPipMode: Boolean,
    forceStopSignal: Int,
    onPlayerActiveChanged: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onFinishApp: () -> Unit
) {
    val dao = app.database.appDao()
    val activePortal by dao.observeActivePortal().collectAsState(initial = null)
    var selectedTab by remember { mutableIntStateOf(TvTab.LIVE.index) }
    var focusedTabIndex by remember { mutableIntStateOf(-1) }
    var activePlayback by remember { mutableStateOf<PlaybackRequest?>(null) }
    var headerClock by remember { mutableStateOf(LocaleUtils.formatClock()) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    var lastFocusedPortalId by remember { mutableLongStateOf(-1L) }
    var lastFocusedLiveChannelId by remember { mutableStateOf<String?>(null) }
    var lastFocusedMovieId by remember { mutableStateOf<String?>(null) }
    var lastFocusedSeriesId by remember { mutableStateOf<String?>(null) }

    val tabRequesters = remember { List(TvTab.entries.size) { FocusRequester() } }
    val topBarScrollState = rememberScrollState()
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusedTabIndex) {
        if (focusedTabIndex >= 0) {
            val maxIdx = (TvTab.entries.size - 1).coerceAtLeast(1)
            val targetScroll = if (focusedTabIndex == 0) 0 else (topBarScrollState.maxValue * focusedTabIndex) / maxIdx
            runCatching { topBarScrollState.animateScrollTo(targetScroll) }
        }
    }

    LaunchedEffect(activePlayback != null, settings.autoPipEnabled) {
        onPlayerActiveChanged(activePlayback != null)
    }

    LaunchedEffect(forceStopSignal) {
        if (forceStopSignal > 0) {
            activePlayback = null
        }
    }

    LaunchedEffect(Unit) {
        val existingPortals = dao.getAllPortals()
        val freePortal = existingPortals.find { it.url == BUILTIN_FREE_AR_M3U_URL }
        if (freePortal == null) {
            val insertedId = dao.insertPortal(
                PortalEntity(
                    name = "AR-TV",
                    protocol = "M3U",
                    url = BUILTIN_FREE_AR_M3U_URL,
                    statusText = "Active (Free AR)",
                    isFavorite = true
                )
            )
            if (existingPortals.none { it.isActive }) {
                dao.setActivePortal(insertedId)
            }
        } else if (existingPortals.none { it.isActive }) {
            dao.setActivePortal(freePortal.id)
        }

        selectedTab = TvTab.LIVE.index
        delay(120)
        runCatching { tabRequesters[TvTab.LIVE.index].requestFocus() }
        while (true) {
            delay(15_000)
            headerClock = LocaleUtils.formatClock()
        }
    }

    val playback = activePlayback
    if (playback != null) {
        TvPlayerScreen(
            initialRequest = playback,
            dao = dao,
            settings = settings,
            settingsStore = app.settingsStore,
            isAr = isAr,
            isInPipMode = isInPipMode,
            onEnterPip = onEnterPip,
            onExitPlayer = { activePlayback = null }
        )
        return
    }

    BackHandler(enabled = !showExitConfirmDialog) {
        if (selectedTab != TvTab.PORTALS.index) {
            selectedTab = TvTab.PORTALS.index
            runCatching { tabRequesters[TvTab.PORTALS.index].requestFocus() }
        } else {
            showExitConfirmDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(showExitConfirmDialog)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(topBarScrollState)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    LtrText(
                        text = "IPTV STALKER",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    val remainingDaysText = remember(activePortal, isAr) {
                        val portal = activePortal
                        when {
                            portal == null -> ""
                            portal.url == BUILTIN_FREE_AR_M3U_URL -> if (isAr) "مجاني / غير محدود" else "Free / Unlimited"
                            portal.expiryTimestampMs > 0L -> {
                                val now = System.currentTimeMillis()
                                val daysLeft = ((portal.expiryTimestampMs - now) / 86_400_000L).toInt()
                                when {
                                    daysLeft > 10 -> if (isAr) "المتبقي: ${LocaleUtils.formatInt(daysLeft)} يوم" else "$daysLeft days left"
                                    daysLeft in 3..10 -> if (isAr) "المتبقي: ${LocaleUtils.formatInt(daysLeft)} أيام" else "$daysLeft days left"
                                    daysLeft == 2 -> if (isAr) "المتبقي: يومان" else "2 days left"
                                    daysLeft == 1 -> if (isAr) "المتبقي: يوم واحد" else "1 day left"
                                    daysLeft == 0 -> if (isAr) "ينتهي اليوم!" else "Expires today!"
                                    else -> if (isAr) "منتهي الصلاحية" else "Expired"
                                }
                            }
                            portal.statusText.isNotBlank() -> portal.statusText
                            else -> if (isAr) "غير محدد" else "N/A"
                        }
                    }

                    if (remainingDaysText.isNotBlank()) {
                        val remainingColor = remember(activePortal) {
                            val portal = activePortal
                            when {
                                portal == null -> Color.Gray
                                portal.url == BUILTIN_FREE_AR_M3U_URL -> Color(0xFF00E676)
                                portal.expiryTimestampMs > 0L -> {
                                    val daysLeft = ((portal.expiryTimestampMs - System.currentTimeMillis()) / 86_400_000L).toInt()
                                    when {
                                        daysLeft > 15 -> Color(0xFF00E5FF)
                                        daysLeft in 3..15 -> Color(0xFFFFB300)
                                        daysLeft in 0..2 -> Color(0xFFFF9100)
                                        else -> Color(0xFFFF5252)
                                    }
                                }
                                else -> Color(0xFF00E5FF)
                            }
                        }

                        Text(
                            text = remainingDaysText,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = remainingColor,
                            maxLines = 1
                        )
                    }
                }

                TvTab.entries.forEachIndexed { idx, tab ->
                    val isSelected = selectedTab == tab.index
                    val isVisorFocused = focusedTabIndex == idx
                    val isPortalsTab = tab == TvTab.PORTALS

                    val tabBgColor = when {
                        isVisorFocused -> Color(0xFF00E5FF)
                        isPortalsTab && isSelected -> Color(0xFFFFB300)
                        isPortalsTab -> Color(0xFFFFB300).copy(alpha = 0.28f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                    val tabContentColor = when {
                        isVisorFocused -> Color.Black
                        isPortalsTab && isSelected -> Color.Black
                        isPortalsTab -> Color(0xFFFFD54F)
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val borderColor = when {
                        isVisorFocused -> Color.White
                        isPortalsTab -> Color(0xFFFFB300)
                        else -> Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .then(
                                if (borderColor != Color.Transparent) {
                                    Modifier.border(
                                        width = if (isVisorFocused) 2.5.dp else 1.5.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                } else Modifier
                            )
                            .focusProperties { down = contentFocusRequester }
                            .onFocusChanged { fState ->
                                if (fState.isFocused) {
                                    focusedTabIndex = idx
                                    if (selectedTab != tab.index) {
                                        selectedTab = tab.index
                                    }
                                } else if (focusedTabIndex == idx) {
                                    focusedTabIndex = -1
                                }
                            }
                            .tvFocusItem(
                                focusRequester = tabRequesters[idx],
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = tabBgColor,
                                onClick = {
                                    selectedTab = tab.index
                                    focusedTabIndex = -1
                                    runCatching { contentFocusRequester.requestFocus() }
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = tabContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isAr) tab.titleAr else tab.titleEn,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected || isPortalsTab || isVisorFocused) FontWeight.Bold else FontWeight.Medium,
                            color = tabContentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (activePortal != null) {
                    LtrText(
                        text = "[${activePortal!!.name.take(10)}]",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                LtrText(
                    text = headerClock,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                // زر الخروج الأحمر يظهر فقط في نسخة الهاتف (!isTv) عبر اللمس ولا يلتقط تركيز الريموت أبداً
                if (!isTv) {
                    Box(
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .tvFocusItem(
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                                onClick = { showExitConfirmDialog = true }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                if (runCatching { tabRequesters[selectedTab].requestFocus() }.isSuccess) {
                                    return@onKeyEvent true
                                }
                            }
                            false
                        }.fillMaxWidth()) {
                when (selectedTab) {
                    TvTab.LIVE.index -> LiveTvScreen(
                        portal = activePortal,
                        settings = settings,
                        settingsStore = app.settingsStore,
                        onNavigateToTopBar = { runCatching { tabRequesters[TvTab.LIVE.index].requestFocus() }.isSuccess },
                        dao = dao,
                        parentalPin = settings.parentalPin,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        lastFocusedChannelId = lastFocusedLiveChannelId,
                        onChannelFocused = { chId -> lastFocusedLiveChannelId = chId },
                        onGoToPortals = { selectedTab = TvTab.PORTALS.index },
                        onPlayChannel = { req ->
                            lastFocusedLiveChannelId = req.itemId
                            activePlayback = req
                        }
                    )
                    TvTab.MOVIES.index -> VodScreen(
                        portal = activePortal,
                        dao = dao,
                        settings = settings,
                        settingsStore = app.settingsStore,
                        isSeriesMode = false,
                        onNavigateToTopBar = { runCatching { tabRequesters[TvTab.MOVIES.index].requestFocus() }.isSuccess },
                        parentalPin = settings.parentalPin,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        lastFocusedItemId = lastFocusedMovieId,
                        onItemFocused = { itemId -> lastFocusedMovieId = itemId },
                        onGoToPortals = { selectedTab = TvTab.PORTALS.index },
                        onPlayVod = { req ->
                            lastFocusedMovieId = req.itemId
                            activePlayback = req
                        }
                    )
                    TvTab.SERIES.index -> VodScreen(
                        portal = activePortal,
                        dao = dao,
                        settings = settings,
                        settingsStore = app.settingsStore,
                        isSeriesMode = true,
                        onNavigateToTopBar = { runCatching { tabRequesters[TvTab.SERIES.index].requestFocus() }.isSuccess },
                        parentalPin = settings.parentalPin,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        lastFocusedItemId = lastFocusedSeriesId,
                        onItemFocused = { itemId -> lastFocusedSeriesId = itemId },
                        onGoToPortals = { selectedTab = TvTab.PORTALS.index },
                        onPlayVod = { req ->
                            lastFocusedSeriesId = req.seriesId.ifBlank { req.itemId }
                            activePlayback = req
                        }
                    )
                    TvTab.FAVORITES.index -> GlobalFavoritesScreen(
                        dao = dao,
                        settings = settings,
                        settingsStore = app.settingsStore,
                        isAr = isAr,
                        onNavigateToTopBar = { runCatching { tabRequesters[TvTab.FAVORITES.index].requestFocus() }.isSuccess },
                        initialContentFocusRequester = contentFocusRequester,
                        onPlayFavorite = { req -> activePlayback = req }
                    )
                    TvTab.PORTALS.index -> PortalsScreen(
                        dao = dao,
                        onNavigateToTopBar = { runCatching { tabRequesters[TvTab.PORTALS.index].requestFocus() }.isSuccess },
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        lastFocusedPortalId = lastFocusedPortalId,
                        onPortalFocused = { pId -> lastFocusedPortalId = pId },
                        onPortalActivated = { activated ->
                            lastFocusedPortalId = activated.id
                            lastFocusedLiveChannelId = null
                            lastFocusedMovieId = null
                            lastFocusedSeriesId = null
                            selectedTab = TvTab.LIVE.index
                        }
                    )
                    TvTab.SETTINGS.index -> SettingsScreen(
                        dao = dao,
                        settings = settings,
                        settingsStore = app.settingsStore,
                        isAr = isAr,
                        isTv = isTv,
                        onNavigateToTopBar = { runCatching { tabRequesters[TvTab.SETTINGS.index].requestFocus() }.isSuccess },
                        initialContentFocusRequester = contentFocusRequester
                    )
                }
            }
        }

        if (showExitConfirmDialog) {
            TvModalDialog(
                title = if (isAr) "تأكيد الخروج" else "Confirm Exit",
                onDismiss = { showExitConfirmDialog = false },
                maxWidth = 420.dp,
                restoreFocusRequester = tabRequesters[TvTab.PORTALS.index]
            ) { firstRequester ->
                Text(
                    text = if (isAr) "هل تريد الخروج من تطبيق stalker-player pro؟" else "Do you want to exit stalker-player pro?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    Box(
                        modifier = Modifier
                            .tvFocusItem(
                                focusRequester = firstRequester,
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { showExitConfirmDialog = false }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isAr) "إلغاء" else "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .tvFocusItem(
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.error,
                                onClick = onFinishApp
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isAr) "نعم، خروج" else "Yes, Exit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }
}

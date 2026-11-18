package com.kadri.stalkerplayerpro

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.player.TvPlayerScreen
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.screens.LiveTvScreen
import com.kadri.stalkerplayerpro.ui.screens.PortalsScreen
import com.kadri.stalkerplayerpro.ui.screens.SettingsScreen
import com.kadri.stalkerplayerpro.ui.screens.VodScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

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
                        onFinishApp = { finish() }
                    )
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
    return isTvMode || (hasLeanback && !hasTouch)
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
    PORTALS(3, "البوابات", "Portals", Icons.Default.Dns),
    SETTINGS(4, "الإعدادات", "Settings", Icons.Default.Settings)
}

@Composable
private fun MainTvRoot(
    app: StalkerApp,
    settings: TvUserSettings,
    isAr: Boolean,
    isTv: Boolean,
    onFinishApp: () -> Unit
) {
    val dao = app.database.appDao()
    val activePortal by dao.observeActivePortal().collectAsState(initial = null)
    var selectedTab by remember { mutableIntStateOf(TvTab.PORTALS.index) }
    var activePlayback by remember { mutableStateOf<PlaybackRequest?>(null) }
    var headerClock by remember { mutableStateOf(LocaleUtils.formatClock()) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val tabRequesters = remember { List(TvTab.entries.size) { FocusRequester() } }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { tabRequesters[selectedTab].requestFocus() }
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
            onExitPlayer = { activePlayback = null }
        )
        return
    }

    BackHandler(enabled = !showExitConfirmDialog) {
        showExitConfirmDialog = true
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
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                            val moved = runCatching { contentFocusRequester.requestFocus() }.isSuccess
                            if (moved) return@onPreviewKeyEvent true
                        }
                        false
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LtrText(
                    text = "IPTV STALKER",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                TvTab.entries.forEachIndexed { idx, tab ->
                    val isSelected = selectedTab == tab.index
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .focusProperties { down = contentFocusRequester }
                            .tvFocusItem(
                                focusRequester = tabRequesters[idx],
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                onClick = {
                                    selectedTab = tab.index
                                    runCatching { contentFocusRequester.requestFocus() }
                                }
                            )
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAr) tab.titleAr else tab.titleEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (activePortal != null) {
                    LtrText(
                        text = activePortal!!.name.take(5),
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

                // زر الخروج مع نافذة التأكيد
                Box(
                    modifier = Modifier
                        .focusProperties { down = contentFocusRequester }
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    TvTab.LIVE.index -> LiveTvScreen(
                        portal = activePortal,
                        dao = dao,
                        parentalPin = settings.parentalPin,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        onGoToPortals = { selectedTab = TvTab.PORTALS.index },
                        onPlayChannel = { req -> activePlayback = req }
                    )
                    TvTab.MOVIES.index -> VodScreen(
                        portal = activePortal,
                        dao = dao,
                        isSeriesMode = false,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        onGoToPortals = { selectedTab = TvTab.PORTALS.index },
                        onPlayVod = { req -> activePlayback = req }
                    )
                    TvTab.SERIES.index -> VodScreen(
                        portal = activePortal,
                        dao = dao,
                        isSeriesMode = true,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        onGoToPortals = { selectedTab = TvTab.PORTALS.index },
                        onPlayVod = { req -> activePlayback = req }
                    )
                    TvTab.PORTALS.index -> PortalsScreen(
                        dao = dao,
                        isAr = isAr,
                        initialContentFocusRequester = contentFocusRequester,
                        onPortalActivated = { selectedTab = TvTab.LIVE.index }
                    )
                    TvTab.SETTINGS.index -> SettingsScreen(
                        dao = dao,
                        settings = settings,
                        settingsStore = app.settingsStore,
                        isAr = isAr,
                        isTv = isTv,
                        initialContentFocusRequester = contentFocusRequester
                    )
                }
            }
        }

        // نافذة تأكيد الخروج من التطبيق
        if (showExitConfirmDialog) {
            TvModalDialog(
                title = if (isAr) "تأكيد الخروج" else "Confirm Exit",
                onDismiss = { showExitConfirmDialog = false },
                maxWidth = 420.dp
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

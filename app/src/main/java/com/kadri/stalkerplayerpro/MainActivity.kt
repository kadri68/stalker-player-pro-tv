package com.kadri.stalkerplayerpro

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
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
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.player.TvPlayerScreen
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
            val settings by app.settingsStore.settingsFlow.collectAsState(initial = TvUserSettings())
            val isAr = settings.language == "ar"
            val layoutDir = if (isAr) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                StalkerTvTheme(
                    themeId = AppThemeId.fromId(settings.themeId),
                    fontScale = TvFontScale.fromId(settings.fontScaleId)
                ) {
                    MainTvRoot(app = app, settings = settings, isAr = isAr)
                }
            }
        }
    }
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
    isAr: Boolean
) {
    val dao = app.database.appDao()
    val activePortal by dao.observeActivePortal().collectAsState(initial = null)
    var selectedTab by remember { mutableIntStateOf(TvTab.PORTALS.index) }
    var activePlayback by remember { mutableStateOf<PlaybackRequest?>(null) }
    var headerClock by remember { mutableStateOf(LocaleUtils.formatClock()) }
    val firstTabRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstTabRequester.requestFocus() }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LtrText(
                text = "IPTV STALKER",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(8.dp))

            TvTab.entries.forEachIndexed { idx, tab ->
                val isSelected = selectedTab == tab.index
                Row(
                    modifier = Modifier
                        .tvFocusItem(
                            focusRequester = if (idx == 0) firstTabRequester else null,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            onClick = { selectedTab = tab.index }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAr) tab.titleAr else tab.titleEn,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (activePortal != null) {
                Text(
                    text = activePortal!!.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            LtrText(
                text = headerClock,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                TvTab.LIVE.index -> LiveTvScreen(
                    portal = activePortal,
                    dao = dao,
                    parentalPin = settings.parentalPin,
                    isAr = isAr,
                    onPlayChannel = { req -> activePlayback = req }
                )
                TvTab.MOVIES.index -> VodScreen(
                    portal = activePortal,
                    dao = dao,
                    isSeriesMode = false,
                    isAr = isAr,
                    onPlayVod = { req -> activePlayback = req }
                )
                TvTab.SERIES.index -> VodScreen(
                    portal = activePortal,
                    dao = dao,
                    isSeriesMode = true,
                    isAr = isAr,
                    onPlayVod = { req -> activePlayback = req }
                )
                TvTab.PORTALS.index -> PortalsScreen(
                    dao = dao,
                    isAr = isAr,
                    onPortalActivated = { selectedTab = TvTab.LIVE.index }
                )
                TvTab.SETTINGS.index -> SettingsScreen(
                    dao = dao,
                    settings = settings,
                    settingsStore = app.settingsStore,
                    isAr = isAr
                )
            }
        }
    }
}

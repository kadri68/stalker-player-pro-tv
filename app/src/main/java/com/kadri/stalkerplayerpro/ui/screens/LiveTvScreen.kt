package com.kadri.stalkerplayerpro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvSafeSearchBar
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvHorizontalStepItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.CategoryRuleEntity
import com.kadri.stalkerplayerpro.data.local.ChannelOverrideEntity
import com.kadri.stalkerplayerpro.data.local.FavoriteEntity
import com.kadri.stalkerplayerpro.data.local.LockedGroupEntity
import com.kadri.stalkerplayerpro.data.local.PortalCacheStore
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.local.WatchHistoryEntity
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.ChannelItem
import com.kadri.stalkerplayerpro.data.model.EpgProgram
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.data.network.M3uStreamParser
import com.kadri.stalkerplayerpro.data.network.StalkerApiClient
import com.kadri.stalkerplayerpro.data.network.XtreamApiClient
import com.kadri.stalkerplayerpro.ui.components.TvActionMenuDialog
import com.kadri.stalkerplayerpro.ui.components.TvMenuOption
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.components.TvPinDialog
import com.kadri.stalkerplayerpro.ui.components.TvTextInputDialog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val liveSessionMemoryCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<List<CategoryItem>, List<ChannelItem>>>()

@Composable
fun LiveTvScreen(
    portal: PortalEntity?,
    dao: AppDao,
    settings: TvUserSettings = TvUserSettings(),
    settingsStore: SettingsDataStore? = null,
    parentalPin: String,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateToTopBar: () -> Boolean = { false },
    initialSelectedCategoryId: String = "ALL",
    onCategorySelected: (String) -> Unit = {},
    lastFocusedChannelId: String? = null,
    onChannelFocused: (String) -> Unit = {},
    onGoToPortals: () -> Unit = {},
    onPlayChannel: (PlaybackRequest) -> Unit
) {
    if (portal == null) {
        LiveTvEmptyPortalView(
            isAr = isAr,
            initialContentFocusRequester = initialContentFocusRequester,
            onGoToPortals = onGoToPortals
        )
    } else {
        LiveTvContent(
            portal = portal,
            dao = dao,
            settings = settings,
            settingsStore = settingsStore,
            parentalPin = parentalPin,
            isAr = isAr,
            initialContentFocusRequester = initialContentFocusRequester,
            onNavigateToTopBar = onNavigateToTopBar,
            initialSelectedCategoryId = initialSelectedCategoryId,
            onCategorySelected = onCategorySelected,
            lastFocusedChannelId = lastFocusedChannelId,
            onChannelFocused = onChannelFocused,
            onPlayChannel = onPlayChannel
        )
    }
}

@Composable
private fun LiveTvEmptyPortalView(
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester,
    onGoToPortals: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isAr) "يرجى اختيار وتفعيل حساب من قسم «البوابات» أولاً" else "Please activate a portal first",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        focusRequester = initialContentFocusRequester,
                        shape = RoundedCornerShape(10.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                        onClick = onGoToPortals
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isAr) "الذهاب إلى البوابات" else "Go to Portals",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun LiveTvContent(
    portal: PortalEntity,
    dao: AppDao,
    settings: TvUserSettings = TvUserSettings(),
    settingsStore: SettingsDataStore? = null,
    parentalPin: String,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateToTopBar: () -> Boolean = { false },
    initialSelectedCategoryId: String = "ALL",
    onCategorySelected: (String) -> Unit = {},
    lastFocusedChannelId: String? = null,
    onChannelFocused: (String) -> Unit = {},
    onPlayChannel: (PlaybackRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extraColors = LocalTvExtraColors.current

    var categories by remember(portal.id) { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var rawChannels by remember(portal.id) { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var isLoading by remember(portal.id) { mutableStateOf(true) }
    var hasError by remember(portal.id) { mutableStateOf(false) }

    var selectedCategoryId by remember(portal.id) { mutableStateOf(initialSelectedCategoryId) }
    var unlockedSessionGroups by remember(portal.id) { mutableStateOf<Set<String>>(emptySet()) }

    var searchQuery by remember(portal.id) { mutableStateOf("") }
    var debouncedQuery by remember(portal.id) { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(350)
        debouncedQuery = searchQuery.trim()
    }

    val favorites by dao.observeFavorites(portal.id, "LIVE").collectAsState(initial = emptyList())
    val overrides by dao.observeChannelOverrides(portal.id).collectAsState(initial = emptyList())
    val lockedGroups by dao.observeLockedGroups(portal.id).collectAsState(initial = emptyList())
    val categoryRules by dao.observeCategoryRules(portal.id, "LIVE").collectAsState(initial = emptyList())
    val allWatchHistory by dao.observeWatchHistory(portal.id).collectAsState(initial = emptyList())
    val liveWatchHistory = remember(allWatchHistory) { allWatchHistory.filter { it.contentType == "LIVE" } }
    var showLiveWatchHistoryDialog by remember { mutableStateOf(false) }

    val rulesMap = remember(categoryRules) { categoryRules.associateBy { it.categoryId } }
    var showCategoryManager by remember(portal.id) { mutableStateOf(false) }

    val favIdSet = remember(favorites) { favorites.map { it.itemId }.toSet() }
    val overrideMap = remember(overrides) { overrides.associateBy { it.channelId } }
    val lockedIdSet = remember(lockedGroups, categoryRules) {
        (lockedGroups.map { it.categoryId } + categoryRules.filter { it.isLocked }.map { it.categoryId }).toSet()
    }
    val hiddenIdSet = remember(categoryRules) { categoryRules.filter { it.isHidden }.map { it.categoryId }.toSet() }

    var pendingPinCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var pendingPinActionLockToggle by remember { mutableStateOf<CategoryItem?>(null) }
    var pinErrorText by remember { mutableStateOf<String?>(null) }

    var channelForMenu by remember { mutableStateOf<ChannelItem?>(null) }
    var channelForRename by remember { mutableStateOf<ChannelItem?>(null) }
    var channelForEpg by remember { mutableStateOf<ChannelItem?>(null) }
    var epgPrograms by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var isLoadingEpg by remember { mutableStateOf(false) }
    var hasHandledStartupAutoPlay by remember(portal.id) { mutableStateOf(false) }

    val anyPopupOpen = showCategoryManager || pendingPinCategory != null || pendingPinActionLockToggle != null ||
            channelForMenu != null || channelForRename != null || channelForEpg != null || showLiveWatchHistoryDialog

    fun loadLiveContent(forceRefresh: Boolean = false) {
        scope.launch {
            try {
                if (!forceRefresh) {
                    val memCached = liveSessionMemoryCache[portal.id]
                    if (memCached != null && memCached.second.isNotEmpty()) {
                        categories = memCached.first
                        rawChannels = memCached.second
                        isLoading = false
                        hasError = false
                        return@launch
                    }
                    val cached = PortalCacheStore.loadLiveCache(context, portal.id)
                    if (cached != null && cached.second.isNotEmpty()) {
                        liveSessionMemoryCache[portal.id] = cached
                        categories = cached.first
                        rawChannels = cached.second
                        isLoading = false
                        hasError = false
                        return@launch
                    }
                } else {
                    liveSessionMemoryCache.remove(portal.id)
                }

                isLoading = true
                hasError = false

                when (portal.protocol) {
                    "XTREAM" -> coroutineScope {
                        val catsDef = async { XtreamApiClient.getCategories(portal, "get_live_categories") }
                        val chsDef = async { XtreamApiClient.getLiveStreams(portal) }
                        val cats = catsDef.await()
                        val chs = chsDef.await()
                        categories = cats
                        rawChannels = chs
                        hasError = chs.isEmpty()
                        if (chs.isNotEmpty()) {
                            liveSessionMemoryCache[portal.id] = (cats to chs)
                            PortalCacheStore.saveLiveCache(context, portal.id, cats, chs)
                        }
                    }
                    "STALKER" -> coroutineScope {
                        val catsDef = async { StalkerApiClient.getItvGenres(portal) }
                        val chsDef = async { StalkerApiClient.getAllChannels(portal) }
                        val cats = catsDef.await()
                        val chs = chsDef.await()
                        categories = cats
                        rawChannels = chs
                        hasError = chs.isEmpty()
                        if (chs.isNotEmpty()) {
                            liveSessionMemoryCache[portal.id] = (cats to chs)
                            PortalCacheStore.saveLiveCache(context, portal.id, cats, chs)
                        }
                    }
                    "M3U" -> {
                        val (cats, chs) = M3uStreamParser.parseFromUrl(portal.url, portal.id, context)
                        categories = cats
                        rawChannels = chs
                        hasError = chs.isEmpty()
                        if (chs.isNotEmpty()) {
                            liveSessionMemoryCache[portal.id] = (cats to chs)
                            PortalCacheStore.saveLiveCache(context, portal.id, cats, chs)
                        }
                    }
                }
            } catch (t: Throwable) {
                hasError = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(portal.id) {
        loadLiveContent(forceRefresh = false)
    }

    LaunchedEffect(rawChannels.isNotEmpty(), settings.startupChannelEnabled) {
        if (!hasHandledStartupAutoPlay &&
            settings.startupChannelEnabled &&
            settings.startupChannelPortalId == portal.id &&
            settings.startupChannelId.isNotBlank() &&
            rawChannels.isNotEmpty()
        ) {
            hasHandledStartupAutoPlay = true
            val targetChannel = rawChannels.find { it.id == settings.startupChannelId }
            if (targetChannel != null) {
                val chIdx = rawChannels.indexOf(targetChannel)
                onPlayChannel(
                    PlaybackRequest(
                        portal = portal,
                        contentType = "LIVE",
                        itemId = targetChannel.id,
                        title = targetChannel.name,
                        subtitleQueryHint = targetChannel.epgNowTitle,
                        posterOrLogo = targetChannel.logoUrl,
                        streamCmdOrUrl = targetChannel.streamCmdOrUrl,
                        channelList = rawChannels,
                        currentChannelIndex = chIdx.coerceAtLeast(0)
                    )
                )
            }
        }
    }

    val fullCategories = remember(categories, isAr, hiddenIdSet) {
        buildList {
            add(CategoryItem("ALL", if (isAr) "الكل" else "All Channels"))
            add(CategoryItem("FAV", if (isAr) "★ المفضلة" else "★ Favorites"))
            add(CategoryItem("HISTORY", if (isAr) "سجل المشاهدة" else "Watch History"))
            addAll(categories.filter { it.id !in hiddenIdSet })
        }
    }

    val filteredChannels = remember(rawChannels, selectedCategoryId, debouncedQuery, favIdSet, overrideMap, hiddenIdSet, liveWatchHistory) {
        val baseList = when (selectedCategoryId) {
            "HISTORY" -> {
                val histOrder = liveWatchHistory.mapIndexed { idx, h -> h.itemId to idx }.toMap()
                rawChannels.filter { it.id in histOrder }.sortedBy { histOrder[it.id] ?: 9999 }
            }
            "ALL" -> rawChannels
            "FAV" -> rawChannels.filter { it.id in favIdSet }
            else -> rawChannels.filter { it.categoryId.trim() == selectedCategoryId.trim() }
        }

        baseList.asSequence()
            .filter { ch -> ch.categoryId !in hiddenIdSet }
            .mapNotNull { ch ->
                val ov = overrideMap[ch.id]
                if (ov?.isHidden == true) return@mapNotNull null
                val finalName = ov?.customName?.takeIf { it.isNotBlank() } ?: ch.name
                ch.copy(name = finalName)
            }
            .filter { ch ->
                when (selectedCategoryId) {
                    "ALL" -> true
                    "FAV", "HISTORY" -> true
                    else -> ch.categoryId == selectedCategoryId
                }
            }
            .filter { ch ->
                debouncedQuery.isEmpty() || ch.name.contains(debouncedQuery, ignoreCase = true) ||
                        ch.number.toString() == debouncedQuery
            }
            .toList()
    }

    val channelListState = rememberLazyListState()
    val catListState = rememberLazyListState()
    val channelsEntryRequester = remember { FocusRequester() }
    val catRequesters = remember(portal.id) { HashMap<Int, FocusRequester>() }
    val channelRequesters = remember(portal.id) { HashMap<Int, FocusRequester>() }
    val manageCatRequester = remember { FocusRequester() }
    val liveTopBarRequesters = remember { List(3) { FocusRequester() } }
    var focusedCatId by remember { mutableStateOf<String?>(null) }
    var hasRestoredInitialFocus by remember(portal.id) { mutableStateOf(false) }

    LaunchedEffect(focusedCatId) {
        val targetId = focusedCatId ?: return@LaunchedEffect
        delay(220)
        val isLocked = targetId in lockedIdSet && targetId !in unlockedSessionGroups
        if (!isLocked && selectedCategoryId != targetId) {
            selectedCategoryId = targetId
            onCategorySelected(targetId)
        }
    }

    LaunchedEffect(selectedCategoryId) {
        runCatching { channelListState.scrollToItem(0) }
    }

    val targetChannelIndex = remember(filteredChannels, lastFocusedChannelId) {
        if (lastFocusedChannelId != null) {
            val found = filteredChannels.indexOfFirst { it.id == lastFocusedChannelId }
            if (found >= 0) found else 0
        } else 0
    }

    fun focusChannelItem(targetIdx: Int = targetChannelIndex) {
        if (filteredChannels.isEmpty()) return
        val safeIdx = targetIdx.coerceIn(0, filteredChannels.size - 1)
        val req = channelRequesters[safeIdx]
        if (req != null && runCatching { req.requestFocus() }.isSuccess) {
            return
        }
        scope.launch {
            runCatching { channelListState.scrollToItem(safeIdx) }
            delay(40)
            val r = channelRequesters[safeIdx] ?: channelsEntryRequester
            if (runCatching { r.requestFocus() }.isSuccess) return@launch
            delay(40)
            runCatching { (channelRequesters[0] ?: channelsEntryRequester).requestFocus() }
        }
    }

    LaunchedEffect(portal.id, isLoading) {
        if (!isLoading && !hasRestoredInitialFocus && !anyPopupOpen) {
            hasRestoredInitialFocus = true
            if (filteredChannels.isNotEmpty() && lastFocusedChannelId != null) {
                runCatching { channelListState.scrollToItem(targetChannelIndex) }
                delay(100)
                val req = channelRequesters[targetChannelIndex] ?: channelsEntryRequester
                if (!runCatching { req.requestFocus() }.isSuccess) {
                    delay(50)
                    runCatching { (channelRequesters[0] ?: channelsEntryRequester).requestFocus() }
                }
            } else {
                delay(80)
                val currentCatIdx = fullCategories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
                val catReq = catRequesters[currentCatIdx] ?: initialContentFocusRequester
                runCatching { catReq.requestFocus() }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(anyPopupOpen)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // عمود المجموعات: يستقبل التركيز مباشرة من السطر العلوي (DOWN) وينقل إلى القنوات بسلاسة (LEFT/RIGHT)
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            val moveToChannels = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
                            val blockExit = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
                            if (moveToChannels) {
                                if (filteredChannels.isNotEmpty()) {
                                    focusChannelItem()
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (blockExit) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isAr) "المجموعات" else "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    ev.key == Key.DirectionUp -> onNavigateToTopBar()
                                    ev.key == Key.DirectionDown -> runCatching { (catRequesters[0] ?: initialContentFocusRequester).requestFocus() }.isSuccess
                                    (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight) -> runCatching { liveTopBarRequesters[0].requestFocus() }.isSuccess
                                    else -> false
                                }
                            }
                            .tvFocusItem(
                                focusRequester = manageCatRequester,
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { showCategoryManager = true }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isAr) "إدارة" else "Manage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    state = catListState,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize().focusGroup()
                ) {
                    itemsIndexed(
                        items = fullCategories,
                        key = { idx, cat -> "live_cat_${portal.id}_${cat.id}_$idx" }
                    ) { index, cat ->
                        val isSelected = cat.id == selectedCategoryId
                        val isLocked = cat.id in lockedIdSet && cat.id !in unlockedSessionGroups
                        val canLock = cat.id != "ALL" && cat.id != "FAV"
                        val itemReq = if (index == 0) initialContentFocusRequester else catRequesters.getOrPut(index) { FocusRequester() }
                        if (index == 0) catRequesters[0] = initialContentFocusRequester

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) focusedCatId = cat.id
                                }
                                .tvVerticalStepItem(
                                    index = index,
                                    totalCount = fullCategories.size,
                                    listState = catListState,
                                    requesters = catRequesters,
                                    scope = scope,
                                    onExitTop = { runCatching { manageCatRequester.requestFocus() }.isSuccess },
                                    onExitBottom = { true }
                                )
                                .tvFocusItem(
                                    focusRequester = itemReq,
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    },
                                    onLongClick = if (canLock) {
                                        {
                                            pinErrorText = null
                                            pendingPinActionLockToggle = cat
                                        }
                                    } else null,
                                    onMenuClick = if (canLock) {
                                        {
                                            pinErrorText = null
                                            pendingPinActionLockToggle = cat
                                        }
                                    } else null,
                                    onClick = {
                                        if (isLocked) {
                                            pinErrorText = null
                                            pendingPinCategory = cat
                                        } else {
                                            selectedCategoryId = cat.id
                                            onCategorySelected(cat.id)
                                            if (filteredChannels.isNotEmpty()) {
                                                focusChannelItem(0)
                                            }
                                        }
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (cat.id in lockedIdSet) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isLocked) extraColors.badgeWarning else extraColors.badgeSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // عمود القنوات: يعيد المؤشر بسلاسة إلى المجموعات عند الضغط باتجاهها (RIGHT في العربية / LEFT في الإنجليزية)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            val backToCategories = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
                            val blockExit = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
                            if (backToCategories) {
                                val currentCatIdx = fullCategories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
                                val catReq = catRequesters[currentCatIdx] ?: (if (currentCatIdx == 0) initialContentFocusRequester else null)
                                if (catReq != null && runCatching { catReq.requestFocus() }.isSuccess) {
                                    return@onPreviewKeyEvent true
                                }
                                scope.launch {
                                    runCatching { catListState.scrollToItem(currentCatIdx) }
                                    delay(40)
                                    runCatching { (catRequesters[currentCatIdx] ?: initialContentFocusRequester).requestFocus() }
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (blockExit) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvSafeSearchBar(
                        query = searchQuery,
                        placeholder = if (isAr) "ابحث باسم القناة أو رقمها..." else "Search channel name or number...",
                        onQueryChange = { searchQuery = it },
                        focusRequester = liveTopBarRequesters[0],
                        isAr = isAr,
                        searchHistory = settings.searchHistory,
                        onSaveSearchQuery = { q -> scope.launch { settingsStore?.addSearchHistoryQuery(q) } },
                        onClearSearchHistory = { scope.launch { settingsStore?.clearSearchHistory() } },
                        modifier = Modifier
                            .weight(1f)
                            .tvHorizontalStepItem(
                                index = 0,
                                totalCount = 3,
                                requesters = liveTopBarRequesters,
                                isAr = isAr,
                                onExitStart = { runCatching { (catRequesters[0] ?: initialContentFocusRequester).requestFocus() }.isSuccess },
                                onUp = { onNavigateToTopBar() },
                                onDown = {
                                    if (filteredChannels.isNotEmpty()) {
                                        runCatching { (channelRequesters[targetChannelIndex] ?: channelRequesters[0] ?: channelsEntryRequester).requestFocus() }.isSuccess
                                    } else false
                                }
                            )
                    )
                    var isLiveHistoryFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isLiveHistoryFocused = it.isFocused }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && (
                                    ev.key == Key.DirectionCenter || ev.key == Key.Enter ||
                                    ev.key == Key.NumPadEnter || ev.key == Key.ButtonA
                                )) {
                                    showLiveWatchHistoryDialog = true
                                    true
                                } else false
                            }
                            .tvHorizontalStepItem(
                                index = 1,
                                totalCount = 3,
                                requesters = liveTopBarRequesters,
                                isAr = isAr,
                                onUp = { onNavigateToTopBar() },
                                onDown = {
                                    if (filteredChannels.isNotEmpty()) {
                                        runCatching { (channelRequesters[targetChannelIndex] ?: channelRequesters[0] ?: channelsEntryRequester).requestFocus() }.isSuccess
                                    } else false
                                }
                            )
                            .tvFocusItem(
                                focusRequester = liveTopBarRequesters[1],
                                shape = RoundedCornerShape(10.dp),
                                focusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = Color.White,
                                onClick = { showLiveWatchHistoryDialog = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isLiveHistoryFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAr) "سجل المشاهدة (${liveWatchHistory.size})" else "History (${liveWatchHistory.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLiveHistoryFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .tvHorizontalStepItem(
                                index = 2,
                                totalCount = 3,
                                requesters = liveTopBarRequesters,
                                isAr = isAr,
                                onUp = { onNavigateToTopBar() },
                                onDown = {
                                    if (filteredChannels.isNotEmpty()) {
                                        runCatching { (channelRequesters[targetChannelIndex] ?: channelRequesters[0] ?: channelsEntryRequester).requestFocus() }.isSuccess
                                    } else false
                                }
                            )
                            .tvFocusItem(
                                focusRequester = liveTopBarRequesters[2],
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { loadLiveContent(forceRefresh = true) }
                            )
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    LtrText(
                        text = "${filteredChannels.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    hasError -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Row(
                                modifier = Modifier
                                    .tvFocusItem(
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                        onClick = { loadLiveContent(forceRefresh = true) }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAr) "إعادة المحاولة" else "Retry",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = channelListState,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = filteredChannels,
                                key = { idx, ch -> "${ch.uniqueKey}_$idx" }
                            ) { index, channel ->
                                val isFav = channel.id in favIdSet
                                val chReq = channelRequesters.getOrPut(index) {
                                    if (index == 0) channelsEntryRequester else FocusRequester()
                                }
                                if (index == 0 && !channelRequesters.containsKey(0)) {
                                    channelRequesters[0] = channelsEntryRequester
                                }

                                ChannelRowCard(
                                    channel = channel,
                                    isFavorite = isFav,
                                    modifier = Modifier.tvVerticalStepItem(
                                        index = index,
                                        totalCount = filteredChannels.size,
                                        listState = channelListState,
                                        requesters = channelRequesters,
                                        scope = scope,
                                        onExitTop = { runCatching { liveTopBarRequesters[0].requestFocus() }.isSuccess }
                                    ),
                                    focusRequester = chReq,
                                    onFocused = { onChannelFocused(channel.id) },
                                    onClick = {
                                        onChannelFocused(channel.id)
                                        onPlayChannel(
                                            PlaybackRequest(
                                                portal = portal,
                                                contentType = "LIVE",
                                                itemId = channel.id,
                                                title = channel.name,
                                                subtitleQueryHint = channel.epgNowTitle,
                                                posterOrLogo = channel.logoUrl,
                                                streamCmdOrUrl = channel.streamCmdOrUrl,
                                                channelList = filteredChannels,
                                                currentChannelIndex = index
                                            )
                                        )
                                    },
                                    onOpenOptions = { channelForMenu = channel }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCategoryManager) {
            CategoryRulesManagerDialog(
                portalName = portal.name,
                sectionTitle = if (isAr) "القنوات" else "Live TV",
                categories = categories,
                rulesMap = rulesMap,
                isAr = isAr,
                onToggleHidden = { cat, hide ->
                    scope.launch {
                        val existing = rulesMap[cat.id]
                        dao.upsertCategoryRule(
                            CategoryRuleEntity(
                                id = existing?.id ?: 0L,
                                portalId = portal.id,
                                contentType = "LIVE",
                                categoryId = cat.id,
                                categoryName = cat.name,
                                isHidden = hide,
                                isLocked = existing?.isLocked ?: (cat.id in lockedIdSet)
                            )
                        )
                        if (hide && selectedCategoryId == cat.id) selectedCategoryId = "ALL"
                    }
                },
                onToggleLocked = { cat, lock ->
                    scope.launch {
                        val existing = rulesMap[cat.id]
                        dao.upsertCategoryRule(
                            CategoryRuleEntity(
                                id = existing?.id ?: 0L,
                                portalId = portal.id,
                                contentType = "LIVE",
                                categoryId = cat.id,
                                categoryName = cat.name,
                                isHidden = existing?.isHidden ?: false,
                                isLocked = lock
                            )
                        )
                        if (!lock) dao.unlockGroup(portal.id, cat.id)
                    }
                },
                onSetAllAdultHidden = { adultCats, hide ->
                    scope.launch {
                        val updates = adultCats.map { cat ->
                            val existing = rulesMap[cat.id]
                            CategoryRuleEntity(
                                id = existing?.id ?: 0L,
                                portalId = portal.id,
                                contentType = "LIVE",
                                categoryId = cat.id,
                                categoryName = cat.name,
                                isHidden = hide,
                                isLocked = existing?.isLocked ?: (cat.id in lockedIdSet)
                            )
                        }
                        dao.upsertCategoryRules(updates)
                        if (hide && adultCats.any { it.id == selectedCategoryId }) selectedCategoryId = "ALL"
                    }
                },
                onDismiss = { showCategoryManager = false }
            )
        }

        pendingPinCategory?.let { cat ->
            TvPinDialog(
                title = if (isAr) "مجموعة مقفلة: ${cat.name}" else "Locked Group: ${cat.name}",
                isAr = isAr,
                errorMessage = pinErrorText,
                onPinSubmitted = { entered ->
                    if (entered == parentalPin) {
                        unlockedSessionGroups = unlockedSessionGroups + cat.id
                        selectedCategoryId = cat.id
                        pendingPinCategory = null
                        pinErrorText = null
                    } else {
                        pinErrorText = if (isAr) "رمز PIN غير صحيح" else "Incorrect PIN"
                    }
                },
                onDismiss = { pendingPinCategory = null }
            )
        }

        pendingPinActionLockToggle?.let { cat ->
            val currentlyLocked = cat.id in lockedIdSet
            TvPinDialog(
                title = if (currentlyLocked) {
                    if (isAr) "إلغاء قفل: ${cat.name}" else "Unlock: ${cat.name}"
                } else {
                    if (isAr) "قفل المجموعة: ${cat.name}" else "Lock Group: ${cat.name}"
                },
                isAr = isAr,
                errorMessage = pinErrorText,
                onPinSubmitted = { entered ->
                    if (entered == parentalPin) {
                        scope.launch {
                            if (currentlyLocked) {
                                dao.unlockGroup(portal.id, cat.id)
                            } else {
                                dao.lockGroup(LockedGroupEntity(portalId = portal.id, categoryId = cat.id, categoryName = cat.name))
                                unlockedSessionGroups = unlockedSessionGroups - cat.id
                                if (selectedCategoryId == cat.id) selectedCategoryId = "ALL"
                            }
                        }
                        pendingPinActionLockToggle = null
                        pinErrorText = null
                    } else {
                        pinErrorText = if (isAr) "رمز PIN غير صحيح" else "Incorrect PIN"
                    }
                },
                onDismiss = { pendingPinActionLockToggle = null }
            )
        }

        channelForMenu?.let { ch ->
            val isFav = ch.id in favIdSet
            val isStartup = settings.startupChannelEnabled &&
                    settings.startupChannelPortalId == portal.id &&
                    settings.startupChannelId == ch.id
            val options = listOf(
                TvMenuOption(
                    id = "startup",
                    label = if (isStartup) {
                        if (isAr) "إلغاء القناة من بدأ التشغيل" else "Disable Channel Startup"
                    } else {
                        if (isAr) "اجعل القناة في بدأ التشغيل" else "Play Channel on Startup"
                    },
                    icon = if (isStartup) Icons.Default.CheckCircle else Icons.Default.PlayArrow
                ),
                TvMenuOption(
                    id = "fav",
                    label = if (isFav) {
                        if (isAr) "إزالة من المفضلة" else "Remove from Favorites"
                    } else {
                        if (isAr) "إضافة للمفضلة ★" else "Add to Favorites ★"
                    },
                    icon = if (isFav) Icons.Default.StarOutline else Icons.Default.Star
                ),
                TvMenuOption(
                    id = "epg",
                    label = if (isAr) "دليل البرامج (EPG)" else "Program Guide (EPG)",
                    icon = Icons.Default.EventNote
                ),
                TvMenuOption(
                    id = "rename",
                    label = if (isAr) "إعادة تسمية القناة" else "Rename Channel",
                    icon = Icons.Default.Edit
                ),
                TvMenuOption(
                    id = "hide",
                    label = if (isAr) "إخفاء القناة" else "Hide Channel",
                    icon = Icons.Default.VisibilityOff
                ),
                TvMenuOption(
                    id = "copy",
                    label = if (isAr) "نسخ رابط البث" else "Copy Stream URL",
                    icon = Icons.Default.ContentCopy
                ),
                TvMenuOption(
                    id = "external",
                    label = if (isAr) "فتح في مشغل خارجي" else "Open in External Player",
                    icon = Icons.Default.OpenInNew
                )
            )
            TvActionMenuDialog(
                title = ch.name,
                options = options,
                restoreFocusRequester = channelRequesters[targetChannelIndex] ?: channelsEntryRequester,
                onSelect = { opt ->
                    when (opt.id) {
                        "startup" -> scope.launch {
                            if (isStartup) {
                                settingsStore?.setStartupChannel(0L, "", false)
                                Toast.makeText(
                                    context,
                                    if (isAr) "تم إلغاء التشغيل التلقائي عند البدء" else "Startup playback disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                settingsStore?.setStartupChannel(portal.id, ch.id, true)
                                Toast.makeText(
                                    context,
                                    if (isAr) "تم تعيين \"${ch.name}\" لتبدأ تلقائياً عند التشغيل" else "\"${ch.name}\" set to play on startup",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        "fav" -> scope.launch {
                            if (isFav) {
                                dao.removeFavorite(portal.id, "LIVE", ch.id)
                            } else {
                                dao.addFavorite(
                                    FavoriteEntity(
                                        portalId = portal.id,
                                        contentType = "LIVE",
                                        itemId = ch.id,
                                        title = ch.name,
                                        logoOrPoster = ch.logoUrl,
                                        streamCmdOrUrl = ch.streamCmdOrUrl,
                                        categoryId = ch.categoryId
                                    )
                                )
                            }
                        }
                        "epg" -> {
                            channelForEpg = ch
                            isLoadingEpg = true
                            scope.launch {
                                epgPrograms = when (portal.protocol) {
                                    "XTREAM" -> XtreamApiClient.getShortEpg(portal, ch.id)
                                    "STALKER" -> StalkerApiClient.getShortEpg(portal, ch.id)
                                    else -> emptyList()
                                }
                                isLoadingEpg = false
                            }
                        }
                        "rename" -> channelForRename = ch
                        "hide" -> scope.launch {
                            val existing = overrideMap[ch.id]
                            dao.upsertChannelOverride(
                                ChannelOverrideEntity(
                                    id = existing?.id ?: 0L,
                                    portalId = portal.id,
                                    channelId = ch.id,
                                    customName = existing?.customName ?: ch.name,
                                    isHidden = true
                                )
                            )
                        }
                        "copy" -> scope.launch {
                            val resolved = resolvePlayableUrl(portal, ch.streamCmdOrUrl, "itv")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Stream URL", resolved))
                            Toast.makeText(context, if (isAr) "تم نسخ الرابط" else "URL Copied", Toast.LENGTH_SHORT).show()
                        }
                        "external" -> scope.launch {
                            val resolved = resolvePlayableUrl(portal, ch.streamCmdOrUrl, "itv")
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(resolved), "video/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    }
                },
                onDismiss = { channelForMenu = null }
            )
        }

        channelForRename?.let { ch ->
            TvTextInputDialog(
                title = if (isAr) "إعادة تسمية القناة" else "Rename Channel",
                initialValue = ch.name,
                confirmLabel = if (isAr) "حفظ" else "Save",
                onConfirm = { newName ->
                    scope.launch {
                        val existing = overrideMap[ch.id]
                        dao.upsertChannelOverride(
                            ChannelOverrideEntity(
                                id = existing?.id ?: 0L,
                                portalId = portal.id,
                                channelId = ch.id,
                                customName = newName,
                                isHidden = existing?.isHidden ?: false
                            )
                        )
                    }
                },
                onDismiss = { channelForRename = null }
            )
        }

        channelForEpg?.let { ch ->
            TvModalDialog(
                title = "EPG - ${ch.name}",
                onDismiss = { channelForEpg = null },
                maxWidth = 540.dp
            ) { _ ->
                if (isLoadingEpg) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (epgPrograms.isEmpty()) {
                    Text(
                        text = if (isAr) "لا تتوفر بيانات دليل برامج لهذه القناة" else "No EPG data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    ) {
                        itemsIndexed(
                            items = epgPrograms,
                            key = { idx, prog -> "epg_${ch.id}_${prog.startTimeMs}_$idx" }
                        ) { _, prog ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (prog.startTimeMs > 0L) {
                                        LtrText(
                                            text = "${LocaleUtils.formatClock(prog.startTimeMs)} - ${LocaleUtils.formatClock(prog.endTimeMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(
                                        text = prog.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (prog.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = prog.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showLiveWatchHistoryDialog) {
            val histListState = rememberLazyListState()
            val histRequesters = remember(liveWatchHistory.size) { HashMap<Int, FocusRequester>() }
            val clearHistoryBtnReq = remember { FocusRequester() }
            val closeHistBtnReq = remember { FocusRequester() }
            TvModalDialog(
                title = if (isAr) "سجل المشاهدة (${liveWatchHistory.size})" else "Watch History (${liveWatchHistory.size})",
                onDismiss = { showLiveWatchHistoryDialog = false },
                maxWidth = 640.dp,
                restoreFocusRequester = liveTopBarRequesters[1],
                closeBtnRequester = closeHistBtnReq,
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (liveWatchHistory.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                            val lastIdx = (liveWatchHistory.size - 1).coerceAtLeast(0)
                                            runCatching { histRequesters[lastIdx]?.requestFocus() }.isSuccess
                                        } else false
                                    }
                                    .tvFocusItem(
                                        focusRequester = clearHistoryBtnReq,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            scope.launch {
                                                liveWatchHistory.forEach { h ->
                                                    dao.removeWatchHistory(portal.id, h.itemId)
                                                }
                                            }
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = if (isAr) "مسح سجل البث المباشر" else "Clear Live History",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        Box(
                            modifier = Modifier
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                        if (liveWatchHistory.isNotEmpty()) {
                                            runCatching { histRequesters[(liveWatchHistory.size - 1).coerceAtLeast(0)]?.requestFocus() }.isSuccess
                                        } else {
                                            runCatching { closeHistBtnReq.requestFocus() }.isSuccess
                                        }
                                    } else false
                                }
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { showLiveWatchHistoryDialog = false }
                                )
                                .padding(horizontal = 20.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = if (isAr) "إغلاق" else "Close",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            ) { firstReq ->
                if (liveWatchHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isAr) "سجل مشاهدة القنوات فارغ حالياً" else "Live TV watch history is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    histRequesters[0] = firstReq
                    LazyColumn(
                        state = histListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    ) {
                        itemsIndexed(
                            items = liveWatchHistory,
                            key = { idx, entry -> "live_hist_dlg_${entry.id}_${entry.itemId}_$idx" }
                        ) { hIdx, entry ->
                            val hReq = if (hIdx == 0) firstReq else histRequesters.getOrPut(hIdx) { FocusRequester() }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvVerticalStepItem(
                                        index = hIdx,
                                        totalCount = liveWatchHistory.size,
                                        listState = histListState,
                                        requesters = histRequesters,
                                        scope = scope,
                                        onExitTop = { runCatching { closeHistBtnReq.requestFocus() }.isSuccess },
                                        onExitBottom = { runCatching { clearHistoryBtnReq.requestFocus() }.isSuccess }
                                    )
                                    .tvFocusItem(
                                        focusRequester = hReq,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        onClick = {
                                            showLiveWatchHistoryDialog = false
                                            val chIdx = rawChannels.indexOfFirst { it.id == entry.itemId }.coerceAtLeast(0)
                                            onPlayChannel(
                                                PlaybackRequest(
                                                    portal = portal,
                                                    contentType = "LIVE",
                                                    itemId = entry.itemId,
                                                    title = entry.title,
                                                    subtitleQueryHint = entry.title,
                                                    posterOrLogo = entry.posterUrl,
                                                    streamCmdOrUrl = entry.streamCmdOrUrl,
                                                    channelList = rawChannels,
                                                    currentChannelIndex = chIdx
                                                )
                                            )
                                        }
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRowCard(
    channel: ChannelItem,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    onOpenOptions: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusItem(
                focusRequester = focusRequester,
                shape = RoundedCornerShape(10.dp),
                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                onLongClick = onOpenOptions,
                onMenuClick = onOpenOptions,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LtrText(
            text = LocaleUtils.formatInt(channel.number),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp),
            maxLines = 1
        )

        // حاوية ثابتة الأبعاد للأيقونة أو الشعار لمنع اهتزاز أو قفز العناصر داخل القائمة
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logoUrl)
                        .size(96, 96)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFavorite) {
                    Text(text = "★ ", color = Color(0xFFFFD600), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (channel.epgNowTitle.isNotBlank()) {
                Text(
                    text = channel.epgNowTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

suspend fun resolvePlayableUrl(portal: PortalEntity, cmdOrUrl: String, type: String): String {
    return if (portal.protocol == "STALKER") {
        StalkerApiClient.createLink(portal, cmdOrUrl, type)
    } else {
        cmdOrUrl
    }
}

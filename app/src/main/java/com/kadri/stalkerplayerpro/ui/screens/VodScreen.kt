package com.kadri.stalkerplayerpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvSafeSearchBar
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvHorizontalStepItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.CategoryRuleEntity
import com.kadri.stalkerplayerpro.data.local.PortalCacheStore
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.local.WatchHistoryEntity
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.EpisodeItem
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.data.model.VodItem
import com.kadri.stalkerplayerpro.data.network.StalkerApiClient
import com.kadri.stalkerplayerpro.data.network.XtreamApiClient
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.components.TvPinDialog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VodScreen(
    portal: PortalEntity?,
    dao: AppDao,
    settings: TvUserSettings = TvUserSettings(),
    settingsStore: SettingsDataStore? = null,
    isSeriesMode: Boolean,
    parentalPin: String = "0000",
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateToTopBar: () -> Boolean = { false },
    lastFocusedItemId: String? = null,
    onItemFocused: (String) -> Unit = {},
    onGoToPortals: () -> Unit = {},
    onPlayVod: (PlaybackRequest) -> Unit
) {
    if (portal == null) {
        VodEmptyPortalView(
            isAr = isAr,
            initialContentFocusRequester = initialContentFocusRequester,
            onGoToPortals = onGoToPortals
        )
    } else {
        VodContent(
            portal = portal,
            dao = dao,
            settings = settings,
            settingsStore = settingsStore,
            isSeriesMode = isSeriesMode,
            parentalPin = parentalPin,
            isAr = isAr,
            initialContentFocusRequester = initialContentFocusRequester,
            onNavigateToTopBar = onNavigateToTopBar,
            lastFocusedItemId = lastFocusedItemId,
            onItemFocused = onItemFocused,
            onPlayVod = onPlayVod
        )
    }
}

@Composable
private fun VodEmptyPortalView(
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester,
    onGoToPortals: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isAr) "يرجى تفعيل حساب من قسم «البوابات» أولاً" else "Please activate a portal first",
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
private fun VodContent(
    portal: PortalEntity,
    dao: AppDao,
    settings: TvUserSettings = TvUserSettings(),
    settingsStore: SettingsDataStore? = null,
    isSeriesMode: Boolean,
    parentalPin: String = "0000",
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateToTopBar: () -> Boolean = { false },
    lastFocusedItemId: String? = null,
    onItemFocused: (String) -> Unit = {},
    onPlayVod: (PlaybackRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember(portal.id, isSeriesMode) { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var allLoadedItems by remember(portal.id, isSeriesMode) { mutableStateOf<List<VodItem>>(emptyList()) }
    var visibleCount by remember(portal.id, isSeriesMode) { mutableIntStateOf(30) }
    var currentStalkerPage by remember(portal.id, isSeriesMode) { mutableIntStateOf(1) }
    var selectedCategoryId by remember(portal.id, isSeriesMode) { mutableStateOf("ALL") }

    var isLoading by remember(portal.id, isSeriesMode) { mutableStateOf(true) }
    var hasError by remember(portal.id, isSeriesMode) { mutableStateOf(false) }

    var searchQuery by remember(portal.id, isSeriesMode) { mutableStateOf("") }
    var debouncedQuery by remember(portal.id, isSeriesMode) { mutableStateOf("") }

    var selectedSeriesForEpisodes by remember { mutableStateOf<VodItem?>(null) }
    var seasonsMap: Map<Int, List<EpisodeItem>> by remember { mutableStateOf(emptyMap()) }
    var selectedSeasonNum by remember { mutableIntStateOf(1) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    val watchHistory by dao.observeWatchHistory(portal.id).collectAsState(initial = emptyList())
    val vodRuleType = if (isSeriesMode) "SERIES" else "MOVIE"
    val categoryRules by dao.observeCategoryRules(portal.id, vodRuleType).collectAsState(initial = emptyList())
    val rulesMap = remember(categoryRules) { categoryRules.associateBy { it.categoryId } }
    val hiddenIdSet = remember(categoryRules) { categoryRules.filter { it.isHidden }.map { it.categoryId }.toSet() }
    val lockedIdSet = remember(categoryRules) { categoryRules.filter { it.isLocked }.map { it.categoryId }.toSet() }
    var unlockedSessionGroups by remember(portal.id, isSeriesMode) { mutableStateOf<Set<String>>(emptySet()) }
    var pendingPinCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    var showCategoryManager by remember(portal.id, isSeriesMode) { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        delay(350)
        debouncedQuery = searchQuery.trim()
        visibleCount = 30
    }

    fun fetchInitialData(catId: String = selectedCategoryId, forceRefresh: Boolean = false) {
        scope.launch {
            try {
                visibleCount = 30
                currentStalkerPage = 1

                if (catId == "HISTORY") {
                    isLoading = false
                    hasError = false
                    return@launch
                }

                if (!forceRefresh) {
                    val cached = PortalCacheStore.loadVodCache(context, portal.id, isSeriesMode, catId)
                    if (cached != null && cached.second.isNotEmpty()) {
                        if (cached.first.isNotEmpty()) categories = cached.first
                        allLoadedItems = cached.second
                        isLoading = false
                        hasError = false
                        return@launch
                    }
                }

                isLoading = true
                hasError = false

                when (portal.protocol) {
                    "XTREAM" -> coroutineScope {
                        val catAction = if (isSeriesMode) "get_series_categories" else "get_vod_categories"
                        val catsDef = if (categories.isEmpty() || forceRefresh) {
                            async { XtreamApiClient.getCategories(portal, catAction) }
                        } else null
                        val itemsDef = async {
                            if (isSeriesMode) XtreamApiClient.getSeriesList(portal, catId)
                            else XtreamApiClient.getVodStreams(portal, catId)
                        }
                        if (catsDef != null) categories = catsDef.await()
                        val items = itemsDef.await()
                        allLoadedItems = items
                        hasError = items.isEmpty()
                        if (items.isNotEmpty()) {
                            PortalCacheStore.saveVodCache(context, portal.id, isSeriesMode, catId, categories, items)
                        }
                    }
                    "STALKER" -> coroutineScope {
                        val stkType = if (isSeriesMode) "series" else "vod"
                        val catsDef = if (categories.isEmpty() || forceRefresh) {
                            async { StalkerApiClient.getVodCategories(portal, stkType) }
                        } else null
                        val stkCat = if (catId == "ALL") "*" else catId
                        val itemsDef = async { StalkerApiClient.getVodOrderedList(portal, stkType, stkCat, page = 1) }
                        if (catsDef != null) categories = catsDef.await()
                        val items = itemsDef.await()
                        allLoadedItems = items
                        hasError = items.isEmpty()
                        if (items.isNotEmpty()) {
                            PortalCacheStore.saveVodCache(context, portal.id, isSeriesMode, catId, categories, items)
                        }
                    }
                    else -> {
                        allLoadedItems = emptyList()
                        hasError = false
                    }
                }
            } catch (t: Throwable) {
                hasError = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(portal.id, isSeriesMode, selectedCategoryId) {
        fetchInitialData(selectedCategoryId, forceRefresh = false)
    }

    val fullCategories = remember(categories, isAr) {
        buildList {
            add(CategoryItem("ALL", if (isAr) "الكل" else "All"))
            addAll(categories.filter { it.id !in hiddenIdSet })
        }
    }

    val filteredItems = remember(allLoadedItems, debouncedQuery) {
        if (debouncedQuery.isEmpty()) {
            allLoadedItems
        } else {
            allLoadedItems.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
        }
    }

    val pagedItems = remember(filteredItems, visibleCount) {
        filteredItems.take(visibleCount)
    }

    val gridState = rememberLazyGridState()
    val itemsEntryRequester = remember { FocusRequester() }
    val vodCatRequesters = remember(portal.id, isSeriesMode) { HashMap<Int, FocusRequester>() }
    val vodItemRequesters = remember(portal.id, isSeriesMode, selectedCategoryId) { HashMap<Int, FocusRequester>() }
    val vodManageCatRequester = remember { FocusRequester() }
    val vodTopBarRequesters = remember { List(3) { FocusRequester() } }
    var showWatchHistoryDialog by remember { mutableStateOf(false) }
    var focusedVodCatId by remember { mutableStateOf<String?>(null) }
    var hasRestoredInitialFocus by remember(portal.id, isSeriesMode) { mutableStateOf(false) }

    LaunchedEffect(focusedVodCatId) {
        val targetId = focusedVodCatId ?: return@LaunchedEffect
        delay(220)
        if (selectedCategoryId != targetId) {
            selectedCategoryId = targetId
        }
    }
    val targetItemIndex = remember(pagedItems, lastFocusedItemId) {
        if (lastFocusedItemId != null) {
            val found = pagedItems.indexOfFirst { it.id == lastFocusedItemId }
            if (found >= 0) found else 0
        } else 0
    }

    val moveToGrid: () -> Boolean = {
        if (pagedItems.isNotEmpty()) {
            val targetIdx = targetItemIndex.coerceIn(0, pagedItems.size - 1)
            val req = vodItemRequesters[targetIdx] ?: itemsEntryRequester
            val ok = runCatching { req.requestFocus() }.isSuccess
            if (ok) {
                true
            } else {
                scope.launch {
                    runCatching { gridState.scrollToItem(targetIdx) }
                    delay(30)
                    val retryOk = runCatching { (vodItemRequesters[targetIdx] ?: itemsEntryRequester).requestFocus() }.isSuccess
                    if (!retryOk) {
                        runCatching { itemsEntryRequester.requestFocus() }
                    }
                }
                true
            }
        } else false
    }

    LaunchedEffect(portal.id, isSeriesMode, isLoading) {
        if (!isLoading && !hasRestoredInitialFocus && selectedSeriesForEpisodes == null) {
            hasRestoredInitialFocus = true
            if (pagedItems.isNotEmpty() && lastFocusedItemId != null) {
                runCatching { gridState.scrollToItem(targetItemIndex) }
                delay(80)
                runCatching { itemsEntryRequester.requestFocus() }
            } else {
                delay(80)
                runCatching { initialContentFocusRequester.requestFocus() }
            }
        }
    }

    val reachedEnd by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= pagedItems.size - 6 && pagedItems.isNotEmpty()
        }
    }

    LaunchedEffect(reachedEnd) {
        if (reachedEnd && !isLoading) {
            if (visibleCount < filteredItems.size) {
                visibleCount += 30
            } else if (portal.protocol == "STALKER" && selectedCategoryId != "HISTORY") {
                val nextPage = currentStalkerPage + 1
                val stkType = if (isSeriesMode) "series" else "vod"
                val stkCat = if (selectedCategoryId == "ALL") "*" else selectedCategoryId
                val more = StalkerApiClient.getVodOrderedList(portal, stkType, stkCat, page = nextPage)
                if (more.isNotEmpty()) {
                    currentStalkerPage = nextPage
                    allLoadedItems = allLoadedItems + more
                    visibleCount += 30
                    PortalCacheStore.saveVodCache(context, portal.id, isSeriesMode, selectedCategoryId, categories, allLoadedItems)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(selectedSeriesForEpisodes != null || showCategoryManager || pendingPinCategory != null || showWatchHistoryDialog)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            val moveToG = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
                            val outerEdge = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
                            when {
                                moveToG -> {
                                    if (moveToGrid()) return@onPreviewKeyEvent true
                                }
                                outerEdge -> {
                                    return@onPreviewKeyEvent true
                                }
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
                        text = if (isSeriesMode) {
                            if (isAr) "أقسام المسلسلات" else "Series Categories"
                        } else {
                            if (isAr) "أقسام الأفلام" else "Movie Categories"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    ev.key == Key.DirectionUp -> onNavigateToTopBar()
                                    ev.key == Key.DirectionDown -> runCatching { (vodCatRequesters[0] ?: initialContentFocusRequester).requestFocus() }.isSuccess
                                    (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight) -> runCatching { vodTopBarRequesters[0].requestFocus() }.isSuccess
                                    else -> false
                                }
                            }
                            .tvFocusItem(
                                focusRequester = vodManageCatRequester,
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

                val catListState = rememberLazyListState()
                LazyColumn(
                    state = catListState,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize().focusGroup()
                ) {
                    itemsIndexed(
                        items = fullCategories,
                        key = { idx, cat -> "vod_cat_${portal.id}_${isSeriesMode}_${cat.id}_$idx" }
                    ) { index, cat ->
                        val isSelected = cat.id == selectedCategoryId
                        val itemReq = if (index == 0) initialContentFocusRequester else vodCatRequesters.getOrPut(index) { FocusRequester() }
                        if (index == 0) vodCatRequesters[0] = initialContentFocusRequester

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        focusedVodCatId = cat.id
                                    }
                                }
                                .tvVerticalStepItem(
                                    index = index,
                                    totalCount = fullCategories.size,
                                    listState = catListState,
                                    requesters = vodCatRequesters,
                                    scope = scope,
                                    onExitTop = { runCatching { vodManageCatRequester.requestFocus() }.isSuccess },
                                    onExitBottom = { moveToGrid() }
                                )
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown) {
                                        val moveToG = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
                                        val outerEdge = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
                                        when {
                                            moveToG -> {
                                                if (moveToGrid()) return@onPreviewKeyEvent true
                                            }
                                            outerEdge -> {
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                    }
                                    false
                                }
                                .focusProperties {
                                    if (pagedItems.isNotEmpty()) {
                                        val targetIdx = targetItemIndex.coerceIn(0, pagedItems.size - 1)
                                        val req = vodItemRequesters[targetIdx] ?: itemsEntryRequester
                                        if (isAr) left = req else right = req
                                    }
                                }
                                .tvFocusItem(
                                    focusRequester = itemReq,
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        focusedVodCatId = cat.id
                                        moveToGrid()
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cat.id == "HISTORY") {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            val backToCategories = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
                            val outerEdge = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
                            when {
                                backToCategories -> {
                                    val currentCatIdx = fullCategories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
                                    val catReq = vodCatRequesters[currentCatIdx] ?: vodCatRequesters[0] ?: initialContentFocusRequester
                                    if (runCatching { catReq.requestFocus() }.isSuccess) {
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                outerEdge -> {
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        false
                    }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvSafeSearchBar(
                        query = searchQuery,
                        placeholder = if (isSeriesMode) {
                            if (isAr) "ابحث عن مسلسل..." else "Search series..."
                        } else {
                            if (isAr) "ابحث عن فيلم..." else "Search movies..."
                        },
                        onQueryChange = { searchQuery = it },
                        focusRequester = vodTopBarRequesters[0],
                        isAr = isAr,
                        searchHistory = settings.searchHistory,
                        onSaveSearchQuery = { q -> scope.launch { settingsStore?.addSearchHistoryQuery(q) } },
                        onClearSearchHistory = { scope.launch { settingsStore?.clearSearchHistory() } },
                        modifier = Modifier
                            .weight(1f)
                            .tvHorizontalStepItem(
                                index = 0,
                                totalCount = 3,
                                requesters = vodTopBarRequesters,
                                isAr = isAr,
                                onExitStart = { runCatching { (vodCatRequesters[0] ?: initialContentFocusRequester).requestFocus() }.isSuccess },
                                onUp = { onNavigateToTopBar() },
                                onDown = {
                                    if (pagedItems.isNotEmpty()) runCatching { itemsEntryRequester.requestFocus() }.isSuccess else false
                                }
                            )
                    )
                    Row(
                        modifier = Modifier
                            .tvHorizontalStepItem(
                                index = 1,
                                totalCount = 3,
                                requesters = vodTopBarRequesters,
                                isAr = isAr,
                                onUp = { onNavigateToTopBar() },
                                onDown = {
                                    if (pagedItems.isNotEmpty()) runCatching { itemsEntryRequester.requestFocus() }.isSuccess else false
                                }
                            )
                            .tvFocusItem(
                                focusRequester = vodTopBarRequesters[1],
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { showWatchHistoryDialog = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAr) "سجل المشاهدة (${watchHistory.size})" else "History (${watchHistory.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .tvHorizontalStepItem(
                                index = 2,
                                totalCount = 3,
                                requesters = vodTopBarRequesters,
                                isAr = isAr,
                                onUp = { onNavigateToTopBar() },
                                onDown = {
                                    if (pagedItems.isNotEmpty()) runCatching { itemsEntryRequester.requestFocus() }.isSuccess else false
                                }
                            )
                            .tvFocusItem(
                                focusRequester = vodTopBarRequesters[2],
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { fetchInitialData(selectedCategoryId, forceRefresh = true) }
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
                }

                Spacer(modifier = Modifier.height(8.dp))

                run {
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
                                            onClick = { fetchInitialData(selectedCategoryId, forceRefresh = true) }
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
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 145.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                            val visibleInfo = gridState.layoutInfo.visibleItemsInfo
                                            val firstItem = visibleInfo.firstOrNull()
                                            if (firstItem != null && firstItem.index == 0) {
                                                val firstRowY = firstItem.offset.y
                                                val currentItemInfo = visibleInfo.firstOrNull { it.index == targetItemIndex }
                                                if (currentItemInfo != null && currentItemInfo.offset.y == firstRowY) {
                                                    return@onPreviewKeyEvent runCatching { vodTopBarRequesters[0].requestFocus() }.isSuccess
                                                }
                                            }
                                            false
                                        } else false
                                    }
                            ) {
                                itemsIndexed(
                                    items = pagedItems,
                                    key = { idx, item -> "${item.uniqueKey}_$idx" }
                                ) { index, item ->
                                    val itemReq = if (index == 0) itemsEntryRequester else vodItemRequesters.getOrPut(index) { FocusRequester() }
                                    VodPosterCard(
                                        item = item,
                                        focusRequester = itemReq,
                                        onFocused = { onItemFocused(item.id) },
                                        onClick = {
                                            onItemFocused(item.id)
                                            if (item.isSeries || isSeriesMode) {
                                                selectedSeriesForEpisodes = item
                                                isLoadingEpisodes = true
                                                scope.launch {
                                                    seasonsMap = if (portal.protocol == "XTREAM") {
                                                        XtreamApiClient.getSeriesEpisodes(portal, item.id)
                                                    } else {
                                                        StalkerApiClient.getSeriesSeasonsAndEpisodes(portal, item)
                                                    }
                                                    selectedSeasonNum = seasonsMap.keys.firstOrNull() ?: 1
                                                    isLoadingEpisodes = false
                                                }
                                            } else {
                                                scope.launch {
                                                    val saved = dao.getWatchProgress(portal.id, item.id)
                                                    val hintWithYear = if (item.year.isNotBlank()) "${item.name} ${item.year}" else item.name
                                                    onPlayVod(
                                                        PlaybackRequest(
                                                            portal = portal,
                                                            contentType = "MOVIE",
                                                            itemId = item.id,
                                                            title = item.name,
                                                            subtitleQueryHint = hintWithYear,
                                                            posterOrLogo = item.posterUrl,
                                                            streamCmdOrUrl = item.streamCmdOrUrl,
                                                            containerExtension = item.containerExtension,
                                                            resumePositionMs = saved?.positionMs ?: 0L
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCategoryManager) {
            CategoryRulesManagerDialog(
                portalName = portal.name,
                sectionTitle = if (isSeriesMode) (if (isAr) "المسلسلات" else "Series") else (if (isAr) "الأفلام" else "Movies"),
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
                                contentType = vodRuleType,
                                categoryId = cat.id,
                                categoryName = cat.name,
                                isHidden = hide,
                                isLocked = existing?.isLocked ?: false
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
                                contentType = vodRuleType,
                                categoryId = cat.id,
                                categoryName = cat.name,
                                isHidden = existing?.isHidden ?: false,
                                isLocked = lock
                            )
                        )
                    }
                },
                onSetAllAdultHidden = { adultCats, hide ->
                    scope.launch {
                        val updates = adultCats.map { cat ->
                            val existing = rulesMap[cat.id]
                            CategoryRuleEntity(
                                id = existing?.id ?: 0L,
                                portalId = portal.id,
                                contentType = vodRuleType,
                                categoryId = cat.id,
                                categoryName = cat.name,
                                isHidden = hide,
                                isLocked = existing?.isLocked ?: false
                            )
                        }
                        dao.upsertCategoryRules(updates)
                        if (hide && adultCats.any { it.id == selectedCategoryId }) selectedCategoryId = "ALL"
                    }
                },
                onDismiss = { showCategoryManager = false }
            )
        }

        pendingPinCategory?.let { targetCat ->
            TvPinDialog(
                title = if (isAr) "مجموعة محمية: ${targetCat.name}" else "Protected: ${targetCat.name}",
                isAr = isAr,
                errorMessage = pinErrorText,
                onPinSubmitted = { entered ->
                    if (entered == parentalPin) {
                        unlockedSessionGroups = unlockedSessionGroups + targetCat.id
                        selectedCategoryId = targetCat.id
                        pendingPinCategory = null
                        pinErrorText = null
                    } else {
                        pinErrorText = if (isAr) "رمز PIN غير صحيح" else "Incorrect PIN"
                    }
                },
                onDismiss = { pendingPinCategory = null; pinErrorText = null }
            )
        }

        if (showWatchHistoryDialog) {
            val histListState = rememberLazyListState()
            val histRequesters = remember(watchHistory.size) { HashMap<Int, FocusRequester>() }
            val clearHistoryBtnReq = remember { FocusRequester() }
            val closeHistBtnReq = remember { FocusRequester() }
            TvModalDialog(
                title = if (isAr) "سجل المشاهدة (${watchHistory.size})" else "Watch History (${watchHistory.size})",
                onDismiss = { showWatchHistoryDialog = false },
                maxWidth = 640.dp,
                restoreFocusRequester = vodTopBarRequesters[1],
                closeBtnRequester = closeHistBtnReq,
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (watchHistory.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                            val lastIdx = (watchHistory.size - 1).coerceAtLeast(0)
                                            runCatching { histRequesters[lastIdx]?.requestFocus() }.isSuccess
                                        } else false
                                    }
                                    .tvFocusItem(
                                        focusRequester = clearHistoryBtnReq,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            scope.launch { dao.clearWatchHistory(portal.id) }
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = if (isAr) "مسح السجل بالكامل" else "Clear All History",
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
                                        if (watchHistory.isNotEmpty()) {
                                            runCatching { histRequesters[(watchHistory.size - 1).coerceAtLeast(0)]?.requestFocus() }.isSuccess
                                        } else {
                                            runCatching { closeHistBtnReq.requestFocus() }.isSuccess
                                        }
                                    } else false
                                }
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { showWatchHistoryDialog = false }
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
                if (watchHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isAr) "سجل المشاهدة فارغ حالياً" else "Watch history is empty",
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
                            items = watchHistory,
                            key = { idx, entry -> "hist_dlg_${entry.id}_${entry.itemId}_$idx" }
                        ) { hIdx, entry ->
                            val hReq = if (hIdx == 0) firstReq else histRequesters.getOrPut(hIdx) { FocusRequester() }
                            val progress = if (entry.durationMs > 0L) {
                                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvVerticalStepItem(
                                        index = hIdx,
                                        totalCount = watchHistory.size,
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
                                            showWatchHistoryDialog = false
                                            onPlayVod(
                                                PlaybackRequest(
                                                    portal = portal,
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
                                    LtrText(
                                        text = "${LocaleUtils.formatDuration(entry.positionMs)} / ${LocaleUtils.formatDuration(entry.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedSeriesForEpisodes?.let { series ->
            TvModalDialog(
                title = series.name,
                onDismiss = { selectedSeriesForEpisodes = null },
                maxWidth = 640.dp
            ) { firstRequester ->
                if (isLoadingEpisodes) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val seasons = seasonsMap.keys.toList()
                    val episodes = seasonsMap[selectedSeasonNum].orEmpty()
                    val seasonRequesters = remember(seasons) {
                        List(seasons.size) { idx -> if (idx == 0) firstRequester else FocusRequester() }
                    }
                    val episodeRequesters = remember(selectedSeasonNum, episodes.size) { HashMap<Int, FocusRequester>() }
                    val episodeListState = rememberLazyListState()

                    if (seasons.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            seasons.forEachIndexed { sIdx, sNum ->
                                val active = sNum == selectedSeasonNum
                                Box(
                                    modifier = Modifier
                                        .tvHorizontalStepItem(
                                            index = sIdx,
                                            totalCount = seasons.size,
                                            requesters = seasonRequesters,
                                            isAr = isAr,
                                            onDown = {
                                                if (episodes.isNotEmpty()) {
                                                    runCatching { episodeRequesters.getOrPut(0) { FocusRequester() }.requestFocus() }.isSuccess
                                                } else false
                                            }
                                        )
                                        .tvFocusItem(
                                            focusRequester = seasonRequesters[sIdx],
                                            shape = RoundedCornerShape(8.dp),
                                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            onClick = { selectedSeasonNum = sNum }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (isAr) "الموسم ${LocaleUtils.formatInt(sNum)}" else "Season ${LocaleUtils.formatInt(sNum)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    LazyColumn(
                        state = episodeListState,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxWidth().height(250.dp)
                    ) {
                        itemsIndexed(
                            items = episodes,
                            key = { idx, ep -> "${ep.uniqueKey}_$idx" }
                        ) { epIdx, ep ->
                            val epReq = episodeRequesters.getOrPut(epIdx) { FocusRequester() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvVerticalStepItem(
                                        index = epIdx,
                                        totalCount = episodes.size,
                                        listState = episodeListState,
                                        requesters = episodeRequesters,
                                        scope = scope,
                                        onExitTop = {
                                            val sIdx = seasons.indexOf(selectedSeasonNum).coerceAtLeast(0)
                                            runCatching { seasonRequesters.getOrNull(sIdx)?.requestFocus() }.isSuccess
                                        }
                                    )
                                    .tvFocusItem(
                                        focusRequester = epReq,
                                        shape = RoundedCornerShape(10.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        onClick = {
                                            scope.launch {
                                                val saved = dao.getWatchProgress(portal.id, ep.id)
                                                val epCode = String.format(java.util.Locale.US, "S%02dE%02d", ep.seasonNumber, ep.episodeNumber)
                                                val fullTitle = "${series.name} - $epCode"
                                                selectedSeriesForEpisodes = null
                                                onPlayVod(
                                                    PlaybackRequest(
                                                        portal = portal,
                                                        contentType = "EPISODE",
                                                        itemId = ep.id,
                                                        seriesId = series.id,
                                                        title = fullTitle,
                                                        subtitleQueryHint = com.kadri.stalkerplayerpro.player.cleanMovieTitleForSubtitleSearch(series.name),
                                                        posterOrLogo = series.posterUrl,
                                                        streamCmdOrUrl = ep.streamCmdOrUrl,
                                                        containerExtension = ep.containerExtension,
                                                        resumePositionMs = saved?.positionMs ?: 0L
                                                    )
                                                )
                                            }
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                LtrText(
                                    text = "E${LocaleUtils.formatInt(ep.episodeNumber)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isAr) "الحلقة ${LocaleUtils.formatInt(ep.episodeNumber)} - ${ep.title}" else ep.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
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
}

@Composable
private fun VodPosterCard(
    item: VodItem,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusItem(
                focusRequester = focusRequester,
                shape = RoundedCornerShape(10.dp),
                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                onClick = onClick
            )
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .size(240, 360)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (item.year.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    LtrText(
                        text = item.year,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD600)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WatchHistoryList(
    history: List<WatchHistoryEntity>,
    isAr: Boolean,
    onResume: (WatchHistoryEntity) -> Unit
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isAr) "سجل المشاهدة فارغ حالياً" else "Watch history is empty",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = history,
            key = { idx, entry -> "history_${entry.id}_${entry.itemId}_$idx" }
        ) { _, entry ->
            val progress = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusItem(
                        shape = RoundedCornerShape(10.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        onClick = { onResume(entry) }
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
                    LtrText(
                        text = "${LocaleUtils.formatDuration(entry.positionMs)} / ${LocaleUtils.formatDuration(entry.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

package com.kadri.stalkerplayerpro.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.PortalCacheStore
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.local.WatchHistoryEntity
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.EpisodeItem
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.data.model.VodItem
import com.kadri.stalkerplayerpro.data.network.StalkerApiClient
import com.kadri.stalkerplayerpro.data.network.XtreamApiClient
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VodScreen(
    portal: PortalEntity?,
    dao: AppDao,
    isSeriesMode: Boolean,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    lastFocusedItemId: String? = null,
    onItemFocused: (String) -> Unit = {},
    onGoToPortals: () -> Unit = {},
    onPlayVod: (PlaybackRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (portal == null) {
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
        return
    }

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

    LaunchedEffect(searchQuery) {
        delay(450)
        debouncedQuery = searchQuery.trim()
        visibleCount = 30
    }

    fun fetchInitialData(catId: String = selectedCategoryId, forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            hasError = false
            visibleCount = 30
            currentStalkerPage = 1

            if (catId == "HISTORY") {
                isLoading = false
                return@launch
            }

            if (!forceRefresh) {
                val cached = PortalCacheStore.loadVodCache(context, portal.id, isSeriesMode, catId)
                if (cached != null) {
                    if (cached.first.isNotEmpty()) categories = cached.first
                    allLoadedItems = cached.second
                    isLoading = false
                    return@launch
                }
            }

            when (portal.protocol) {
                "XTREAM" -> {
                    val catAction = if (isSeriesMode) "get_series_categories" else "get_vod_categories"
                    if (categories.isEmpty() || forceRefresh) {
                        categories = XtreamApiClient.getCategories(portal, catAction)
                    }
                    val items = if (isSeriesMode) {
                        XtreamApiClient.getSeriesList(portal, catId)
                    } else {
                        XtreamApiClient.getVodStreams(portal, catId)
                    }
                    allLoadedItems = items
                    hasError = items.isEmpty()
                    if (items.isNotEmpty()) {
                        PortalCacheStore.saveVodCache(context, portal.id, isSeriesMode, catId, categories, items)
                    }
                }
                "STALKER" -> {
                    val stkType = if (isSeriesMode) "series" else "vod"
                    if (categories.isEmpty() || forceRefresh) {
                        categories = StalkerApiClient.getVodCategories(portal, stkType)
                    }
                    val stkCat = if (catId == "ALL") "*" else catId
                    val items = StalkerApiClient.getVodOrderedList(portal, stkType, stkCat, page = 1)
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
            isLoading = false
        }
    }

    LaunchedEffect(portal.id, isSeriesMode, selectedCategoryId) {
        fetchInitialData(selectedCategoryId, forceRefresh = false)
    }

    val fullCategories = remember(categories, isAr) {
        buildList {
            add(CategoryItem("ALL", if (isAr) "الكل" else "All"))
            add(CategoryItem("HISTORY", if (isAr) "سجل المشاهدة" else "Continue Watching"))
            addAll(categories)
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
    val lastItemRequester = remember { FocusRequester() }
    val targetItemIndex = remember(pagedItems, lastFocusedItemId) {
        if (lastFocusedItemId != null) {
            pagedItems.indexOfFirst { it.id == lastFocusedItemId }
        } else -1
    }

    // استعادة آخر فيلم أو مسلسل كان يقف عليه المؤشر عند الخروج من المشغّل أو الرجوع
    LaunchedEffect(isLoading, pagedItems.size, lastFocusedItemId, selectedSeriesForEpisodes) {
        if (!isLoading && pagedItems.isNotEmpty() && selectedSeriesForEpisodes == null && targetItemIndex >= 0) {
            runCatching { gridState.scrollToItem(targetItemIndex) }
            delay(70)
            runCatching { lastItemRequester.requestFocus() }
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
                .blockFocusWhen(selectedSeriesForEpisodes != null)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = if (isSeriesMode) {
                        if (isAr) "أقسام المسلسلات" else "Series Categories"
                    } else {
                        if (isAr) "أقسام الأفلام" else "Movie Categories"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = fullCategories,
                        key = { idx, cat -> "vod_cat_${portal.id}_${isSeriesMode}_${cat.id}_$idx" }
                    ) { _, cat ->
                        val isSelected = cat.id == selectedCategoryId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // التنقل الفوري بالريموت بين الأقسام دون الحاجة لزر OK
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && selectedCategoryId != cat.id) {
                                        selectedCategoryId = cat.id
                                    }
                                }
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    },
                                    onClick = { selectedCategoryId = cat.id }
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
                        focusRequester = initialContentFocusRequester,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .tvFocusItem(
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

                if (selectedCategoryId == "HISTORY") {
                    WatchHistoryList(
                        history = watchHistory,
                        isAr = isAr,
                        onResume = { entry ->
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
                } else {
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
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = pagedItems,
                                    key = { idx, item -> "${item.uniqueKey}_$idx" }
                                ) { index, item ->
                                    VodPosterCard(
                                        item = item,
                                        focusRequester = if (index == targetItemIndex) lastItemRequester else null,
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
                    if (seasons.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            seasons.forEachIndexed { sIdx, sNum ->
                                val active = sNum == selectedSeasonNum
                                Box(
                                    modifier = Modifier
                                        .tvFocusItem(
                                            focusRequester = if (sIdx == 0) firstRequester else null,
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

                    val episodes = seasonsMap[selectedSeasonNum].orEmpty()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxWidth().height(250.dp)
                    ) {
                        itemsIndexed(
                            items = episodes,
                            key = { idx, ep -> "${ep.uniqueKey}_$idx" }
                        ) { _, ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusItem(
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
                                                        subtitleQueryHint = "${series.name} $epCode",
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

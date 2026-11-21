package com.kadri.stalkerplayerpro.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvSafeSearchBar
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvHorizontalStepItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.local.FavoriteEntity
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GlobalFavoritesScreen(
    dao: AppDao,
    settings: TvUserSettings = TvUserSettings(),
    settingsStore: SettingsDataStore? = null,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateToTopBar: () -> Boolean = { false },
    onPlayFavorite: (PlaybackRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allFavorites by dao.observeAllFavoritesGlobal().collectAsState(initial = emptyList())
    val allPortals by dao.observePortals().collectAsState(initial = emptyList())
    val portalsMap = remember(allPortals) { allPortals.associateBy { it.id } }

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, LIVE, MOVIE, SERIES
    var searchQuery by remember { mutableStateOf("") }
    var pendingDeleteFav by remember { mutableStateOf<FavoriteEntity?>(null) }

    val filteredFavorites = remember(allFavorites, selectedFilter, searchQuery, portalsMap) {
        val q = searchQuery.trim()
        allFavorites.filter { fav ->
            val matchesType = when (selectedFilter) {
                "ALL" -> true
                "LIVE" -> fav.contentType == "LIVE"
                "MOVIE" -> fav.contentType == "MOVIE"
                "SERIES" -> fav.contentType == "SERIES" || fav.contentType == "EPISODE"
                else -> true
            }
            val portalName = portalsMap[fav.portalId]?.name.orEmpty()
            val matchesQuery = q.isEmpty() ||
                fav.title.contains(q, ignoreCase = true) ||
                portalName.contains(q, ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    val topBarRequesters = remember {
        List(5) { idx -> if (idx == 0) initialContentFocusRequester else FocusRequester() }
    }
    val favListState = rememberLazyListState()
    val favPlayRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { initialContentFocusRequester.requestFocus() }
    }

    val focusFirstFavoritePlay: () -> Boolean = {
        if (filteredFavorites.isNotEmpty()) {
            runCatching { favPlayRequesters.getOrPut(0) { FocusRequester() }.requestFocus() }.isSuccess
        } else {
            false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(pendingDeleteFav != null)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to (if (isAr) "الكل (${allFavorites.size})" else "All (${allFavorites.size})"),
                    "LIVE" to (if (isAr) "قنوات" else "Live TV"),
                    "MOVIE" to (if (isAr) "أفلام" else "Movies"),
                    "SERIES" to (if (isAr) "مسلسلات" else "Series")
                )
                filters.forEachIndexed { idx, (code, label) ->
                    val active = selectedFilter == code
                    Box(
                        modifier = Modifier
                            .tvHorizontalStepItem(
                                index = idx,
                                totalCount = 5,
                                requesters = topBarRequesters,
                                isAr = isAr,
                                onUp = { onNavigateToTopBar() },
                                onDown = focusFirstFavoritePlay
                            )
                            .tvFocusItem(
                                focusRequester = topBarRequesters[idx],
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                onClick = { selectedFilter = code }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TvSafeSearchBar(
                    query = searchQuery,
                    placeholder = if (isAr) "بحث في المفضلة الشاملة..." else "Search all favorites...",
                    onQueryChange = { searchQuery = it },
                    focusRequester = topBarRequesters[4],
                    isAr = isAr,
                    searchHistory = settings.searchHistory,
                    onSaveSearchQuery = { q -> scope.launch { settingsStore?.addSearchHistoryQuery(q) } },
                    onClearSearchHistory = { scope.launch { settingsStore?.clearSearchHistory() } },
                    modifier = Modifier
                        .weight(1f)
                        .tvHorizontalStepItem(
                            index = 4,
                            totalCount = 5,
                            requesters = topBarRequesters,
                            isAr = isAr,
                            onUp = { onNavigateToTopBar() },
                            onDown = focusFirstFavoritePlay,
                            onExitEnd = focusFirstFavoritePlay
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredFavorites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isAr) "لا توجد عناصر في المفضلة الشاملة حالياً" else "No items in Global Favorites yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = favListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filteredFavorites,
                        key = { idx, fav -> "gfav_${fav.id}_${fav.portalId}_${fav.itemId}_$idx" }
                    ) { index, fav ->
                        val portal = portalsMap[fav.portalId]
                        val typeLabel = when (fav.contentType) {
                            "LIVE" -> if (isAr) "قناة" else "LIVE"
                            "MOVIE" -> if (isAr) "فيلم" else "MOVIE"
                            else -> if (isAr) "مسلسل" else "SERIES"
                        }
                        val typeIcon = when (fav.contentType) {
                            "LIVE" -> Icons.Default.LiveTv
                            "MOVIE" -> Icons.Default.Movie
                            else -> Icons.Default.VideoLibrary
                        }

                        val playReq = favPlayRequesters.getOrPut(index) { FocusRequester() }
                        val deleteReq = remember(fav.id, index) { FocusRequester() }
                        val rowStepMod = Modifier.tvVerticalStepItem(
                            index = index,
                            totalCount = filteredFavorites.size,
                            listState = favListState,
                            requesters = favPlayRequesters,
                            scope = scope,
                            onExitTop = { runCatching { topBarRequesters[0].requestFocus() }.isSuccess }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (fav.logoOrPoster.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(fav.logoOrPoster).crossfade(false).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = typeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fav.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LtrText(
                                        text = "[${portal?.name ?: "Server #${fav.portalId}"}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            // زر التشغيل هو الهدف الأول للتركيز دائماً لحماية المفضلة من الحذف غير المقصود
                            Box(
                                modifier = Modifier
                                    .then(rowStepMod)
                                    .focusProperties {
                                        if (isAr) left = deleteReq else right = deleteReq
                                    }
                                    .tvFocusItem(
                                        focusRequester = playReq,
                                        shape = RoundedCornerShape(9.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            if (portal != null) {
                                                onPlayFavorite(
                                                    PlaybackRequest(
                                                        portal = portal,
                                                        contentType = fav.contentType,
                                                        itemId = fav.itemId,
                                                        title = fav.title,
                                                        subtitleQueryHint = fav.title,
                                                        posterOrLogo = fav.logoOrPoster,
                                                        streamCmdOrUrl = fav.streamCmdOrUrl
                                                    )
                                                )
                                            }
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAr) "تشغيل" else "Play",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .then(rowStepMod)
                                    .focusProperties {
                                        if (isAr) right = playReq else left = playReq
                                    }
                                    .tvFocusItem(
                                        focusRequester = deleteReq,
                                        shape = RoundedCornerShape(9.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                                        onClick = { pendingDeleteFav = fav }
                                    )
                                    .padding(9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        pendingDeleteFav?.let { targetFav ->
            TvModalDialog(
                title = if (isAr) "تأكيد الحذف من المفضلة" else "Confirm Remove Favorite",
                onDismiss = { pendingDeleteFav = null },
                maxWidth = 440.dp
            ) { firstReq ->
                Text(
                    text = if (isAr) {
                        "هل تريد حذف «${targetFav.title}» من المفضلة الشاملة؟"
                    } else {
                        "Remove \"${targetFav.title}\" from Global Favorites?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    Box(
                        modifier = Modifier
                            .tvFocusItem(
                                focusRequester = firstReq,
                                shape = RoundedCornerShape(9.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { pendingDeleteFav = null }
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
                            .tvFocusItem(
                                shape = RoundedCornerShape(9.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.error,
                                onClick = {
                                    scope.launch {
                                        dao.removeFavorite(targetFav.portalId, targetFav.contentType, targetFav.itemId)
                                    }
                                    pendingDeleteFav = null
                                }
                            )
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (isAr) "حذف" else "Delete",
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

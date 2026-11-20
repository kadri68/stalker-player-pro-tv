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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.TvSafeSearchBar
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.FavoriteEntity
import com.kadri.stalkerplayerpro.data.model.PlaybackRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GlobalFavoritesScreen(
    dao: AppDao,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onPlayFavorite: (PlaybackRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allFavorites by dao.observeAllFavoritesGlobal().collectAsState(initial = emptyList())
    val allPortals by dao.observePortals().collectAsState(initial = emptyList())
    val portalsMap = remember(allPortals) { allPortals.associateBy { it.id } }

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, LIVE, MOVIE, SERIES
    var searchQuery by remember { mutableStateOf("") }

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

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { initialContentFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                        .tvFocusItem(
                            focusRequester = if (idx == 0) initialContentFocusRequester else null,
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
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = filteredFavorites,
                    key = { idx, fav -> "gfav_${fav.id}_${fav.portalId}_${fav.itemId}_$idx" }
                ) { _, fav ->
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

                        if (portal != null) {
                            Box(
                                modifier = Modifier
                                    .tvFocusItem(
                                        shape = RoundedCornerShape(9.dp),
                                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                        onClick = {
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
                        }

                        Box(
                            modifier = Modifier
                                .tvFocusItem(
                                    shape = RoundedCornerShape(9.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                                    onClick = {
                                        scope.launch {
                                            dao.removeFavorite(fav.portalId, fav.contentType, fav.itemId)
                                        }
                                    }
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
}

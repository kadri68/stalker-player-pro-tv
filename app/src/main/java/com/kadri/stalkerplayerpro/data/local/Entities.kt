package com.kadri.stalkerplayerpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "portals")
data class PortalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val protocol: String, // STALKER, XTREAM, M3U
    val url: String,
    val mac: String = "",
    val username: String = "",
    val password: String = "",
    val expiryTimestampMs: Long = 0L,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0,
    val isActive: Boolean = false,
    val statusText: String = ""
)

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["portalId", "contentType", "itemId"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portalId: Long,
    val contentType: String, // LIVE, MOVIE, SERIES
    val itemId: String,
    val title: String,
    val logoOrPoster: String = "",
    val streamCmdOrUrl: String = "",
    val categoryId: String = "",
    val extraInfo: String = "",
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "watch_history",
    indices = [Index(value = ["portalId", "contentType", "itemId"], unique = true)]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portalId: Long,
    val contentType: String, // MOVIE, EPISODE
    val itemId: String,
    val seriesId: String = "",
    val title: String,
    val posterUrl: String = "",
    val streamCmdOrUrl: String = "",
    val containerExtension: String = "mp4",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "channel_overrides",
    indices = [Index(value = ["portalId", "channelId"], unique = true)]
)
data class ChannelOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portalId: Long,
    val channelId: String,
    val customName: String = "",
    val isHidden: Boolean = false
)

@Entity(
    tableName = "locked_groups",
    indices = [Index(value = ["portalId", "categoryId"], unique = true)]
)
data class LockedGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val portalId: Long,
    val categoryId: String,
    val categoryName: String
)

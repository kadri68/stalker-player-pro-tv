package com.kadri.stalkerplayerpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ==================== البوابات والحسابات ====================
    @Query("SELECT * FROM portals ORDER BY isFavorite DESC, orderIndex ASC, id ASC")
    fun observePortals(): Flow<List<PortalEntity>>

    @Query("SELECT * FROM portals ORDER BY isFavorite DESC, orderIndex ASC, id ASC")
    suspend fun getAllPortals(): List<PortalEntity>

    @Query("SELECT * FROM portals WHERE isActive = 1 LIMIT 1")
    fun observeActivePortal(): Flow<PortalEntity?>

    @Query("SELECT * FROM portals WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePortal(): PortalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortal(portal: PortalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortals(portals: List<PortalEntity>)

    @Update
    suspend fun updatePortal(portal: PortalEntity)

    @Query("DELETE FROM portals WHERE id IN (:ids)")
    suspend fun deletePortalsByIds(ids: List<Long>)

    @Query("UPDATE portals SET isActive = (id = :portalId)")
    suspend fun setActivePortal(portalId: Long)

    @Query("UPDATE portals SET isFavorite = :isFav WHERE id = :portalId")
    suspend fun setPortalFavorite(portalId: Long, isFav: Boolean)

    @Query("UPDATE portals SET orderIndex = :newOrder WHERE id = :portalId")
    suspend fun updatePortalOrder(portalId: Long, newOrder: Int)

    @Transaction
    suspend fun movePortal(portalId: Long, moveUp: Boolean) {
        val all = getAllPortals()
        val index = all.indexOfFirst { it.id == portalId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in all.indices) return

        val mutable = all.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)

        mutable.forEachIndexed { idx, portal ->
            if (portal.orderIndex != idx) {
                updatePortalOrder(portal.id, idx)
            }
        }
    }

    // ==================== المفضلة ====================
    @Query("SELECT * FROM favorites WHERE portalId = :portalId AND contentType = :type ORDER BY addedAtMs DESC")
    fun observeFavorites(portalId: Long, type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAtMs DESC")
    fun observeAllFavoritesGlobal(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE portalId = :portalId AND contentType = :type AND itemId = :itemId")
    suspend fun removeFavorite(portalId: Long, type: String, itemId: String)

    // ==================== سجل المشاهدة والاستئناف التلقائي ====================
    @Query("SELECT * FROM watch_history WHERE portalId = :portalId ORDER BY updatedAtMs DESC LIMIT 40")
    fun observeWatchHistory(portalId: Long): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE portalId = :portalId AND itemId = :itemId LIMIT 1")
    suspend fun getWatchProgress(portalId: Long, itemId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE portalId = :portalId AND itemId = :itemId")
    suspend fun removeWatchHistory(portalId: Long, itemId: String)

    // ==================== تخصيص القنوات (إعادة تسمية / إخفاء) ====================
    @Query("SELECT * FROM channel_overrides WHERE portalId = :portalId")
    fun observeChannelOverrides(portalId: Long): Flow<List<ChannelOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannelOverride(overrideEntity: ChannelOverrideEntity)

    // ==================== الرقابة الأبوية (قفل المجموعات) ====================
    @Query("SELECT * FROM locked_groups WHERE portalId = :portalId")
    fun observeLockedGroups(portalId: Long): Flow<List<LockedGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun lockGroup(lockedGroup: LockedGroupEntity)

    @Query("DELETE FROM locked_groups WHERE portalId = :portalId AND categoryId = :categoryId")
    suspend fun unlockGroup(portalId: Long, categoryId: String)

    // ==================== قواعد المجموعات لكل سيرفر (إخفاء / قفل / الكبار) ====================
    @Query("SELECT * FROM category_rules WHERE portalId = :portalId AND contentType = :contentType")
    fun observeCategoryRules(portalId: Long, contentType: String): Flow<List<CategoryRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryRule(rule: CategoryRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryRules(rules: List<CategoryRuleEntity>)
}

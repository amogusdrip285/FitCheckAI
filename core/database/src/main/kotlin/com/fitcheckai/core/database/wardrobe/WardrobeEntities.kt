package com.fitcheckai.core.database.wardrobe

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wardrobe_v2")
data class WardrobeEntity(
    @PrimaryKey val id: String,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val payload: String?,
)
@Entity(tableName = "wardrobe_cleanup")
data class MediaCleanupEntity(@PrimaryKey val mediaId: String, val ref: String)
@Entity(tableName = "wardrobe_legacy_ids", indices = [Index(value = ["itemId"], unique = true)])
data class LegacyIdEntity(@PrimaryKey val legacyId: String, val itemId: String)

@Dao
interface WardrobeDao {
    @Query("SELECT * FROM wardrobe_v2 WHERE deletedAt IS NULL ORDER BY createdAt DESC, id ASC")
    fun observeActive(): Flow<List<WardrobeEntity>>
    @Query("SELECT * FROM wardrobe_v2 WHERE deletedAt IS NULL ORDER BY createdAt DESC, id ASC")
    suspend fun active(): List<WardrobeEntity>
    @Query("SELECT * FROM wardrobe_v2 ORDER BY id") suspend fun all(): List<WardrobeEntity>
    @Query("SELECT * FROM wardrobe_v2 WHERE id = :id") suspend fun get(id: String): WardrobeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entity: WardrobeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(entity: MediaCleanupEntity)
    @Query("SELECT * FROM wardrobe_cleanup") suspend fun pending(): List<MediaCleanupEntity>
    @Query("DELETE FROM wardrobe_cleanup WHERE mediaId = :id") suspend fun finishCleanup(id: String)
    @Query("SELECT legacyId FROM wardrobe_legacy_ids WHERE itemId = :id") suspend fun legacyId(id: String): String?
    @Query("SELECT itemId FROM wardrobe_legacy_ids WHERE legacyId = :id") suspend fun migratedId(id: String): String?
}

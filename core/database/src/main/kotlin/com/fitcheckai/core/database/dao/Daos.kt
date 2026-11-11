package com.fitcheckai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fitcheckai.core.database.entity.ClothingItemEntity
import com.fitcheckai.core.database.entity.FeedbackEntity
import com.fitcheckai.core.database.entity.OutfitEntity
import com.fitcheckai.core.database.entity.WearLogEntity
import kotlinx.coroutines.flow.Flow

/** OWNER: Track A */
@Dao
interface ClothingItemDao {
    @Query("SELECT * FROM clothing_items ORDER BY createdAtMillis DESC, id ASC")
    fun observeAll(): Flow<List<ClothingItemEntity>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getById(id: String): ClothingItemEntity?

    @Upsert
    suspend fun upsert(item: ClothingItemEntity)

    @Query(
        "UPDATE clothing_items SET category = :category, subCategory = :subCategory, " +
            "colors = :colors, pattern = :pattern, material = :material, formality = :formality, " +
            "seasons = :seasons, styleTags = :styleTags WHERE id = :id",
    )
    suspend fun updateAttributes(
        id: String, category: String, subCategory: String, colors: String, pattern: String,
        material: String?, formality: Int, seasons: String, styleTags: String,
    ): Int

    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE clothing_items SET wearCount = wearCount + 1, " +
            "lastWornAtMillis = MAX(COALESCE(lastWornAtMillis, :atMillis), :atMillis) " +
            "WHERE id IN (:ids)",
    )
    suspend fun markWorn(ids: List<String>, atMillis: Long)
}

/** OWNER: Track C (ghi outfit đã gợi ý) — schema do Track A quản lý. */
@Dao
interface OutfitDao {
    @Upsert
    suspend fun upsert(outfit: OutfitEntity)

    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getById(id: String): OutfitEntity?
}

/** OWNER: Track D — schema do Track A quản lý. */
@Dao
interface WearLogDao {
    @Query("SELECT * FROM wear_logs ORDER BY wornAtMillis DESC")
    fun observeAll(): Flow<List<WearLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WearLogEntity)
}

/** OWNER: Track D — schema do Track A quản lý. */
@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: FeedbackEntity)

    @Query("SELECT * FROM outfit_feedback ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<FeedbackEntity>>
}

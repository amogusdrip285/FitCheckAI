package com.fitcheckai.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Danh sách được lưu dạng chuỗi nối bằng ListCodec (xem Mappers.kt) — tránh TypeConverter phức tạp.

@Entity(tableName = "clothing_items")
data class ClothingItemEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val imageHash: String?,
    val category: String,
    val subCategory: String,
    val colors: String,
    val pattern: String,
    val material: String?,
    val formality: Int,
    val seasons: String,
    val styleTags: String,
    val createdAtMillis: Long,
    val wearCount: Int,
    val lastWornAtMillis: Long?,
)

@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey val id: String,
    val itemIds: String,
    val occasion: String,
    val explanation: String,
    val alternativesHint: String?,
    val source: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "wear_logs", indices = [Index("wornAtMillis")])
data class WearLogEntity(
    @PrimaryKey val id: String,
    val outfitId: String,
    val itemIds: String,
    val wornAtMillis: Long,
)

@Entity(tableName = "outfit_feedback", indices = [Index("outfitId")])
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val outfitId: String,
    val liked: Boolean,
    val reason: String?,
    val createdAtMillis: Long,
)

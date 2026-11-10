package com.fitcheckai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fitcheckai.core.database.dao.ClothingItemDao
import com.fitcheckai.core.database.dao.FeedbackDao
import com.fitcheckai.core.database.dao.OutfitDao
import com.fitcheckai.core.database.dao.WearLogDao
import com.fitcheckai.core.database.entity.ClothingItemEntity
import com.fitcheckai.core.database.entity.FeedbackEntity
import com.fitcheckai.core.database.entity.OutfitEntity
import com.fitcheckai.core.database.entity.WearLogEntity

/** Đổi schema ⇒ tăng version + viết Migration (không dùng fallbackToDestructiveMigration). */
@Database(
    entities = [
        ClothingItemEntity::class,
        OutfitEntity::class,
        WearLogEntity::class,
        FeedbackEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun outfitDao(): OutfitDao
    abstract fun wearLogDao(): WearLogDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        const val NAME = "fitcheck.db"
    }
}

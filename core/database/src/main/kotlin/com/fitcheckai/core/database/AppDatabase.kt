package com.fitcheckai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fitcheckai.core.database.dao.ClothingItemDao
import com.fitcheckai.core.database.dao.FeedbackDao
import com.fitcheckai.core.database.dao.OutfitDao
import com.fitcheckai.core.database.dao.WearLogDao
import com.fitcheckai.core.database.entity.ClothingItemEntity
import com.fitcheckai.core.database.entity.FeedbackEntity
import com.fitcheckai.core.database.entity.OutfitEntity
import com.fitcheckai.core.database.entity.WearLogEntity
import com.fitcheckai.core.database.wardrobe.WardrobeEntity
import com.fitcheckai.core.database.wardrobe.MediaCleanupEntity
import com.fitcheckai.core.database.wardrobe.LegacyIdEntity
import com.fitcheckai.core.database.wardrobe.WardrobeDao

/** Đổi schema ⇒ tăng version + viết Migration (không dùng fallbackToDestructiveMigration). */
@Database(
    entities = [
        ClothingItemEntity::class,
        OutfitEntity::class,
        WearLogEntity::class,
        FeedbackEntity::class,
        WardrobeEntity::class,
        MediaCleanupEntity::class,
        LegacyIdEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun outfitDao(): OutfitDao
    abstract fun wearLogDao(): WearLogDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        const val NAME = "fitcheck.db"
    }
}

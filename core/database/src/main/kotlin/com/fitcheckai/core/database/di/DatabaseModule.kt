package com.fitcheckai.core.database.di

import android.content.Context
import androidx.room.Room
import com.fitcheckai.core.database.AppDatabase
import com.fitcheckai.core.database.wardrobe.LegacyWardrobeBridge
import com.fitcheckai.core.database.wardrobe.WardrobeMigration
import com.fitcheckai.core.database.wardrobe.RoomWardrobeRepository
import com.fitcheckai.core.database.dao.ClothingItemDao
import com.fitcheckai.core.database.dao.FeedbackDao
import com.fitcheckai.core.database.dao.OutfitDao
import com.fitcheckai.core.database.dao.WearLogDao
import com.fitcheckai.core.model.WardrobeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(WardrobeMigration(context))
            .build()

    @Provides fun clothingItemDao(db: AppDatabase): ClothingItemDao = db.clothingItemDao()
    @Provides fun outfitDao(db: AppDatabase): OutfitDao = db.outfitDao()
    @Provides fun wearLogDao(db: AppDatabase): WearLogDao = db.wearLogDao()
    @Provides fun feedbackDao(db: AppDatabase): FeedbackDao = db.feedbackDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WardrobeModule {
    @Binds
    abstract fun bindV2WardrobeRepository(impl: RoomWardrobeRepository): com.fitcheckai.core.model.wardrobe.WardrobeRepository
    @Binds
    abstract fun bindWardrobeRepository(impl: LegacyWardrobeBridge): WardrobeRepository
}

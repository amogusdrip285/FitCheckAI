package com.fitcheckai.core.datastore.di

import android.content.Context
import com.fitcheckai.core.datastore.StubWearLogRepository
import com.fitcheckai.core.datastore.profile.FileProfileStorage
import com.fitcheckai.core.datastore.profile.LegacyProfileBridge
import com.fitcheckai.core.model.ProfileRepository
import com.fitcheckai.core.model.WearLogRepository
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.wardrobe.MediaStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    /** v1 adapter over ProfileStorage — remove once StubOutfitService migrates to CT-04. */
    @Binds
    abstract fun bindProfileRepository(impl: LegacyProfileBridge): ProfileRepository

    /** Wear tracking is parked in v2 (TEAM_WORKFLOW §3); kept in-memory only for old v1 screens. */
    @Binds
    abstract fun bindWearLogRepository(impl: StubWearLogRepository): WearLogRepository

    companion object {
        /** noBackupFilesDir: profile + person refs are excluded from cloud backup/device transfer. */
        @Provides
        @Singleton
        fun provideProfileStorage(@ApplicationContext context: Context, media: MediaStore): ProfileStorage =
            FileProfileStorage(File(context.noBackupFilesDir, "profile"), media, Dispatchers.IO)
    }
}

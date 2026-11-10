package com.fitcheckai.core.datastore.di

import com.fitcheckai.core.datastore.StubProfileRepository
import com.fitcheckai.core.datastore.StubWearLogRepository
import com.fitcheckai.core.model.ProfileRepository
import com.fitcheckai.core.model.WearLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    // TODO(Track D): đổi sang DataStoreProfileRepository / RoomWearLogRepository.
    @Binds
    abstract fun bindProfileRepository(impl: StubProfileRepository): ProfileRepository

    @Binds
    abstract fun bindWearLogRepository(impl: StubWearLogRepository): WearLogRepository
}

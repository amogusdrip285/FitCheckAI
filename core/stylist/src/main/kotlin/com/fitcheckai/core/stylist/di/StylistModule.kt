package com.fitcheckai.core.stylist.di

import com.fitcheckai.core.model.OutfitService
import com.fitcheckai.core.stylist.StubOutfitService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StylistModule {
    // TODO(Track C): đổi sang ResilientOutfitService.
    @Binds
    abstract fun bindOutfitService(impl: StubOutfitService): OutfitService
}

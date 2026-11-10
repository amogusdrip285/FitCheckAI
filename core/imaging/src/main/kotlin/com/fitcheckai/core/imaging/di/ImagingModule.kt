package com.fitcheckai.core.imaging.di

import com.fitcheckai.core.imaging.StubImageSegmenter
import com.fitcheckai.core.model.ImageSegmenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImagingModule {
    // TODO(Track A): đổi sang MlKitSegmenter.
    @Binds
    abstract fun bindImageSegmenter(impl: StubImageSegmenter): ImageSegmenter
}

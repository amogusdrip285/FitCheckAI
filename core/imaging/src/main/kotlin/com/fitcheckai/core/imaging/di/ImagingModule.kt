package com.fitcheckai.core.imaging.di

import com.fitcheckai.core.imaging.MlKitSegmenter
import com.fitcheckai.core.model.ImageSegmenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImagingModule {
    @Binds
    abstract fun bindGarmentCutoutProcessor(impl: com.fitcheckai.core.imaging.MlKitGarmentCutoutProcessor): com.fitcheckai.core.imaging.GarmentCutoutProcessor
    @Binds
    abstract fun bindMediaStore(impl: com.fitcheckai.core.imaging.LocalMediaStore): com.fitcheckai.core.model.wardrobe.MediaStore
    @Binds
    abstract fun bindImageSegmenter(impl: MlKitSegmenter): ImageSegmenter
}

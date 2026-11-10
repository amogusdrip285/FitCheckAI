package com.fitcheckai.core.ai.di

import com.fitcheckai.core.ai.StubAiOutfitGenerator
import com.fitcheckai.core.ai.StubClothingTagger
import com.fitcheckai.core.model.AiOutfitGenerator
import com.fitcheckai.core.model.ClothingTagger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    // TODO(Track B): đổi sang ClaudeClothingTagger / ClaudeOutfitGenerator.
    @Binds
    abstract fun bindClothingTagger(impl: StubClothingTagger): ClothingTagger

    @Binds
    abstract fun bindAiOutfitGenerator(impl: StubAiOutfitGenerator): AiOutfitGenerator
}

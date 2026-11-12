package com.fitcheckai.core.ai.di

import com.fitcheckai.core.ai.ProxyRecognitionClient
import com.fitcheckai.core.ai.StubAiOutfitGenerator
import com.fitcheckai.core.ai.StubClothingTagger
import com.fitcheckai.core.ai.proxy.ProxyOutfitRecommendation
import com.fitcheckai.core.model.AiOutfitGenerator
import com.fitcheckai.core.model.ClothingTagger
import com.fitcheckai.core.model.recommendation.OutfitRecommendation
import com.fitcheckai.core.model.wardrobe.ClothingRecognition
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    /** CT-05 (B-007): client của A (`ProxyRecognitionClient`) được nhóm chọn ngày 12/11/2026; B review/bảo trì. */
    @Binds
    abstract fun bindRecognition(impl: ProxyRecognitionClient): ClothingRecognition

    // Các binding tương thích cho consumer v1.
    @Binds
    abstract fun bindClothingTagger(impl: StubClothingTagger): ClothingTagger

    @Binds
    abstract fun bindAiOutfitGenerator(impl: StubAiOutfitGenerator): AiOutfitGenerator

    /** CT-02 v2 (B-003). Chưa cấu hình access code ⇒ lỗi UNSUPPORTED_CAPABILITY có kiểu, không giả AI. */
    @Binds
    abstract fun bindOutfitRecommendation(impl: ProxyOutfitRecommendation): OutfitRecommendation
}

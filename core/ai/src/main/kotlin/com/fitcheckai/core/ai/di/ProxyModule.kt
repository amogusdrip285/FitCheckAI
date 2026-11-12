package com.fitcheckai.core.ai.di

import com.fitcheckai.core.ai.BuildConfig
import com.fitcheckai.core.ai.proxy.ProxyClothingRecognition
import com.fitcheckai.core.ai.proxy.ProxyConfig
import com.fitcheckai.core.ai.proxy.ProxyOutfitRecommendation
import com.fitcheckai.core.ai.proxy.ProxyTransport
import com.fitcheckai.core.ai.proxy.createProxyTransport
import com.fitcheckai.core.model.wardrobe.MediaStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * Track B v2 clients. [ProxyClothingRecognition] được cung cấp nhưng CHƯA bind vào ClothingRecognition:
 * binding thuộc A (feature/wardrobe WardrobeBindings) và đổi ở INT-002.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProxyModule {
    @Provides @Singleton
    fun proxyConfig(): ProxyConfig = ProxyConfig(BuildConfig.PROXY_URL, BuildConfig.PROXY_ACCESS_CODE)

    @Provides @Singleton
    fun proxyTransport(config: ProxyConfig): ProxyTransport = createProxyTransport(config, OkHttp.create())

    @Provides @Singleton
    fun outfitRecommendation(transport: ProxyTransport): ProxyOutfitRecommendation = ProxyOutfitRecommendation(transport)

    @Provides @Singleton
    fun clothingRecognition(transport: ProxyTransport, media: MediaStore): ProxyClothingRecognition =
        ProxyClothingRecognition(transport, media)
}

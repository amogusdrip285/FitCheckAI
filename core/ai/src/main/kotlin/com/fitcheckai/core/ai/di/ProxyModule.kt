package com.fitcheckai.core.ai.di

import com.fitcheckai.core.ai.BuildConfig
import com.fitcheckai.core.ai.proxy.ProxyConfig
import com.fitcheckai.core.ai.proxy.ProxyOutfitRecommendation
import com.fitcheckai.core.ai.proxy.ProxyTransport
import com.fitcheckai.core.ai.proxy.createProxyTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * Track B v2 recommendation client (B-003). Recognition dùng `ProxyRecognitionClient` (bind ở [AiModule]),
 * có phiên demo riêng nhập mã trên UI.
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
}

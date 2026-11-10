package com.fitcheckai.core.analytics.di

import com.fitcheckai.core.analytics.InMemoryMetricsTracker
import com.fitcheckai.core.model.MetricsTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun bindMetricsTracker(impl: InMemoryMetricsTracker): MetricsTracker
}

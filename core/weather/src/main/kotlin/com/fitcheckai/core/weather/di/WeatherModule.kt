package com.fitcheckai.core.weather.di

import com.fitcheckai.core.model.WeatherProvider
import com.fitcheckai.core.weather.StubWeatherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherModule {
    // TODO(Track C): đổi sang OpenMeteoWeatherProvider.
    @Binds
    abstract fun bindWeatherProvider(impl: StubWeatherProvider): WeatherProvider
}

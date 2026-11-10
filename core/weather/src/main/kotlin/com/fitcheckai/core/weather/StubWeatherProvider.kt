package com.fitcheckai.core.weather

import com.fitcheckai.core.model.WeatherProvider
import com.fitcheckai.core.model.WeatherSnapshot
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPRINT 0 STUB — thời tiết Hà Nội cố định.
 * TODO(Track C): OpenMeteoWeatherProvider (vị trí → Open-Meteo, cache 30 phút, fallback snapshot cũ).
 */
@Singleton
class StubWeatherProvider @Inject constructor() : WeatherProvider {
    override suspend fun current(): Result<WeatherSnapshot> = Result.success(
        WeatherSnapshot(
            tempC = 24.0,
            feelsLikeC = 25.0,
            rainProbability = 20,
            condition = "Nhiều mây",
            city = "Hà Nội",
            fetchedAt = Instant.now(),
        ),
    )
}

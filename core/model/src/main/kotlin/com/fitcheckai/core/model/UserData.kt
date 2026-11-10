package com.fitcheckai.core.model

import java.time.Instant

data class WeatherSnapshot(
    val tempC: Double,
    val feelsLikeC: Double,
    val rainProbability: Int,
    /** Mô tả ngắn tiếng Việt, vd. "Mưa rào". */
    val condition: String,
    val city: String,
    val fetchedAt: Instant,
)

data class StyleProfile(
    val styles: Set<String> = emptySet(),
    val fitNotes: String? = null,
    val city: String? = null,
)

data class WearLogEntry(
    val id: String,
    val outfitId: String,
    val itemIds: List<String>,
    val wornAt: Instant,
)

data class OutfitFeedback(
    val outfitId: String,
    val liked: Boolean,
    val reason: String? = null,
    val createdAt: Instant,
)

/** Event analytics — KHÔNG chứa ảnh hay thông tin cá nhân. */
data class MetricEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val at: Instant = Instant.now(),
)

object MetricNames {
    const val ITEM_ADDED = "item_added"
    const val TAG_CORRECTED = "tag_corrected"
    const val OUTFIT_REQUESTED = "outfit_requested"
    const val OUTFIT_SHOWN = "outfit_shown"
    const val OUTFIT_WORN = "outfit_worn"
    const val OUTFIT_FEEDBACK = "outfit_feedback"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
}

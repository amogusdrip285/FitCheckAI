package com.fitcheckai.core.model

enum class Occasion { SCHOOL, WORK, DATE, PARTY, SPORT, CASUAL, FORMAL_EVENT }

enum class OutfitSource { AI, RULES }

data class Outfit(
    val id: String,
    val itemIds: List<String>,
    val explanation: String,
    val alternativesHint: String? = null,
    val source: OutfitSource,
)

/** Đầu vào cho bộ phối đồ (AI hoặc rule-based). */
data class OutfitRequest(
    val items: List<ClothingItem>,
    val weather: WeatherSnapshot?,
    val occasion: Occasion,
    val profile: StyleProfile,
    val avoidItemIds: Set<String> = emptySet(),
    val focusItemId: String? = null,
)

/** Kết quả trả cho màn hình "Hôm nay". */
data class OutfitSuggestions(
    val outfits: List<Outfit>,
    val weather: WeatherSnapshot?,
    /** Lý do không có gợi ý (vd. tủ đồ thiếu giày) — null nếu có outfit. */
    val emptyReason: String? = null,
)

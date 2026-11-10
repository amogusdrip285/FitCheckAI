package com.fitcheckai.core.stylist

import com.fitcheckai.core.model.AiOutfitGenerator
import com.fitcheckai.core.model.Occasion
import com.fitcheckai.core.model.OutfitRequest
import com.fitcheckai.core.model.OutfitService
import com.fitcheckai.core.model.OutfitSuggestions
import com.fitcheckai.core.model.ProfileRepository
import com.fitcheckai.core.model.StyleProfile
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.WeatherProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPRINT 0 STUB — chỉ gọi AI, KHÔNG có fallback.
 * TODO(Track C): ResilientOutfitService = AI (timeout) → RuleBasedStylist khi lỗi/rỗng.
 */
@Singleton
class StubOutfitService @Inject constructor(
    private val wardrobe: WardrobeRepository,
    private val weatherProvider: WeatherProvider,
    private val profiles: ProfileRepository,
    private val ai: AiOutfitGenerator,
) : OutfitService {

    override suspend fun suggest(occasion: Occasion, focusItemId: String?): OutfitSuggestions {
        val weather = weatherProvider.current().getOrNull()
        val request = OutfitRequest(
            items = wardrobe.observeItems().first(),
            weather = weather,
            occasion = occasion,
            profile = profiles.observe().first() ?: StyleProfile(),
            focusItemId = focusItemId,
        )
        return ai.generate(request).fold(
            onSuccess = { OutfitSuggestions(outfits = it, weather = weather) },
            onFailure = { OutfitSuggestions(outfits = emptyList(), weather = weather, emptyReason = it.message) },
        )
    }
}

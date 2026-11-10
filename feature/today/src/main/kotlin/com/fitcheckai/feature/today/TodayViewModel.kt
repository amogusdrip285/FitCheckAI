package com.fitcheckai.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.MetricEvent
import com.fitcheckai.core.model.MetricNames
import com.fitcheckai.core.model.MetricsTracker
import com.fitcheckai.core.model.Occasion
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.OutfitService
import com.fitcheckai.core.model.OutfitSuggestions
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.WearLogRepository
import com.fitcheckai.core.model.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val occasion: Occasion = Occasion.CASUAL,
    val isLoading: Boolean = false,
    val suggestions: OutfitSuggestions? = null,
    val itemsById: Map<String, ClothingItem> = emptyMap(),
    val wornOutfitId: String? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val outfitService: OutfitService,
    private val wardrobe: WardrobeRepository,
    private val wearLog: WearLogRepository,
    private val metrics: MetricsTracker,
) : ViewModel() {
    private val focusItemId: String? = savedStateHandle[Routes.ARG_FOCUS_ITEM_ID]
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        suggest()
    }

    fun onOccasionSelected(occasion: Occasion) {
        _state.update { it.copy(occasion = occasion) }
        suggest()
    }

    fun suggest() {
        val occasion = _state.value.occasion
        _state.update { it.copy(isLoading = true, wornOutfitId = null) }
        metrics.track(MetricEvent(MetricNames.OUTFIT_REQUESTED, mapOf("occasion" to occasion.name)))
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val suggestions = outfitService.suggest(occasion, focusItemId)
            val items = wardrobe.observeItems().first().associateBy { it.id }
            metrics.track(
                MetricEvent(
                    MetricNames.OUTFIT_SHOWN,
                    mapOf(
                        "latencyMs" to (System.currentTimeMillis() - startedAt).toString(),
                        "count" to suggestions.outfits.size.toString(),
                        "source" to (suggestions.outfits.firstOrNull()?.source?.name ?: "NONE"),
                    ),
                ),
            )
            _state.update { it.copy(isLoading = false, suggestions = suggestions, itemsById = items) }
        }
    }

    fun onWear(outfit: Outfit) {
        viewModelScope.launch {
            wearLog.logWear(outfit).onSuccess {
                metrics.track(MetricEvent(MetricNames.OUTFIT_WORN, mapOf("source" to outfit.source.name)))
                _state.update { it.copy(wornOutfitId = outfit.id) }
            }
        }
    }
}

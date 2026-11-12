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
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.WardrobeRepository as VersionedWardrobeRepository
import com.fitcheckai.core.model.wardrobe.ClothingItem as VersionedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

private data class TodayProposal(
    val ui: TodayUiState = TodayUiState(),
    val revisions: Map<String, Long> = emptyMap(),
) {
    fun isCurrent(outfit: Outfit, current: Map<String, Long>): Boolean =
        outfit.itemIds.isNotEmpty() && outfit.itemIds.all { id ->
            id in ui.itemsById && current[id] != null && current[id] == revisions[id]
        }

    fun visibleTo(load: LoadState<List<VersionedItem>>): TodayUiState {
        val suggestions = ui.suggestions ?: return ui
        val current = if (load is LoadState.Ready) load.value.associate { it.id to it.revision } else emptyMap()
        val valid = suggestions.outfits.filter { isCurrent(it, current) }
        if (valid.size == suggestions.outfits.size) return ui
        val visibleItemIds = valid.flatMap { it.itemIds }.toSet()
        return ui.copy(
            suggestions = suggestions.copy(
                outfits = valid,
                emptyReason = if (valid.isEmpty()) "Tủ đồ đã thay đổi. Hãy phối lại với các món hiện tại." else null,
            ),
            itemsById = ui.itemsById.filterKeys(visibleItemIds::contains),
            wornOutfitId = ui.wornOutfitId?.takeIf { id -> valid.any { it.id == id } },
        )
    }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val outfitService: OutfitService,
    private val wardrobe: WardrobeRepository,
    private val versionedWardrobe: VersionedWardrobeRepository,
    private val wearLog: WearLogRepository,
    private val metrics: MetricsTracker,
) : ViewModel() {
    private val focusItemId: String? = savedStateHandle[Routes.ARG_FOCUS_ITEM_ID]
    private val proposal = MutableStateFlow(TodayProposal())
    private var suggestionJob: Job? = null
    val state: StateFlow<TodayUiState> = combine(proposal, versionedWardrobe.observeItems()) { current, items ->
        current.visibleTo(items)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUiState(isLoading = true))

    init {
        suggest()
    }

    fun onOccasionSelected(occasion: Occasion) {
        proposal.update { it.copy(ui = it.ui.copy(occasion = occasion)) }
        suggest()
    }

    fun suggest() {
        val occasion = proposal.value.ui.occasion
        suggestionJob?.cancel()
        proposal.value = TodayProposal(TodayUiState(occasion = occasion, isLoading = true))
        metrics.track(MetricEvent(MetricNames.OUTFIT_REQUESTED, mapOf("occasion" to occasion.name)))
        suggestionJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                // The existing service returns v1 IDs. Pin their v2 revisions before starting it.
                val revisions = versionedWardrobe.getSnapshot().getOrThrow().items.associate { it.id to it.revision }
                val suggestions = outfitService.suggest(occasion, focusItemId)
                val items = wardrobe.observeItems().first().associateBy { it.id }
                val latest = versionedWardrobe.getSnapshot().getOrThrow()
                val result = TodayProposal(TodayUiState(occasion, suggestions = suggestions, itemsById = items), revisions)
                val visible = result.visibleTo(LoadState.Ready(latest.items))
                ensureActive()
                proposal.value = result.copy(ui = visible)
                val shown = visible.suggestions?.outfits.orEmpty()
                metrics.track(MetricEvent(MetricNames.OUTFIT_SHOWN, mapOf(
                    "latencyMs" to (System.currentTimeMillis() - startedAt).toString(),
                    "count" to shown.size.toString(),
                    "source" to (shown.firstOrNull()?.source?.name ?: "NONE"),
                )))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                proposal.value = TodayProposal(TodayUiState(occasion, suggestions = OutfitSuggestions(
                    emptyList(), null, "Chưa tải được gợi ý. Hãy thử lại.",
                )))
            }
        }
    }

    fun onWear(outfit: Outfit) {
        viewModelScope.launch {
            val selected = proposal.value
            if (outfit !in selected.ui.suggestions?.outfits.orEmpty()) return@launch
            val snapshot = versionedWardrobe.getSnapshot().getOrNull() ?: return@launch
            if (proposal.value !== selected || !selected.isCurrent(outfit, snapshot.items.associate { it.id to it.revision })) return@launch
            wearLog.logWear(outfit).onSuccess {
                metrics.track(MetricEvent(MetricNames.OUTFIT_WORN, mapOf("source" to outfit.source.name)))
                proposal.update { if (it === selected) it.copy(ui = it.ui.copy(wornOutfitId = outfit.id)) else it }
            }
        }
    }
}

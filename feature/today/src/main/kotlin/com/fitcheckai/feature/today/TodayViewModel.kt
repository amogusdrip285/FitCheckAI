package com.fitcheckai.feature.today

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.ai.fallback.BasicOutfitFallback
import com.fitcheckai.core.ai.proxy.rerollAfter
import com.fitcheckai.core.imaging.OutfitShare
import com.fitcheckai.core.model.MetricEvent
import com.fitcheckai.core.model.MetricNames
import com.fitcheckai.core.model.MetricsTracker
import com.fitcheckai.core.model.Outfit as LegacyOutfit
import com.fitcheckai.core.model.OutfitSource as LegacySource
import com.fitcheckai.core.model.WearLogRepository
import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.MAX_FREE_TEXT
import com.fitcheckai.core.model.recommendation.MAX_OUTFITS
import com.fitcheckai.core.model.recommendation.MAX_REQUEST_ITEMS
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.Outfit
import com.fitcheckai.core.model.recommendation.OutfitRecommendation
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.RecommendationResult
import com.fitcheckai.core.model.recommendation.ShortfallReason
import com.fitcheckai.core.model.tryon.TryOnSelection
import com.fitcheckai.core.model.tryon.TryOnSelectionHandoff
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingItem
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.WardrobeRepository
import com.fitcheckai.core.model.wardrobe.WardrobeSnapshot
import com.fitcheckai.core.model.wardrobe.wardrobeResult
import com.fitcheckai.core.ui.component.OutfitVisualData
import com.fitcheckai.core.ui.component.resolveOutfitItems
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

data class TodayUiState(
    val occasion: Occasion = Occasion.CASUAL,
    val isLoading: Boolean = true,
    val outfits: List<OutfitVisualData> = emptyList(),
    val message: String? = null,
    val wornOutfitId: String? = null,
    val styleOverrides: List<String> = emptyList(),
    val freeText: String = "",
    val requestedCount: Int = MAX_OUTFITS,
)

/** C-001 request inputs. Editing them never calls the AI; only [TodayViewModel.applyPreferences] does. */
private data class TodayPreferences(
    val styleOverrides: List<String> = emptyList(),
    val freeText: String = "",
    val requestedCount: Int = MAX_OUTFITS,
)

private const val MAX_STYLE_OVERRIDES = 5

private const val STALE_MESSAGE = "Tủ đồ đã thay đổi. Hãy phối lại với các món hiện tại."

private data class TodayProposal(
    val ui: TodayUiState = TodayUiState(),
    val request: OutfitRequest? = null,
    val result: RecommendationResult? = null,
) {
    fun visibleTo(load: LoadState<List<ClothingItem>>): TodayUiState {
        if (ui.outfits.isEmpty()) return ui
        val current = (load as? LoadState.Ready)?.value.orEmpty().associate { it.id to it.revision }
        val validIds = result?.outfits.orEmpty()
            .filter { outfit -> outfit.items.all { current[it.itemId] == it.revision } }
            .map { it.id }.toSet()
        val visible = ui.outfits.filter { it.outfitId in validIds }
        return ui.copy(
            outfits = visible,
            message = if (visible.isEmpty()) STALE_MESSAGE else ui.message,
            wornOutfitId = ui.wornOutfitId?.takeIf { it in validIds },
        )
    }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wardrobe: WardrobeRepository,
    private val profiles: ProfileStorage,
    private val media: MediaStore,
    private val recommendation: OutfitRecommendation,
    private val fallback: BasicOutfitFallback,
    private val share: OutfitShare,
    private val wearLog: WearLogRepository,
    private val metrics: MetricsTracker,
    private val tryOnHandoff: TryOnSelectionHandoff,
) : ViewModel() {
    private val focusItemId: String? = savedStateHandle[Routes.ARG_FOCUS_ITEM_ID]
    private val proposal = MutableStateFlow(TodayProposal())
    private val preferences = MutableStateFlow(TodayPreferences())
    private var suggestionJob: Job? = null
    private var wearJob: Job? = null

    val state: StateFlow<TodayUiState> = combine(proposal, wardrobe.observeItems(), preferences) { current, items, prefs ->
        current.visibleTo(items).copy(
            styleOverrides = prefs.styleOverrides,
            freeText = prefs.freeText,
            requestedCount = prefs.requestedCount,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUiState())

    init { suggest() }

    fun onOccasionSelected(occasion: Occasion) {
        if (occasion != proposal.value.ui.occasion) requestOutfits(occasion, reroll = false)
    }

    fun suggest() {
        if (suggestionJob?.isActive != true) requestOutfits(proposal.value.ui.occasion, reroll = false)
    }

    fun onStyleToggled(style: String) = preferences.update { prefs ->
        val styles = prefs.styleOverrides
        when {
            style in styles -> prefs.copy(styleOverrides = styles - style)
            styles.size >= MAX_STYLE_OVERRIDES || style.isBlank() -> prefs
            else -> prefs.copy(styleOverrides = styles + style)
        }
    }

    fun onFreeTextChanged(text: String) = preferences.update { it.copy(freeText = text.take(MAX_FREE_TEXT)) }

    fun onCountSelected(count: Int) = preferences.update { it.copy(requestedCount = count.coerceIn(1, MAX_OUTFITS)) }

    /** Explicit user action: replaces any in-flight request with one using the current inputs. */
    fun applyPreferences() = requestOutfits(proposal.value.ui.occasion, reroll = false)

    fun reroll() {
        if (suggestionJob?.isActive != true) requestOutfits(proposal.value.ui.occasion, reroll = true)
    }

    private fun requestOutfits(occasion: Occasion, reroll: Boolean) {
        val previous = proposal.value
        val prefs = preferences.value
        suggestionJob?.cancel()
        proposal.value = TodayProposal(TodayUiState(occasion))
        metrics.track(MetricEvent(MetricNames.OUTFIT_REQUESTED, mapOf("occasion" to occasion.name)))
        suggestionJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                val snapshot = wardrobe.getSnapshot().getOrThrow()
                val draft = buildRequest(snapshot, occasion, prefs)
                val previousRequest = previous.request
                val previousResult = previous.result
                val request = if (reroll && previousRequest != null && previousResult != null &&
                    recommendation.capability.supportsReroll &&
                    previousRequest.copy(requestId = draft.requestId, excludeOutfitSignatures = emptySet(),
                        regenerationOfRequestId = null) == draft
                ) previousRequest.rerollAfter(previousResult, draft.requestId) else draft
                val result = recommend(request)
                val latest = wardrobe.getSnapshot().getOrThrow()
                val visible = result.outfits.filter { it.items.isNotEmpty() && latest.isCurrent(it.items) }
                val cards = withContext(Dispatchers.IO) {
                    val ids = visible.flatMap { it.items }.map { it.itemId }.toSet()
                    val paths = latest.items.filter { it.id in ids }.flatMap { it.images.refs() }
                        .mapNotNull { ref -> media.displayPath(ref)?.let { ref.id to it } }.toMap()
                    visible.mapIndexed { index, outfit ->
                        OutfitVisualData(
                            outfit.id, "Bộ phối ${index + 1}", resolveOutfitItems(latest, outfit.items, paths),
                            outfit.source, outfit.explanation,
                        )
                    }
                }
                val ui = TodayUiState(
                    occasion, isLoading = false, outfits = cards,
                    message = if (cards.isEmpty()) STALE_MESSAGE else result.shortfallReason?.label(),
                )
                ensureActive()
                proposal.value = TodayProposal(ui, request, result.copy(outfits = visible))
                metrics.track(MetricEvent(MetricNames.OUTFIT_SHOWN, mapOf(
                    "latencyMs" to (System.currentTimeMillis() - startedAt).toString(),
                    "count" to cards.size.toString(),
                    "source" to (cards.firstOrNull()?.source?.name ?: "NONE"),
                )))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                proposal.value = TodayProposal(TodayUiState(
                    occasion, isLoading = false,
                    message = (error as? WardrobeException)?.message ?: "Chưa tải được gợi ý. Hãy thử lại.",
                ))
            }
        }
    }

    private suspend fun buildRequest(snapshot: WardrobeSnapshot, occasion: Occasion, prefs: TodayPreferences): OutfitRequest {
        val known = snapshot.items.filter { it.attributes.category != Category.UNKNOWN }
        val focus = focusItemId?.let { id -> known.find { it.id == id } ?: throw WardrobeException(
            ErrorCode.NEEDS_INPUT, "Món được chọn đã bị xóa hoặc chưa có loại đồ. Hãy chọn món khác.",
        ) }
        if (known.isEmpty()) throw WardrobeException(
            ErrorCode.INSUFFICIENT_WARDROBE, "Hãy thêm áo và quần/chân váy hoặc một đầm liền vào tủ đồ.",
        )
        // Keep the focus and at least one possible base outfit even in a large wardrobe.
        val essentials = listOfNotNull(focus) + listOf(Category.TOP, Category.BOTTOM, Category.DRESS).mapNotNull { category ->
            known.firstOrNull { it.attributes.category == category }
        }
        val items = (essentials + known).distinctBy { it.id }.take(MAX_REQUEST_ITEMS)
        val profile = profiles.observeProfile().first { it !is LoadState.Loading } as? LoadState.Ready
        return OutfitRequest(
            requestId = UUID.randomUUID().toString(),
            wardrobeSnapshotId = snapshot.snapshotId,
            items = items.map { ItemSummary(it.id, it.revision, it.attributes) },
            profile = profile?.value?.toProjection(),
            occasion = occasion,
            styleOverrides = prefs.styleOverrides,
            freeText = prefs.freeText.trim(),
            requestedCount = prefs.requestedCount,
            focusItemId = focus?.id,
        )
    }

    private suspend fun recommend(request: OutfitRequest): RecommendationResult =
        recommendation.recommend(request).getOrElse { error ->
            if ((error as? WardrobeException)?.code !in FALLBACK_ERRORS) throw error
            RecommendationResult(request.requestId, listOf(fallback.suggest(request).getOrThrow()))
        }

    fun onWear(outfitId: String) {
        if (wearJob?.isActive == true || proposal.value.ui.wornOutfitId == outfitId) return
        wearJob = viewModelScope.launch {
            val selected = proposal.value
            val outfit = selected.result?.outfits?.find { it.id == outfitId } ?: return@launch
            if (wardrobeResult { checkCurrent(selected, outfit) }.isFailure) return@launch
            val legacy = LegacyOutfit(
                outfit.id, outfit.items.map { it.itemId }, outfit.explanation,
                source = if (outfit.source == OutfitSource.AI_LIVE) LegacySource.AI else LegacySource.RULES,
            )
            wearLog.logWear(legacy).onSuccess {
                metrics.track(MetricEvent(MetricNames.OUTFIT_WORN, mapOf("source" to outfit.source.name)))
                proposal.update { if (it === selected) it.copy(ui = it.ui.copy(wornOutfitId = outfitId)) else it }
            }
        }
    }

    suspend fun prepareShare(outfitId: String): Result<Intent> = wardrobeResult {
        val selected = proposal.value
        val outfit = selected.result?.outfits?.find { it.id == outfitId } ?: throw stale()
        val snapshot = checkCurrent(selected, outfit)
        val photos = outfit.items.map { ref ->
            snapshot.items.first { it.id == ref.itemId }.images.front
                ?: throw WardrobeException(ErrorCode.IO, "Món đồ thiếu ảnh mặt trước.")
        }
        val intent = share.prepare("Bộ phối FitCheck", photos).getOrThrow()
        checkCurrent(selected, outfit)
        intent
    }

    /** C-001/CT-03: validates the picked outfit, hands it to Try-On and returns the route. Never starts a job. */
    suspend fun prepareTryOn(outfitId: String): Result<String> = wardrobeResult {
        val selected = proposal.value
        val outfit = selected.result?.outfits?.find { it.id == outfitId } ?: throw stale()
        val snapshot = checkCurrent(selected, outfit)
        val ids = outfit.items.map { it.itemId }.toSet()
        val items = snapshot.items.filter { it.id in ids }
        val paths = withContext(Dispatchers.IO) {
            items.flatMap { it.images.refs() }.mapNotNull { ref -> media.displayPath(ref)?.let { ref.id to it } }.toMap()
        }
        tryOnHandoff.publish(TryOnSelection(outfit, items, paths))
        Routes.tryOn(outfit.id, outfit.wardrobeSnapshotId)
    }

    private suspend fun checkCurrent(selected: TodayProposal, outfit: Outfit): WardrobeSnapshot {
        val latest = wardrobe.getSnapshot().getOrThrow()
        if (proposal.value.result !== selected.result || outfit.items.isEmpty() || !latest.isCurrent(outfit.items)) throw stale()
        return latest
    }

    private fun stale() = WardrobeException(ErrorCode.STALE_INPUT, STALE_MESSAGE)

    private companion object {
        val FALLBACK_ERRORS = setOf(
            ErrorCode.UNSUPPORTED_CAPABILITY, ErrorCode.NETWORK, ErrorCode.UPSTREAM_TIMEOUT, ErrorCode.UPSTREAM_ERROR,
            ErrorCode.RATE_LIMITED, ErrorCode.BUDGET_EXCEEDED, ErrorCode.INVALID_AI_OUTPUT, ErrorCode.UNAUTHORIZED,
        )
    }
}

private fun ShortfallReason.label(): String = when (this) {
    ShortfallReason.NOT_ENOUGH_DISTINCT_COMBINATIONS -> "Tủ đồ hiện chỉ có từng này bộ khác nhau."
    ShortfallReason.CONSTRAINTS_TOO_STRICT -> "Một số lựa chọn đang giới hạn số bộ phù hợp."
    ShortfallReason.INVALID_OUTFITS_REMOVED -> "Đã bỏ các bộ có món không hợp lệ."
}

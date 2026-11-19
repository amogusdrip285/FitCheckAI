package com.fitcheckai.feature.tryon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.Category
import com.fitcheckai.feature.tryon.contract.FeasibilityDecision
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnError
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnGarmentSlot
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnOutfitSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnPersonProfileSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.contract.TryOnProfilePort
import com.fitcheckai.feature.tryon.contract.TryOnRequest
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.VirtualTryOn
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class TryOnViewMode {
    FRONT_ONLY,
    BACK_ONLY,
    FRONT_AND_BACK,
}

enum class TryOnGarmentCombo {
    TOP_ONLY,
    BOTTOM_ONLY,
    TOP_AND_BOTTOM,
}

data class TryOnGarmentSlotUi(
    val slot: TryOnGarmentSlot,
    val titleVi: String,
    val itemSummary: TryOnItemSummary,
    val images: ImagePair,
) {
    val hasAnyPhoto: Boolean
        get() = images.front != null || images.back != null

    val hasFrontPhoto: Boolean
        get() = images.front != null

    val isBackPhotoOptional: Boolean
        get() = slot == TryOnGarmentSlot.HAT || slot == TryOnGarmentSlot.SHOES

    val hasDistinctBackPhoto: Boolean
        get() = images.back != null &&
            (images.front == null || images.front.contentHash != images.back.contentHash)
}

internal fun tryOnSlotTitleVi(slot: TryOnGarmentSlot): String = when (slot) {
    TryOnGarmentSlot.TOP -> "Áo"
    TryOnGarmentSlot.BOTTOM -> "Quần"
    TryOnGarmentSlot.HAT -> "Mũ"
    TryOnGarmentSlot.SHOES -> "Giày"
}

internal fun defaultSlotItemId(slot: TryOnGarmentSlot): String = when (slot) {
    TryOnGarmentSlot.TOP -> "tryon-slot-top"
    TryOnGarmentSlot.BOTTOM -> "tryon-slot-bottom"
    TryOnGarmentSlot.HAT -> "tryon-slot-hat"
    TryOnGarmentSlot.SHOES -> "tryon-slot-shoes"
}

private fun defaultSlotCategory(slot: TryOnGarmentSlot): Category = when (slot) {
    TryOnGarmentSlot.TOP -> Category.TOP
    TryOnGarmentSlot.BOTTOM -> Category.BOTTOM
    TryOnGarmentSlot.HAT -> Category.ACCESSORY
    TryOnGarmentSlot.SHOES -> Category.SHOES
}

internal fun buildCanonicalGarmentSlots(
    outfitSnapshot: TryOnOutfitSnapshot?,
    garmentImages: Map<String, ImagePair>,
): List<TryOnGarmentSlotUi> {
    val existingItems = outfitSnapshot?.items.orEmpty()
    return TryOnGarmentSlot.CANONICAL_ORDER.map { slot ->
        val matched = existingItems.firstOrNull { it.resolvedGarmentSlot() == slot }
        val summary = matched ?: TryOnItemSummary(
            itemId = defaultSlotItemId(slot),
            label = tryOnSlotTitleVi(slot),
            category = defaultSlotCategory(slot),
            slot = slot,
            revision = 1L,
        )
        TryOnGarmentSlotUi(
            slot = slot,
            titleVi = tryOnSlotTitleVi(slot),
            itemSummary = summary,
            images = garmentImages[summary.itemId] ?: ImagePair(),
        )
    }
}

internal fun derivePhotoDrivenSelectedItemIds(
    outfitSnapshot: TryOnOutfitSnapshot?,
    garmentImages: Map<String, ImagePair>,
    capability: TryOnCapability?,
): List<String> {
    val maxPerJob = capability?.maxGarmentsPerJob ?: TryOnGarmentSlot.CANONICAL_ORDER.size
    if (maxPerJob <= 0) return emptyList()
    return buildCanonicalGarmentSlots(outfitSnapshot, garmentImages)
        .filter { slotUi ->
            slotUi.hasAnyPhoto &&
                (capability == null || slotUi.itemSummary.category in capability.supportedCategories)
        }
        .take(maxPerJob)
        .map { it.itemSummary.itemId }
}

data class TryOnUiState(
    val isLoadingInitial: Boolean = true,
    val capability: TryOnCapability? = null,
    val outfitSnapshot: TryOnOutfitSnapshot? = null,
    val personProfile: TryOnPersonProfileSnapshot? = null,
    val garmentImages: Map<String, ImagePair> = emptyMap(),
    val selectedViewMode: TryOnViewMode = TryOnViewMode.FRONT_ONLY,
    val selectedPresentationMode: TryOnPresentationMode = TryOnPresentationMode.ORIGINAL_PHOTO,
    val selectedItemIds: List<String> = emptyList(),
    val consentConfirmed: Boolean = false,
    val isSubmitting: Boolean = false,
    val activeOrLatestResult: TryOnResult? = null,
    val activeResultTab: ImageView = ImageView.FRONT,
    val compareWithOriginal: Boolean = false,
    val bannerError: TryOnError? = null,
    val statusNotice: String? = null,
) {
    val isCapabilityAvailable: Boolean
        get() = capability?.isAvailableForTryOn == true

    val isFostaProvider: Boolean
        get() = capability?.providerId == "fosta" || activeOrLatestResult?.providerId == "fosta"

    val isFakeEnvironment: Boolean
        get() = activeOrLatestResult?.source == TryOnSource.FAKE ||
            capability?.providerId?.startsWith("fake") == true ||
            capability?.executionMode == "FAKE" ||
            capability?.executionMode == "OFFLINE_MOCK_OPENAI" ||
            capability?.executionMode == "OFFLINE_MOCK_FOSTA"

    val canSelectStudioWhite: Boolean
        get() = TryOnPresentationMode.STUDIO_WHITE in capability?.supportedPresentationModes.orEmpty()

    val garmentSlots: List<TryOnGarmentSlotUi>
        get() = buildCanonicalGarmentSlots(outfitSnapshot, garmentImages)

    val activeSlots: List<TryOnGarmentSlotUi>
        get() {
            val selectedSet = selectedItemIds.toSet()
            return garmentSlots.filter { it.itemSummary.itemId in selectedSet }
        }

    val tryOnSummaryText: String
        get() = if (activeSlots.isEmpty()) {
            "Chọn ít nhất một món"
        } else {
            "Sẽ thử: ${activeSlots.joinToString(" + ") { it.titleVi }}"
        }

    val hasShoesImage: Boolean
        get() = garmentSlots.any { it.slot == TryOnGarmentSlot.SHOES && it.hasAnyPhoto }

    val supportedOutfitItems: List<TryOnItemSummary>
        get() {
            val cap = capability ?: return emptyList()
            return outfitSnapshot?.items
                ?.filter { it.category in cap.supportedCategories }
                .orEmpty()
        }

    val availableTopItem: TryOnItemSummary?
        get() = garmentSlots.firstOrNull { it.slot == TryOnGarmentSlot.TOP }?.itemSummary

    val availableBottomItem: TryOnItemSummary?
        get() = garmentSlots.firstOrNull { it.slot == TryOnGarmentSlot.BOTTOM }?.itemSummary

    val canChooseGarmentCombos: Boolean
        get() = (capability?.maxGarmentsPerJob ?: 1) >= 2 &&
            availableTopItem != null &&
            availableBottomItem != null

    val activeGarmentCombo: TryOnGarmentCombo?
        get() {
            val active = activeSlots
            if (active.isEmpty()) return null
            val slots = active.map { it.slot }.toSet()
            return when (slots) {
                setOf(TryOnGarmentSlot.TOP) -> TryOnGarmentCombo.TOP_ONLY
                setOf(TryOnGarmentSlot.BOTTOM) -> TryOnGarmentCombo.BOTTOM_ONLY
                setOf(TryOnGarmentSlot.TOP, TryOnGarmentSlot.BOTTOM) -> TryOnGarmentCombo.TOP_AND_BOTTOM
                else -> null
            }
        }

    val excludedOutfitItems: List<TryOnItemSummary>
        get() {
            val selectedSet = selectedItemIds.toSet()
            return outfitSnapshot?.items
                ?.filterNot { it.itemId in selectedSet }
                .orEmpty()
        }

    val isSubsetSimulation: Boolean
        get() {
            val total = outfitSnapshot?.items?.size ?: 0
            return total > 0 && selectedItemIds.isNotEmpty() && selectedItemIds.size < total
        }

    val hasPersonFront: Boolean
        get() = personProfile?.bodyImages?.front != null

    val hasPersonBack: Boolean
        get() = personProfile?.bodyImages?.back != null

    val hasAllSelectedGarmentFronts: Boolean
        get() = selectedItemIds.isNotEmpty() &&
            activeSlots.isNotEmpty() &&
            activeSlots.all { it.hasFrontPhoto }

    /** Required rear photos: only non-optional slots (TOP/BOTTOM) must supply a rear photo; HAT/SHOES rear is optional. */
    val hasAllSelectedGarmentBacks: Boolean
        get() = selectedItemIds.isNotEmpty() &&
            activeSlots.isNotEmpty() &&
            activeSlots.filterNot { it.isBackPhotoOptional }.all { it.images.back != null } &&
            activeSlots.filter { it.isBackPhotoOptional }.all { it.images.back != null || it.images.front != null }

    val hasGenuineBackHashes: Boolean
        get() {
            val pFront = personProfile?.bodyImages?.front
            val pBack = personProfile?.bodyImages?.back
            if (pFront != null && pBack != null && pFront.contentHash == pBack.contentHash) {
                return false
            }
            for (slotUi in activeSlots) {
                val pair = slotUi.images
                if (
                    pair.front != null &&
                    pair.back != null &&
                    pair.front.contentHash == pair.back.contentHash
                ) {
                    return false
                }
            }
            return true
        }

    val missingFrontSlotLabels: List<String>
        get() = activeSlots.filterNot { it.hasFrontPhoto }.map { it.titleVi }

    val missingBackSlotLabels: List<String>
        get() = buildList {
            val pFront = personProfile?.bodyImages?.front
            val pBack = personProfile?.bodyImages?.back
            if (pBack == null || (pFront != null && pFront.contentHash == pBack.contentHash)) {
                add("Người (mặt sau)")
            }
            activeSlots
                .filterNot { it.isBackPhotoOptional }
                .filterNot { it.hasDistinctBackPhoto }
                .forEach { add(it.titleVi) }
        }

    val inferredBackSlotLabels: List<String>
        get() = activeSlots
            .filter { it.isBackPhotoOptional && it.images.back == null && it.images.front != null }
            .map { it.titleVi }

    val optionalBackInferenceNotice: String?
        get() = if (ImageView.BACK in requestedViews && inferredBackSlotLabels.isNotEmpty()) {
            "Mũ/Giày chưa có ảnh sau: AI sẽ tự suy ra mặt sau, có thể không giống mẫu thật."
        } else {
            null
        }

    val viewMissingPhotosError: String?
        get() {
            if (!isCapabilityAvailable || selectedItemIds.isEmpty()) return null
            if (ImageView.FRONT in requestedViews) {
                val clauses = buildList {
                    if (!hasPersonFront) add("ảnh người mặt trước")
                    val missingFront = missingFrontSlotLabels
                    if (missingFront.isNotEmpty()) add("ảnh trước của ${missingFront.joinToString(", ")}")
                }
                if (clauses.isNotEmpty()) {
                    return "Không thể tạo mặt trước: thiếu ${clauses.joinToString(" và ")}."
                }
            }
            if (ImageView.BACK in requestedViews) {
                val clauses = buildList {
                    if (!hasPersonBack) add("ảnh người mặt sau")
                    val missingReqBacks = activeSlots
                        .filterNot { it.isBackPhotoOptional }
                        .filter { it.images.back == null }
                        .map { it.titleVi }
                    if (missingReqBacks.isNotEmpty()) {
                        add("ảnh sau của ${missingReqBacks.joinToString(", ")}")
                    }
                    val missingOptAny = activeSlots
                        .filter { it.isBackPhotoOptional && it.images.back == null && it.images.front == null }
                        .map { it.titleVi }
                    if (missingOptAny.isNotEmpty()) {
                        add("ảnh của ${missingOptAny.joinToString(", ")}")
                    }
                }
                if (clauses.isNotEmpty()) {
                    return "Không thể tạo mặt sau: thiếu ${clauses.joinToString(" và ")}."
                }
                if (!hasGenuineBackHashes) {
                    return "Không thể tạo mặt sau: ảnh mặt sau không được trùng với ảnh mặt trước."
                }
            }
            return null
        }

    val consentedGarmentPhotoCount: Int
        get() = activeSlots.sumOf { slotUi ->
            val uniqueRefs = buildSet {
                requestedViews.forEach { view ->
                    when (view) {
                        ImageView.FRONT -> slotUi.images.front?.id?.let(::add)
                        ImageView.BACK -> {
                            val ref = slotUi.images.back
                                ?: if (slotUi.isBackPhotoOptional) slotUi.images.front else null
                            ref?.id?.let(::add)
                        }
                    }
                }
            }
            uniqueRefs.size
        }

    val backViewUnavailableReason: String?
        get() {
            val cap = capability ?: return "Đang kiểm tra khả năng mô phỏng."
            if (cap.decision == FeasibilityDecision.GO_FRONT_ONLY || ImageView.BACK !in cap.supportedViews) {
                return "Mô hình hiện chỉ hỗ trợ mô phỏng mặt trước (FRONT_ONLY)."
            }
            if (selectedItemIds.isEmpty()) {
                return "Chọn ít nhất một món trang phục để mở góc nhìn mặt sau."
            }
            if (!hasPersonBack) {
                return "Chưa có ảnh toàn thân mặt sau trong Hồ sơ cá nhân."
            }
            if (!hasAllSelectedGarmentBacks) {
                return "Một số món đồ đã chọn chưa có ảnh mặt sau trong Tủ đồ."
            }
            if (!hasGenuineBackHashes) {
                return "Ảnh mặt sau không được trùng khớp (nhân bản) với ảnh mặt trước."
            }
            return null
        }

    val canSelectBackView: Boolean
        get() = backViewUnavailableReason == null

    val dualViewUnavailableReason: String?
        get() {
            backViewUnavailableReason?.let { return it }
            if (!hasPersonFront) {
                return "Chưa có ảnh toàn thân mặt trước trong Hồ sơ cá nhân."
            }
            if (!hasAllSelectedGarmentFronts) {
                return "Một số món đồ đã chọn chưa có ảnh mặt trước trong Tủ đồ."
            }
            return null
        }

    val canSelectDualView: Boolean
        get() = !isFostaProvider && dualViewUnavailableReason == null

    val requestedViews: Set<ImageView>
        get() = when (selectedViewMode) {
            TryOnViewMode.FRONT_ONLY -> setOf(ImageView.FRONT)
            TryOnViewMode.BACK_ONLY -> setOf(ImageView.BACK)
            TryOnViewMode.FRONT_AND_BACK -> setOf(ImageView.FRONT, ImageView.BACK)
        }

    val hasExperimentalSelection: Boolean
        get() = isFostaProvider && (
            ImageView.BACK in requestedViews ||
                activeSlots.size >= 2 ||
                activeSlots.any { it.slot != TryOnGarmentSlot.TOP }
            )

    val isJobActive: Boolean
        get() {
            val status = activeOrLatestResult?.status ?: return false
            return status == TryOnJobStatus.QUEUED || status == TryOnJobStatus.RUNNING
        }

    val canSubmitTryOn: Boolean
        get() {
            if (!isCapabilityAvailable || isSubmitting || isJobActive) return false
            if (!consentConfirmed || selectedItemIds.isEmpty()) return false
            if (viewMissingPhotosError != null) return false
            if (ImageView.FRONT in requestedViews && (!hasPersonFront || !hasAllSelectedGarmentFronts)) {
                return false
            }
            if (ImageView.BACK in requestedViews && !canSelectBackView) return false
            if (selectedViewMode == TryOnViewMode.FRONT_AND_BACK && !canSelectDualView) return false
            return true
        }
}

/**
 * OWNER: Track C (`C-209` — ViewModel managing single-outfit Virtual Try-On state,
 * per-request consent, rotation-safe job observation, and honest partial/error states).
 *
 * Strictly NEVER auto-submits a VTO job on initialization or configuration change.
 */
class TryOnViewModel(
    private val tryOnService: VirtualTryOn,
    private val profilePort: TryOnProfilePort,
    private val privacyCoordinator: TryOnPrivacyCoordinator,
    initialOutfit: TryOnOutfitSnapshot?,
    initialGarmentImages: Map<String, ImagePair>,
    private val sessionId: String = "android-local-session",
    resumedJobId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TryOnUiState(
            isLoadingInitial = true,
            outfitSnapshot = initialOutfit,
            garmentImages = initialGarmentImages,
        ),
    )
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    private var activeObserverJob: Job? = null
    private var pendingIdempotencyKey: String = "idem-${idGenerator()}"

    init {
        initializeState(resumedJobId)
    }

    private fun initializeState(resumedJobId: String?) {
        viewModelScope.launch {
            val capability = tryOnService.capabilities().getOrNull()
            _uiState.update { cur ->
                val defaultSelected = computeDefaultSelectedItems(cur.outfitSnapshot, capability, cur.garmentImages)
                val normalizedMode = if (
                    cur.selectedPresentationMode != TryOnPresentationMode.ORIGINAL_PHOTO &&
                    cur.selectedPresentationMode !in capability?.supportedPresentationModes.orEmpty()
                ) {
                    TryOnPresentationMode.ORIGINAL_PHOTO
                } else {
                    cur.selectedPresentationMode
                }
                cur.copy(
                    isLoadingInitial = false,
                    capability = capability,
                    selectedItemIds = defaultSelected,
                    selectedPresentationMode = normalizedMode,
                )
            }
            if (resumedJobId != null) {
                attachJobObserver(resumedJobId)
            }
        }

        viewModelScope.launch {
            profilePort.observePersonProfile().collect { snapshot ->
                var outdatedJobIdToDelete: String? = null
                var shouldCancelObserver = false
                var rotatedIdempotencyKey: String? = null
                _uiState.update { cur ->
                    outdatedJobIdToDelete = null
                    shouldCancelObserver = false
                    val previousProfile = cur.personProfile
                    if (previousProfile == null) {
                        return@update normalizeViewAfterSelectionChange(cur.copy(personProfile = snapshot))
                    }

                    val bodyImagesChanged = previousProfile.bodyImages != snapshot?.bodyImages
                    if (!bodyImagesChanged) {
                        return@update cur.copy(personProfile = snapshot)
                    }
                    if (cur.isJobActive || cur.isSubmitting) {
                        return@update cur
                    }

                    val frontChanged = previousProfile.bodyImages.front != snapshot?.bodyImages?.front
                    val backChanged = previousProfile.bodyImages.back != snapshot?.bodyImages?.back
                    if (rotatedIdempotencyKey == null) {
                        rotatedIdempotencyKey = "idem-${idGenerator()}"
                    }

                    val previousResult = cur.activeOrLatestResult
                    val affectsPreviousResult = previousResult != null && (
                        previousResult.views.isEmpty() ||
                            (frontChanged && ImageView.FRONT in previousResult.views) ||
                            (backChanged && ImageView.BACK in previousResult.views)
                        )
                    if (affectsPreviousResult) {
                        shouldCancelObserver = true
                        outdatedJobIdToDelete = previousResult!!.jobId
                    }

                    val updated = cur.copy(
                        personProfile = snapshot,
                        consentConfirmed = false,
                        activeOrLatestResult = if (affectsPreviousResult) null else cur.activeOrLatestResult,
                        compareWithOriginal = if (affectsPreviousResult) false else cur.compareWithOriginal,
                        bannerError = null,
                        statusNotice = null,
                    )
                    normalizeViewAfterSelectionChange(updated)
                }
                rotatedIdempotencyKey?.let { pendingIdempotencyKey = it }
                if (shouldCancelObserver) {
                    activeObserverJob?.cancel()
                }
                outdatedJobIdToDelete?.let { jobId ->
                    tryOnService.delete(jobId)
                }
            }
        }
    }

    fun selectViewMode(mode: TryOnViewMode) {
        _uiState.update { cur ->
            if (cur.isJobActive || cur.isSubmitting) return@update cur
            if (mode == TryOnViewMode.BACK_ONLY && !cur.canSelectBackView) {
                return@update cur
            }
            if (mode == TryOnViewMode.FRONT_AND_BACK && !cur.canSelectDualView) {
                return@update cur
            }
            if (cur.selectedViewMode == mode) return@update cur
            pendingIdempotencyKey = "idem-${idGenerator()}"
            // Changing scope of uploaded views requires explicit fresh user consent
            cur.copy(
                selectedViewMode = mode,
                consentConfirmed = false,
                bannerError = null,
                statusNotice = null,
            )
        }
    }

    fun selectPresentationMode(mode: TryOnPresentationMode) {
        _uiState.update { cur ->
            if (cur.isJobActive || cur.isSubmitting) return@update cur
            if (
                mode != TryOnPresentationMode.ORIGINAL_PHOTO &&
                mode !in cur.capability?.supportedPresentationModes.orEmpty()
            ) {
                return@update cur
            }
            if (cur.selectedPresentationMode == mode) return@update cur
            pendingIdempotencyKey = "idem-${idGenerator()}"
            cur.copy(
                selectedPresentationMode = mode,
                consentConfirmed = false,
                bannerError = null,
                statusNotice = null,
            )
        }
    }

    fun selectGarmentCombo(combo: TryOnGarmentCombo) {
        _uiState.update { cur ->
            if (cur.isJobActive || cur.isSubmitting) return@update cur
            val cap = cur.capability ?: return@update cur
            val topId = cur.availableTopItem?.itemId
            val bottomId = cur.availableBottomItem?.itemId
            val nextIds = when (combo) {
                TryOnGarmentCombo.TOP_ONLY -> listOfNotNull(topId)
                TryOnGarmentCombo.BOTTOM_ONLY -> listOfNotNull(bottomId)
                TryOnGarmentCombo.TOP_AND_BOTTOM -> {
                    if (topId != null && bottomId != null) listOf(topId, bottomId) else emptyList()
                }
            }
            if (nextIds.isEmpty() || nextIds.size > cap.maxGarmentsPerJob) return@update cur
            if (nextIds == cur.selectedItemIds) return@update cur
            pendingIdempotencyKey = "idem-${idGenerator()}"
            normalizeViewAfterSelectionChange(
                cur.copy(
                    selectedItemIds = nextIds,
                    consentConfirmed = false,
                    bannerError = null,
                    statusNotice = null,
                ),
            )
        }
    }

    fun toggleSelectedItem(itemId: String) {
        _uiState.update { cur ->
            if (cur.isJobActive || cur.isSubmitting) return@update cur
            val cap = cur.capability ?: return@update cur
            val itemsById = cur.outfitSnapshot?.items?.associateBy { it.itemId }.orEmpty()
            val item = itemsById[itemId] ?: return@update cur
            if (item.category !in cap.supportedCategories) return@update cur

            val currentIds = cur.selectedItemIds
            val nextIds = if (itemId in currentIds) {
                if (currentIds.size <= 1) currentIds else currentIds - itemId
            } else if (cap.maxGarmentsPerJob == 1) {
                listOf(itemId)
            } else if (cap.providerId == "fosta") {
                val targetIsTop = item.category == Category.TOP || item.category == Category.OUTERWEAR
                val filtered = currentIds.filterNot { existingId ->
                    val existingCat = itemsById[existingId]?.category
                    val existingIsTop = existingCat == Category.TOP || existingCat == Category.OUTERWEAR
                    existingIsTop == targetIsTop
                }
                (filtered + itemId).take(cap.maxGarmentsPerJob)
            } else if (currentIds.size < cap.maxGarmentsPerJob) {
                currentIds + itemId
            } else {
                currentIds
            }
            if (nextIds == currentIds) return@update cur
            pendingIdempotencyKey = "idem-${idGenerator()}"
            normalizeViewAfterSelectionChange(
                cur.copy(
                    selectedItemIds = nextIds,
                    consentConfirmed = false,
                    bannerError = null,
                ),
            )
        }
    }

    /**
     * Updates garment reference photos in place without recreating the ViewModel.
     * Selection ("có ảnh thì ghép") is derived automatically in canonical order [TOP, BOTTOM, HAT, SHOES].
     * Resets stale consent and purges any prior result when active slots or their photos change.
     */
    fun updateGarmentImages(updatedImages: Map<String, ImagePair>) {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting || snapshot.isJobActive) return

        val allItemIds = snapshot.garmentImages.keys + updatedImages.keys
        val changedItemIds = allItemIds.filter { itemId ->
            snapshot.garmentImages[itemId] != updatedImages[itemId]
        }
        if (changedItemIds.isEmpty()) return

        val nextSelectedIds = derivePhotoDrivenSelectedItemIds(
            outfitSnapshot = snapshot.outfitSnapshot,
            garmentImages = updatedImages,
            capability = snapshot.capability,
        )
        val selectionChanged = nextSelectedIds != snapshot.selectedItemIds
        val previousResult = snapshot.activeOrLatestResult
        val affectsPreviousResult = previousResult != null && (
            selectionChanged ||
                previousResult.appliedItemIds.isEmpty() ||
                changedItemIds.any { it in previousResult.appliedItemIds } ||
                previousResult.appliedItemIds.any { it !in nextSelectedIds }
            )
        val affectsCurrentSelection =
            selectionChanged || changedItemIds.any { it in snapshot.selectedItemIds || it in nextSelectedIds }

        if (affectsPreviousResult) {
            activeObserverJob?.cancel()
        }

        val previewState = normalizeViewAfterSelectionChange(
            snapshot.copy(
                garmentImages = updatedImages,
                selectedItemIds = nextSelectedIds,
            ),
        )
        val viewFallbackTriggered = previewState.selectedViewMode != snapshot.selectedViewMode
        val shouldResetConsent =
            affectsCurrentSelection || affectsPreviousResult || viewFallbackTriggered

        if (shouldResetConsent) {
            pendingIdempotencyKey = "idem-${idGenerator()}"
        }

        _uiState.update { cur ->
            val recomputedSelectedIds = derivePhotoDrivenSelectedItemIds(
                outfitSnapshot = cur.outfitSnapshot,
                garmentImages = updatedImages,
                capability = cur.capability,
            )
            val updated = cur.copy(
                garmentImages = updatedImages,
                selectedItemIds = recomputedSelectedIds,
                consentConfirmed = if (shouldResetConsent) false else cur.consentConfirmed,
                activeOrLatestResult = if (affectsPreviousResult) null else cur.activeOrLatestResult,
                compareWithOriginal = if (affectsPreviousResult) false else cur.compareWithOriginal,
                bannerError = null,
                statusNotice = null,
            )
            val normalized = normalizeViewAfterSelectionChange(updated)
            if (normalized.selectedViewMode != cur.selectedViewMode) {
                normalized.copy(consentConfirmed = false)
            } else {
                normalized
            }
        }

        if (affectsPreviousResult) {
            viewModelScope.launch {
                tryOnService.delete(previousResult.jobId)
            }
        }
    }

    /**
     * Clears both front and back photos for [slot], removes it from the current try-on selection,
     * resets user consent, and invalidates/deletes any prior try-on result.
     */
    fun removeGarmentSlot(slot: TryOnGarmentSlot) {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting || snapshot.isJobActive) return
        val slotUi = snapshot.garmentSlots.firstOrNull { it.slot == slot } ?: return
        val itemId = slotUi.itemSummary.itemId
        val updatedImages = snapshot.garmentImages - itemId
        val nextSelectedIds = derivePhotoDrivenSelectedItemIds(
            outfitSnapshot = snapshot.outfitSnapshot,
            garmentImages = updatedImages,
            capability = snapshot.capability,
        )
        val previousResult = snapshot.activeOrLatestResult
        val affectsPreviousResult = previousResult != null && (
            slotUi.hasAnyPhoto ||
                previousResult.appliedItemIds.isEmpty() ||
                itemId in previousResult.appliedItemIds ||
                previousResult.appliedItemIds.any { it !in nextSelectedIds }
            )

        if (!slotUi.hasAnyPhoto && itemId !in snapshot.selectedItemIds && previousResult == null) {
            return
        }

        if (affectsPreviousResult) {
            activeObserverJob?.cancel()
        }
        pendingIdempotencyKey = "idem-${idGenerator()}"

        _uiState.update { cur ->
            val recomputedSelectedIds = derivePhotoDrivenSelectedItemIds(
                outfitSnapshot = cur.outfitSnapshot,
                garmentImages = updatedImages,
                capability = cur.capability,
            )
            val updated = cur.copy(
                garmentImages = updatedImages,
                selectedItemIds = recomputedSelectedIds,
                consentConfirmed = false,
                activeOrLatestResult = if (affectsPreviousResult) null else cur.activeOrLatestResult,
                compareWithOriginal = if (affectsPreviousResult) false else cur.compareWithOriginal,
                bannerError = null,
                statusNotice = null,
            )
            normalizeViewAfterSelectionChange(updated)
        }

        if (affectsPreviousResult) {
            viewModelScope.launch {
                tryOnService.delete(previousResult.jobId)
            }
        }
    }

    /**
     * Clears only a single view ([ImageView.FRONT] or [ImageView.BACK]) of [slot] without silently
     * switching the selected view mode, so missing-photo validation surfaces clearly.
     */
    fun removeGarmentPhoto(slot: TryOnGarmentSlot, view: ImageView) {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting || snapshot.isJobActive) return
        val slotUi = snapshot.garmentSlots.firstOrNull { it.slot == slot } ?: return
        val itemId = slotUi.itemSummary.itemId
        val existingPair = snapshot.garmentImages[itemId] ?: ImagePair()
        val removedRef = existingPair.refFor(view) ?: return
        val nextPair = when (view) {
            ImageView.FRONT -> existingPair.copy(front = null)
            ImageView.BACK -> existingPair.copy(back = null)
        }
        val updatedImages = snapshot.garmentImages.toMutableMap().apply {
            if (nextPair.front == null && nextPair.back == null) {
                remove(itemId)
            } else {
                put(itemId, nextPair)
            }
        }
        val nextSelectedIds = derivePhotoDrivenSelectedItemIds(
            outfitSnapshot = snapshot.outfitSnapshot,
            garmentImages = updatedImages,
            capability = snapshot.capability,
        )
        val previousResult = snapshot.activeOrLatestResult
        val affectsPreviousResult = previousResult != null && (
            previousResult.appliedItemIds.isEmpty() ||
                itemId in previousResult.appliedItemIds ||
                previousResult.appliedItemIds.any { it !in nextSelectedIds } ||
                removedRef.id.isNotBlank()
            )
        if (affectsPreviousResult) {
            activeObserverJob?.cancel()
        }
        pendingIdempotencyKey = "idem-${idGenerator()}"

        _uiState.update { cur ->
            val recomputedSelectedIds = derivePhotoDrivenSelectedItemIds(
                outfitSnapshot = cur.outfitSnapshot,
                garmentImages = updatedImages,
                capability = cur.capability,
            )
            cur.copy(
                garmentImages = updatedImages,
                selectedItemIds = recomputedSelectedIds,
                consentConfirmed = false,
                activeOrLatestResult = if (affectsPreviousResult) null else cur.activeOrLatestResult,
                compareWithOriginal = if (affectsPreviousResult) false else cur.compareWithOriginal,
                bannerError = null,
                statusNotice = null,
            )
        }

        if (affectsPreviousResult) {
            viewModelScope.launch {
                tryOnService.delete(previousResult.jobId)
            }
        }
    }

    /** Preserve user-chosen view mode even if photos change so missing-photo error is shown transparently instead of silently flipping to FRONT. */
    private fun normalizeViewAfterSelectionChange(nextState: TryOnUiState): TryOnUiState = nextState

    fun setConsentConfirmed(confirmed: Boolean) {
        _uiState.update { cur ->
            if (cur.isJobActive || cur.isSubmitting) return@update cur
            cur.copy(consentConfirmed = confirmed, bannerError = null)
        }
    }

    fun selectResultTab(view: ImageView) {
        _uiState.update { cur ->
            if (cur.activeOrLatestResult?.views?.containsKey(view) == true) {
                cur.copy(activeResultTab = view)
            } else {
                cur
            }
        }
    }

    fun setCompareWithOriginal(compare: Boolean) {
        _uiState.update { it.copy(compareWithOriginal = compare) }
    }

    fun submitTryOn() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting || snapshot.isJobActive) return

        val capability = snapshot.capability
        val outfit = snapshot.outfitSnapshot
        val profile = snapshot.personProfile
        if (capability == null || !capability.isAvailableForTryOn) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                        message = "Tính năng thử đồ trực tiếp hiện chưa được mở.",
                    ),
                )
            }
            return
        }
        if (outfit == null) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Chưa có bộ trang phục nào được chọn để thử đồ.",
                    ),
                )
            }
            return
        }
        if (profile == null) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Chưa có dữ liệu Hồ sơ cá nhân để thử đồ.",
                    ),
                )
            }
            return
        }
        if (snapshot.selectedItemIds.isEmpty()) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Chọn ít nhất một món trang phục để thử đồ.",
                    ),
                )
            }
            return
        }
        val views = snapshot.requestedViews
        snapshot.viewMissingPhotosError?.let { viewErr ->
            val errCode = if (ImageView.BACK in views && !snapshot.hasGenuineBackHashes) {
                TryOnErrorCode.INVALID_REQUEST
            } else {
                TryOnErrorCode.NEEDS_INPUT
            }
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = errCode,
                        message = viewErr,
                    ),
                )
            }
            return
        }
        if (ImageView.FRONT in views && !snapshot.hasPersonFront) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Không thể tạo mặt trước: thiếu ảnh người mặt trước.",
                    ),
                )
            }
            return
        }
        if (ImageView.FRONT in views && !snapshot.hasAllSelectedGarmentFronts) {
            val missing = snapshot.missingFrontSlotLabels.joinToString(", ")
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = if (missing.isNotEmpty()) {
                            "Không thể tạo mặt trước: thiếu ảnh trước của $missing."
                        } else {
                            "Không thể tạo mặt trước: thiếu ảnh mặt trước cho các món trang phục đã chọn."
                        },
                    ),
                )
            }
            return
        }
        if (ImageView.BACK in views && !snapshot.hasPersonBack) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Không thể tạo mặt sau: thiếu ảnh người mặt sau.",
                    ),
                )
            }
            return
        }
        if (ImageView.BACK in views && !snapshot.hasAllSelectedGarmentBacks) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Một số món trang phục đã chọn chưa có ảnh mặt sau thật sự cho góc nhìn BACK.",
                    ),
                )
            }
            return
        }
        if (ImageView.BACK in views && !snapshot.hasGenuineBackHashes) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.INVALID_REQUEST,
                        message = "Không được dùng lại ảnh mặt trước làm ảnh mặt sau khi mô phỏng BACK.",
                    ),
                )
            }
            return
        }
        if (!snapshot.consentConfirmed) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.CONSENT_REQUIRED,
                        message = "Vui lòng xác nhận đồng ý gửi ảnh cho lượt thử đồ này trước khi tạo mô phỏng.",
                    ),
                )
            }
            return
        }

        val canonicalSelectedIds = snapshot.activeSlots.map { it.itemSummary.itemId }
        val existingItemIds = outfit.items.map { it.itemId }.toSet()
        val extraSlotItems = snapshot.activeSlots
            .map { it.itemSummary }
            .filter { it.itemId !in existingItemIds }
        val effectiveOutfit = if (extraSlotItems.isEmpty()) {
            outfit
        } else {
            outfit.copy(items = outfit.items + extraSlotItems)
        }

        val activeSlotsById = snapshot.activeSlots.associateBy { it.itemSummary.itemId }
        val selectedMediaIds = buildSet {
            views.forEach { v ->
                profile.bodyImages.refFor(v)?.id?.let(::add)
                canonicalSelectedIds.forEach { itemId ->
                    val pair = snapshot.garmentImages[itemId]
                    val slotUi = activeSlotsById[itemId]
                    val ref = when (v) {
                        ImageView.FRONT -> pair?.front
                        ImageView.BACK -> pair?.back
                            ?: if (slotUi?.isBackPhotoOptional == true) pair?.front else null
                    }
                    ref?.id?.let(::add)
                }
            }
        }

        val consentGrant = privacyCoordinator.createPerRequestConsentGrant(
            capability = capability,
            selectedMediaIds = selectedMediaIds,
            allowedViews = views,
            confirmedByUser = true,
            grantId = "consent-$pendingIdempotencyKey",
        )

        val request = TryOnRequest(
            requestId = "req-$pendingIdempotencyKey",
            idempotencyKey = pendingIdempotencyKey,
            sessionId = sessionId,
            outfitSnapshot = effectiveOutfit,
            profileRevision = profile.revision,
            personImages = profile.bodyImages,
            garmentImages = snapshot.garmentImages.filterKeys { it in canonicalSelectedIds },
            requestedViews = views,
            presentationMode = snapshot.selectedPresentationMode,
            selectedItemIds = canonicalSelectedIds,
            consentGrant = consentGrant,
            inputGeneration = privacyCoordinator.currentInputGeneration,
            providerConfigVersion = capability.providerConfigVersion,
        )

        _uiState.update {
            it.copy(
                isSubmitting = true,
                bannerError = null,
                statusNotice = null,
                compareWithOriginal = false,
            )
        }

        viewModelScope.launch {
            val result = tryOnService.submit(request)
            result.fold(
                onSuccess = { handle ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            activeResultTab = if (ImageView.FRONT in views) ImageView.FRONT else views.first(),
                        )
                    }
                    attachJobObserver(handle.jobId)
                },
                onFailure = { throwable ->
                    val err = (throwable as? TryOnException)?.tryOnError ?: TryOnError(
                        code = TryOnErrorCode.UPSTREAM_ERROR,
                        message = throwable.message ?: "Không thể gửi yêu cầu thử đồ.",
                    )
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            bannerError = err,
                        )
                    }
                },
            )
        }
    }

    private fun attachJobObserver(jobId: String) {
        activeObserverJob?.cancel()
        activeObserverJob = viewModelScope.launch {
            tryOnService.observe(jobId).collect { result ->
                _uiState.update { cur ->
                    val preferredTab = when {
                        cur.activeResultTab in result.views.keys -> cur.activeResultTab
                        ImageView.FRONT in result.views.keys -> ImageView.FRONT
                        else -> result.views.keys.firstOrNull() ?: ImageView.FRONT
                    }
                    cur.copy(
                        activeOrLatestResult = result,
                        activeResultTab = preferredTab,
                    )
                }
            }
        }
    }

    fun cancelActiveJob() {
        val jobId = _uiState.value.activeOrLatestResult?.jobId ?: return
        viewModelScope.launch {
            tryOnService.cancel(jobId).onSuccess { receipt ->
                pendingIdempotencyKey = "idem-${idGenerator()}"
                _uiState.update {
                    it.copy(
                        consentConfirmed = false,
                        statusNotice = receipt.message,
                    )
                }
            }
        }
    }

    fun deleteLatestResult() {
        val jobId = _uiState.value.activeOrLatestResult?.jobId ?: return
        activeObserverJob?.cancel()
        viewModelScope.launch {
            tryOnService.delete(jobId).onSuccess { receipt ->
                pendingIdempotencyKey = "idem-${idGenerator()}"
                _uiState.update {
                    it.copy(
                        activeOrLatestResult = null,
                        consentConfirmed = false,
                        compareWithOriginal = false,
                        statusNotice = receipt.message,
                    )
                }
            }
        }
    }

    fun prepareFreshAttempt() {
        val cur = _uiState.value
        if (cur.isJobActive) return
        activeObserverJob?.cancel()
        pendingIdempotencyKey = "idem-${idGenerator()}"
        _uiState.update {
            it.copy(
                activeOrLatestResult = null,
                consentConfirmed = false,
                compareWithOriginal = false,
                bannerError = null,
                statusNotice = null,
            )
        }
    }

    private fun computeDefaultSelectedItems(
        outfit: TryOnOutfitSnapshot?,
        capability: TryOnCapability?,
        garmentImages: Map<String, ImagePair> = _uiState.value.garmentImages,
    ): List<String> = derivePhotoDrivenSelectedItemIds(
        outfitSnapshot = outfit,
        garmentImages = garmentImages,
        capability = capability,
    )
}

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

    val supportedOutfitItems: List<TryOnItemSummary>
        get() {
            val cap = capability ?: return emptyList()
            return outfitSnapshot?.items
                ?.filter { it.category in cap.supportedCategories }
                .orEmpty()
        }

    val availableTopItem: TryOnItemSummary?
        get() = supportedOutfitItems.firstOrNull {
            it.category == Category.TOP || it.category == Category.OUTERWEAR
        }

    val availableBottomItem: TryOnItemSummary?
        get() = supportedOutfitItems.firstOrNull { it.category == Category.BOTTOM }

    val canChooseGarmentCombos: Boolean
        get() = (capability?.maxGarmentsPerJob ?: 1) >= 2 &&
            availableTopItem != null &&
            availableBottomItem != null

    val activeGarmentCombo: TryOnGarmentCombo?
        get() {
            val byId = outfitSnapshot?.items?.associateBy { it.itemId }.orEmpty()
            val selectedItems = selectedItemIds.mapNotNull { byId[it] }
            if (selectedItems.isEmpty()) return null
            val topCount = selectedItems.count {
                it.category == Category.TOP || it.category == Category.OUTERWEAR
            }
            val bottomCount = selectedItems.count { it.category == Category.BOTTOM }
            return when {
                topCount == 1 && bottomCount == 1 && selectedItems.size == 2 ->
                    TryOnGarmentCombo.TOP_AND_BOTTOM
                topCount == 1 && bottomCount == 0 && selectedItems.size == 1 ->
                    TryOnGarmentCombo.TOP_ONLY
                topCount == 0 && bottomCount == 1 && selectedItems.size == 1 ->
                    TryOnGarmentCombo.BOTTOM_ONLY
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
            selectedItemIds.all { garmentImages[it]?.front != null }

    val hasAllSelectedGarmentBacks: Boolean
        get() = selectedItemIds.isNotEmpty() &&
            selectedItemIds.all { garmentImages[it]?.back != null }

    val hasGenuineBackHashes: Boolean
        get() {
            val pFront = personProfile?.bodyImages?.front
            val pBack = personProfile?.bodyImages?.back
            if (pFront != null && pBack != null && pFront.contentHash == pBack.contentHash) {
                return false
            }
            for (itemId in selectedItemIds) {
                val pair = garmentImages[itemId]
                if (
                    pair?.front != null &&
                    pair.back != null &&
                    pair.front.contentHash == pair.back.contentHash
                ) {
                    return false
                }
            }
            return true
        }

    val backViewUnavailableReason: String?
        get() {
            val cap = capability ?: return "Đang kiểm tra khả năng mô phỏng."
            if (cap.decision == FeasibilityDecision.GO_FRONT_ONLY || ImageView.BACK !in cap.supportedViews) {
                return "Mô hình hiện chỉ hỗ trợ mô phỏng mặt trước (FRONT_ONLY)."
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
                activeGarmentCombo == TryOnGarmentCombo.BOTTOM_ONLY ||
                activeGarmentCombo == TryOnGarmentCombo.TOP_AND_BOTTOM
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
                val defaultSelected = computeDefaultSelectedItems(cur.outfitSnapshot, capability)
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
                _uiState.update { cur ->
                    val nextState = cur.copy(personProfile = snapshot)
                    // If user removed back photo while BACK_ONLY or FRONT_AND_BACK was selected (and no job running),
                    // fall back cleanly to FRONT_ONLY and require fresh consent confirmation.
                    val backInvalidated =
                        (nextState.selectedViewMode == TryOnViewMode.FRONT_AND_BACK && !nextState.canSelectDualView) ||
                            (nextState.selectedViewMode == TryOnViewMode.BACK_ONLY && !nextState.canSelectBackView)
                    if (backInvalidated && !nextState.isJobActive) {
                        nextState.copy(
                            selectedViewMode = TryOnViewMode.FRONT_ONLY,
                            consentConfirmed = false,
                        )
                    } else {
                        nextState
                    }
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

    private fun normalizeViewAfterSelectionChange(nextState: TryOnUiState): TryOnUiState {
        val backInvalidated =
            (nextState.selectedViewMode == TryOnViewMode.BACK_ONLY && !nextState.canSelectBackView) ||
                (nextState.selectedViewMode == TryOnViewMode.FRONT_AND_BACK && !nextState.canSelectDualView)
        return if (backInvalidated) {
            nextState.copy(selectedViewMode = TryOnViewMode.FRONT_ONLY)
        } else {
            nextState
        }
    }

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
        val views = snapshot.requestedViews
        if (ImageView.FRONT in views && !snapshot.hasPersonFront) {
            _uiState.update {
                it.copy(
                    bannerError = TryOnError(
                        code = TryOnErrorCode.NEEDS_INPUT,
                        message = "Cần bổ sung ảnh toàn thân mặt trước trong Hồ sơ trước khi thử đồ.",
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
                        message = "Cần bổ sung ảnh toàn thân mặt sau thật sự trong Hồ sơ trước khi thử góc nhìn BACK.",
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

        val selectedMediaIds = buildSet {
            views.forEach { v ->
                profile.bodyImages.refFor(v)?.id?.let(::add)
                snapshot.selectedItemIds.forEach { itemId ->
                    snapshot.garmentImages[itemId]?.refFor(v)?.id?.let(::add)
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
            outfitSnapshot = outfit,
            profileRevision = profile.revision,
            personImages = profile.bodyImages,
            garmentImages = snapshot.garmentImages.filterKeys { it in snapshot.selectedItemIds },
            requestedViews = views,
            presentationMode = snapshot.selectedPresentationMode,
            selectedItemIds = snapshot.selectedItemIds,
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
    ): List<String> {
        if (outfit == null || capability == null || capability.maxGarmentsPerJob <= 0) {
            return emptyList()
        }
        val supported = outfit.items.filter { it.category in capability.supportedCategories }
        if (capability.providerId == "fosta") {
            val defaultPrimary = supported.firstOrNull {
                it.category == Category.TOP || it.category == Category.OUTERWEAR
            } ?: supported.firstOrNull()
            return listOfNotNull(defaultPrimary?.itemId)
        }
        return supported
            .take(capability.maxGarmentsPerJob)
            .map { it.itemId }
    }
}

package com.fitcheckai.feature.tryon.fake

import com.fitcheckai.core.model.Category
import com.fitcheckai.feature.tryon.contract.ConsentGrant
import com.fitcheckai.feature.tryon.contract.ConsentPurpose
import com.fitcheckai.feature.tryon.contract.FeasibilityDecision
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.RemoteDeletionState
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnError
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import com.fitcheckai.feature.tryon.contract.TryOnOutfitSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnPersonProfileSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.contract.TryOnProfilePort
import com.fitcheckai.feature.tryon.contract.TryOnRequest
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.ViewProcessState
import com.fitcheckai.feature.tryon.contract.ViewResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

/**
 * In-memory `TryOnMediaStorePort` for offline fake development, Compose previews & unit tests.
 * Never writes to public gallery or external network.
 */
class FakeInMemoryMediaStore(
    initialEntries: Map<String, Pair<MediaRef, ByteArray>> = emptyMap(),
    var customOutputPathProvider: ((jobId: String, view: ImageView, counter: Int, bytes: ByteArray) -> String?)? = null,
) : TryOnMediaStorePort {
    private val store = LinkedHashMap<String, Pair<MediaRef, ByteArray>>(initialEntries)
    val failRemoveMediaIds = mutableSetOf<String>()
    var readCallCount: Int = 0
        private set
    var savedOutputCounter: Int = 0
        private set

    @Synchronized
    fun putMedia(ref: MediaRef, bytes: ByteArray = ByteArray(ref.byteCount.toInt().coerceAtLeast(16)) { 0x2A }) {
        store[ref.id] = ref to bytes
    }

    @Synchronized
    fun hasMedia(mediaId: String): Boolean = store.containsKey(mediaId)

    override suspend fun readBytes(ref: MediaRef): Result<ByteArray> = synchronized(store) {
        readCallCount += 1
        val entry = store[ref.id]
            ?: return Result.failure(IOException("Missing media ref ${ref.id}"))
        Result.success(entry.second.copyOf())
    }

    override suspend fun prepareUploadDerivative(ref: MediaRef, maxBytes: Long): Result<MediaRef> = synchronized(store) {
        val entry = store[ref.id]
            ?: return Result.failure(IOException("Missing source media ref ${ref.id}"))
        if (entry.second.size.toLong() <= maxBytes) {
            return Result.success(ref)
        }
        // Simulate compressed upload derivative tracked separately from original
        val derivedBytes = entry.second.copyOf(maxBytes.toInt())
        val derivedRef = ref.copy(
            id = "${ref.id}-deriv",
            localRelativePath = "tryon/derivatives/${ref.id}-deriv.jpg",
            byteCount = derivedBytes.size.toLong(),
            contentHash = "${ref.contentHash}:deriv",
            derivedFromMediaId = ref.id,
        )
        store[derivedRef.id] = derivedRef to derivedBytes
        Result.success(derivedRef)
    }

    override suspend fun saveTryOnOutput(
        jobId: String,
        view: ImageView,
        mimeType: String,
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): Result<MediaRef> = synchronized(store) {
        savedOutputCounter += 1
        val id = "tryon-out-$jobId-${view.name.lowercase()}-$savedOutputCounter"
        val customPath = customOutputPathProvider?.invoke(jobId, view, savedOutputCounter, bytes)
        val ref = MediaRef(
            id = id,
            ownerEntityId = jobId,
            scope = MediaScope.TRYON_RESULT,
            view = view,
            localRelativePath = customPath ?: "seed://tryon_result/$id.png",
            mimeType = mimeType,
            width = width,
            height = height,
            byteCount = bytes.size.toLong(),
            contentHash = "sha256:fake-out-$id",
            revision = 1L,
        )
        store[id] = ref to bytes.copyOf()
        Result.success(ref)
    }

    override suspend fun remove(ref: MediaRef): Result<Unit> = synchronized(store) {
        if (ref.id in failRemoveMediaIds) {
            return Result.failure(IOException("Simulated remove failure for ${ref.id}"))
        }
        store.remove(ref.id)
        Result.success(Unit)
    }
}

class FakeTryOnProfilePort(
    initial: TryOnPersonProfileSnapshot? = FakeTryOnFixtures.personDualSnapshot,
) : TryOnProfilePort {
    private val state = MutableStateFlow(initial)
    fun update(snapshot: TryOnPersonProfileSnapshot?) {
        state.value = snapshot
    }
    override fun observePersonProfile(): Flow<TryOnPersonProfileSnapshot?> = state
}

object FakeTryOnFixtures {
    const val PROVIDER_ID = "fake-tryon-v2"
    const val CONFIG_VERSION = "v2-poc-1"

    val supportedCategoriesDefault = setOf(
        Category.TOP,
        Category.BOTTOM,
        Category.DRESS,
        Category.OUTERWEAR,
    )

    val coreUnknownCapability = TryOnCapability(
        decision = FeasibilityDecision.UNKNOWN,
        supportedViews = emptySet(),
        supportedCategories = supportedCategoriesDefault,
        maxGarmentsPerJob = 0,
        supportsNativePairedViews = false,
        runtimeVerifiedViews = emptySet(),
        providerId = PROVIDER_ID,
        providerConfigVersion = CONFIG_VERSION,
    )

    val noGoCapability = TryOnCapability(
        decision = FeasibilityDecision.NO_GO,
        supportedViews = emptySet(),
        supportedCategories = emptySet(),
        maxGarmentsPerJob = 0,
        supportsNativePairedViews = false,
        runtimeVerifiedViews = emptySet(),
        providerId = PROVIDER_ID,
        providerConfigVersion = CONFIG_VERSION,
    )

    val frontOnlyCapability = TryOnCapability(
        decision = FeasibilityDecision.GO_FRONT_ONLY,
        supportedViews = setOf(ImageView.FRONT),
        supportedCategories = supportedCategoriesDefault,
        maxGarmentsPerJob = 2,
        supportsNativePairedViews = false,
        runtimeVerifiedViews = setOf(ImageView.FRONT),
        providerId = PROVIDER_ID,
        providerConfigVersion = CONFIG_VERSION,
    )

    val dualCapability = TryOnCapability(
        decision = FeasibilityDecision.GO_DUAL,
        supportedViews = setOf(ImageView.FRONT, ImageView.BACK),
        supportedCategories = supportedCategoriesDefault,
        maxGarmentsPerJob = 2,
        supportsNativePairedViews = false,
        runtimeVerifiedViews = emptySet(), // Honest: paired runtime not live-verified until C-211/C-212
        providerId = PROVIDER_ID,
        providerConfigVersion = CONFIG_VERSION,
        supportedPresentationModes = setOf(
            TryOnPresentationMode.ORIGINAL_PHOTO,
            TryOnPresentationMode.STUDIO_WHITE,
        ),
    )

    val fostaCapability = TryOnCapability(
        decision = FeasibilityDecision.GO_DUAL,
        supportedViews = setOf(ImageView.FRONT, ImageView.BACK),
        supportedCategories = setOf(Category.TOP, Category.BOTTOM),
        maxGarmentsPerJob = 2,
        supportsNativePairedViews = false,
        runtimeVerifiedViews = emptySet(),
        providerId = "fosta",
        providerConfigVersion = "v2-fosta-poc-1",
        supportedPresentationModes = setOf(
            TryOnPresentationMode.ORIGINAL_PHOTO,
            TryOnPresentationMode.STUDIO_WHITE,
        ),
        executionMode = "OFFLINE_MOCK_FOSTA",
        modelId = "gpt-6-luna",
    )

    val singleGarmentCapability = dualCapability.copy(maxGarmentsPerJob = 1)

    val personFrontRef = MediaRef(
        id = "dev-person-front-1",
        ownerEntityId = "profile-dev-1",
        scope = MediaScope.PERSON,
        view = ImageView.FRONT,
        localRelativePath = "seed://dev-silhouette-front",
        mimeType = "image/jpeg",
        width = 768,
        height = 1024,
        byteCount = 65_536L,
        contentHash = "sha256:dev-person-front-v1",
        revision = 1L,
    )

    val personBackRef = MediaRef(
        id = "dev-person-back-1",
        ownerEntityId = "profile-dev-1",
        scope = MediaScope.PERSON,
        view = ImageView.BACK,
        localRelativePath = "seed://dev-silhouette-back",
        mimeType = "image/jpeg",
        width = 768,
        height = 1024,
        byteCount = 64_000L,
        contentHash = "sha256:dev-person-back-v1",
        revision = 1L,
    )

    val personDualSnapshot = TryOnPersonProfileSnapshot(
        profileId = "profile-dev-1",
        revision = 2L,
        bodyImages = ImagePair(front = personFrontRef, back = personBackRef),
    )

    val personFrontOnlySnapshot = TryOnPersonProfileSnapshot(
        profileId = "profile-dev-1",
        revision = 2L,
        bodyImages = ImagePair(front = personFrontRef, back = null),
    )

    val personEmptySnapshot = TryOnPersonProfileSnapshot(
        profileId = "profile-dev-1",
        revision = 1L,
        bodyImages = ImagePair(front = null, back = null),
    )

    val topFrontRef = MediaRef(
        id = "dev-top-front-1",
        ownerEntityId = "item-top-linen",
        scope = MediaScope.GARMENT,
        view = ImageView.FRONT,
        localRelativePath = "seed://dev-top-linen-front",
        mimeType = "image/png",
        width = 768,
        height = 1024,
        byteCount = 48_000L,
        contentHash = "sha256:dev-top-front",
        revision = 1L,
    )

    val topBackRef = MediaRef(
        id = "dev-top-back-1",
        ownerEntityId = "item-top-linen",
        scope = MediaScope.GARMENT,
        view = ImageView.BACK,
        localRelativePath = "seed://dev-top-linen-back",
        mimeType = "image/png",
        width = 768,
        height = 1024,
        byteCount = 47_500L,
        contentHash = "sha256:dev-top-back",
        revision = 1L,
    )

    val bottomFrontRef = MediaRef(
        id = "dev-bottom-front-1",
        ownerEntityId = "item-trouser-noir",
        scope = MediaScope.GARMENT,
        view = ImageView.FRONT,
        localRelativePath = "seed://dev-trouser-front",
        mimeType = "image/png",
        width = 768,
        height = 1024,
        byteCount = 52_000L,
        contentHash = "sha256:dev-bottom-front",
        revision = 1L,
    )

    val bottomBackRef = MediaRef(
        id = "dev-bottom-back-1",
        ownerEntityId = "item-trouser-noir",
        scope = MediaScope.GARMENT,
        view = ImageView.BACK,
        localRelativePath = "seed://dev-trouser-back",
        mimeType = "image/png",
        width = 768,
        height = 1024,
        byteCount = 51_200L,
        contentHash = "sha256:dev-bottom-back",
        revision = 1L,
    )

    val shoesFrontRef = MediaRef(
        id = "dev-shoes-front-1",
        ownerEntityId = "item-shoes-loafer",
        scope = MediaScope.GARMENT,
        view = ImageView.FRONT,
        localRelativePath = "seed://dev-shoes-front",
        mimeType = "image/png",
        width = 512,
        height = 512,
        byteCount = 32_000L,
        contentHash = "sha256:dev-shoes-front",
        revision = 1L,
    )

    val editorialWorkOutfit = TryOnOutfitSnapshot(
        outfitId = "outfit-editorial-01",
        wardrobeSnapshotId = "snap-2026-11",
        explanation = "Sơ mi linen kem phối quần âu ống suông đen tạo phom thanh lịch, thoáng nhẹ cho ngày đi làm.",
        items = listOf(
            TryOnItemSummary(
                itemId = "item-top-linen",
                label = "Áo sơ mi linen kem",
                category = Category.TOP,
                colors = listOf("BEIGE", "WHITE"),
                accentHex = "#E6DCCF",
                revision = 1L,
            ),
            TryOnItemSummary(
                itemId = "item-trouser-noir",
                label = "Quần âu ống suông đen",
                category = Category.BOTTOM,
                colors = listOf("BLACK"),
                accentHex = "#252321",
                revision = 1L,
            ),
            TryOnItemSummary(
                itemId = "item-shoes-loafer",
                label = "Giày loafer da đen",
                category = Category.SHOES,
                colors = listOf("BLACK"),
                accentHex = "#3A3532",
                revision = 1L,
            ),
        ),
    )

    val defaultGarmentImages: Map<String, ImagePair> = mapOf(
        "item-top-linen" to ImagePair(front = topFrontRef, back = topBackRef),
        "item-trouser-noir" to ImagePair(front = bottomFrontRef, back = bottomBackRef),
        "item-shoes-loafer" to ImagePair(front = shoesFrontRef, back = null),
    )

    val fakeResultFrontRef = MediaRef(
        id = "fake-out-front-preview",
        ownerEntityId = "job-preview-1",
        scope = MediaScope.TRYON_RESULT,
        view = ImageView.FRONT,
        localRelativePath = "seed://fake-result-front",
        mimeType = "image/png",
        width = 768,
        height = 1024,
        byteCount = 98_304L,
        contentHash = "sha256:fake-result-front-preview",
        revision = 1L,
    )

    val fakeResultBackRef = MediaRef(
        id = "fake-out-back-preview",
        ownerEntityId = "job-preview-1",
        scope = MediaScope.TRYON_RESULT,
        view = ImageView.BACK,
        localRelativePath = "seed://fake-result-back",
        mimeType = "image/png",
        width = 768,
        height = 1024,
        byteCount = 96_120L,
        contentHash = "sha256:fake-result-back-preview",
        revision = 1L,
    )

    fun createPopulatedMediaStore(): FakeInMemoryMediaStore {
        val store = FakeInMemoryMediaStore()
        listOf(
            personFrontRef,
            personBackRef,
            topFrontRef,
            topBackRef,
            bottomFrontRef,
            bottomBackRef,
            shoesFrontRef,
            fakeResultFrontRef,
            fakeResultBackRef,
        ).forEach { store.putMedia(it) }
        return store
    }

    fun sampleDualRequest(
        idempotencyKey: String = "idem-sample-dual",
        requestedViews: Set<ImageView> = setOf(ImageView.FRONT, ImageView.BACK),
        selectedItemIds: List<String> = listOf("item-top-linen", "item-trouser-noir"),
        confirmedConsent: Boolean = true,
        inputGeneration: Long = 1L,
    ): TryOnRequest {
        val mediaIds = buildSet {
            if (ImageView.FRONT in requestedViews) {
                add(personFrontRef.id)
                selectedItemIds.forEach { id -> defaultGarmentImages[id]?.front?.id?.let(::add) }
            }
            if (ImageView.BACK in requestedViews) {
                add(personBackRef.id)
                selectedItemIds.forEach { id -> defaultGarmentImages[id]?.back?.id?.let(::add) }
            }
        }
        return TryOnRequest(
            requestId = "req-$idempotencyKey",
            idempotencyKey = idempotencyKey,
            sessionId = "session-dev-1",
            outfitSnapshot = editorialWorkOutfit,
            profileRevision = personDualSnapshot.revision,
            personImages = personDualSnapshot.bodyImages,
            garmentImages = defaultGarmentImages,
            requestedViews = requestedViews,
            selectedItemIds = selectedItemIds,
            consentGrant = ConsentGrant(
                id = "consent-$idempotencyKey",
                purpose = ConsentPurpose.TRYON,
                providerId = PROVIDER_ID,
                providerConfigVersion = CONFIG_VERSION,
                selectedMediaIds = mediaIds,
                allowedViews = requestedViews,
                requestGeneration = inputGeneration,
                confirmedByUser = confirmedConsent,
                issuedAtEpochMillis = 1_700_000_000_000L,
            ),
            inputGeneration = inputGeneration,
            providerConfigVersion = CONFIG_VERSION,
        )
    }

    fun previewResult(
        status: TryOnJobStatus,
        includeBack: Boolean = true,
        backFailed: Boolean = false,
        qualityReview: QualityReviewState = QualityReviewState.UNREVIEWED,
    ): TryOnResult {
        val views = buildMap {
            put(
                ImageView.FRONT,
                ViewResult(
                    view = ImageView.FRONT,
                    state = when (status) {
                        TryOnJobStatus.QUEUED -> ViewProcessState.QUEUED
                        TryOnJobStatus.RUNNING -> ViewProcessState.RUNNING
                        TryOnJobStatus.FAILED, TryOnJobStatus.TIMED_OUT -> ViewProcessState.FAILED
                        TryOnJobStatus.CANCELLED -> ViewProcessState.CANCELLED
                        else -> ViewProcessState.SUCCEEDED
                    },
                    outputRef = if (status == TryOnJobStatus.SUCCEEDED || status == TryOnJobStatus.PARTIAL) {
                        fakeResultFrontRef
                    } else {
                        null
                    },
                    error = if (status == TryOnJobStatus.FAILED || status == TryOnJobStatus.TIMED_OUT) {
                        TryOnError(
                            code = TryOnErrorCode.UPSTREAM_TIMEOUT,
                            message = "Quá thời gian xử lý kỹ thuật (120 giây). Nhà cung cấp có thể vẫn đang kết thúc tiến trình.",
                            retryable = true,
                        )
                    } else {
                        null
                    },
                    qualityReview = qualityReview,
                ),
            )
            if (includeBack) {
                put(
                    ImageView.BACK,
                    if (backFailed) {
                        ViewResult(
                            view = ImageView.BACK,
                            state = ViewProcessState.FAILED,
                            outputRef = null,
                            error = TryOnError(
                                code = TryOnErrorCode.INVALID_AI_OUTPUT,
                                message = "Không thể tái tạo chi tiết mặt sau nhất quán từ ảnh đầu vào.",
                                retryable = false,
                            ),
                            qualityReview = QualityReviewState.UNREVIEWED,
                        )
                    } else {
                        ViewResult(
                            view = ImageView.BACK,
                            state = when (status) {
                                TryOnJobStatus.QUEUED -> ViewProcessState.QUEUED
                                TryOnJobStatus.RUNNING -> ViewProcessState.QUEUED
                                TryOnJobStatus.SUCCEEDED -> ViewProcessState.SUCCEEDED
                                TryOnJobStatus.CANCELLED -> ViewProcessState.CANCELLED
                                else -> ViewProcessState.FAILED
                            },
                            outputRef = if (status == TryOnJobStatus.SUCCEEDED) fakeResultBackRef else null,
                            qualityReview = qualityReview,
                        )
                    },
                )
            }
        }
        return TryOnResult(
            jobId = "job-preview-1",
            requestId = "req-preview-1",
            outfitId = editorialWorkOutfit.outfitId,
            inputGeneration = 1L,
            inputRevisions = mapOf("profile" to 2L, "item:item-top-linen" to 1L, "item:item-trouser-noir" to 1L),
            status = status,
            views = views,
            appliedItemIds = listOf("item-top-linen", "item-trouser-noir"),
            unappliedItemIds = listOf("item-shoes-loafer"),
            providerConfigVersion = CONFIG_VERSION,
            createdAtEpochMillis = 1_700_000_000_000L,
            expiresAtEpochMillis = 1_700_003_600_000L,
            source = TryOnSource.FAKE,
            warnings = buildList {
                if (includeBack) {
                    add("INDEPENDENT_VIEW_GENERATION: Mặt trước và mặt sau được tạo bởi hai lượt độc lập.")
                }
                add("SUBSET_GARMENTS_ONLY: Giày loafer da đen không nằm trong danh mục hỗ trợ VTO.")
            },
            remoteDeletionState = RemoteDeletionState.NOT_UPLOADED,
        )
    }
}

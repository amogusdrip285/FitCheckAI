package com.fitcheckai.feature.tryon.contract

import com.fitcheckai.core.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * OWNER: Track C (`:feature:tryon`).
 *
 * Implements the exact CT-01 / CT-03 / CT-04 / CT-06 / CT-07 Virtual Try-On types and ports
 * proposed in `docs/execution/tracks/C_SHARED_CONTRACTS_PROPOSAL.md` while reusing `:core:model`
 * enums (`Category`, `Occasion`) without mutating frozen `:core:model` files before Track D's `S-002`.
 */
@Serializable
enum class MediaScope {
    GARMENT,
    PERSON,
    TRYON_RESULT,
}

@Serializable
enum class ImageView {
    FRONT,
    BACK,
}

@Serializable
data class MediaRef(
    val id: String,
    val ownerEntityId: String,
    val scope: MediaScope,
    val view: ImageView,
    val localRelativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteCount: Long,
    val contentHash: String,
    val revision: Long,
    val derivedFromMediaId: String? = null,
)

@Serializable
data class ImagePair(
    val front: MediaRef? = null,
    val back: MediaRef? = null,
) {
    fun refFor(view: ImageView): MediaRef? = when (view) {
        ImageView.FRONT -> front
        ImageView.BACK -> back
    }
}

@Serializable
enum class ConsentPurpose {
    RECOGNITION,
    TRYON,
}

@Serializable
data class ConsentGrant(
    val id: String,
    val purpose: ConsentPurpose,
    val providerId: String,
    val providerConfigVersion: String,
    val selectedMediaIds: Set<String>,
    val allowedViews: Set<ImageView>,
    val requestGeneration: Long,
    val confirmedByUser: Boolean,
    val issuedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
)

@Serializable
enum class FeasibilityDecision {
    UNKNOWN,
    NO_GO,
    GO_FRONT_ONLY,
    GO_DUAL,
}

@Serializable
enum class TryOnSource {
    AI_LIVE,
    FAKE,
}

@Serializable
enum class TryOnPresentationMode {
    ORIGINAL_PHOTO,
    STUDIO_WHITE,
}

@Serializable
enum class ViewProcessState {
    NOT_REQUESTED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED,
}

@Serializable
enum class QualityReviewState {
    UNREVIEWED,
    ACCEPTED,
    REJECTED,
}

@Serializable
enum class TryOnJobStatus {
    QUEUED,
    RUNNING,
    PARTIAL,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    UNKNOWN_SUBMISSION,
    CANCELLED,
    DELETED,
}

@Serializable
enum class RemoteDeletionState {
    NOT_UPLOADED,
    BASE64_EPHEMERAL_60M,
    EXPIRES_AUTOMATICALLY_72H,
    PENDING_BEST_EFFORT,
    UNSUPPORTED,
    UNKNOWN,
}

@Serializable
enum class TryOnErrorCode {
    INVALID_REQUEST,
    CONSENT_REQUIRED,
    UNAUTHORIZED,
    FORBIDDEN,
    PAYLOAD_TOO_LARGE,
    RATE_LIMITED,
    INSUFFICIENT_QUOTA,
    BUDGET_EXCEEDED,
    STALE_INPUT,
    CONFLICT,
    NEEDS_INPUT,
    UNSUPPORTED_CAPABILITY,
    INSUFFICIENT_WARDROBE,
    INVALID_AI_OUTPUT,
    NETWORK,
    UPSTREAM_TIMEOUT,
    UPSTREAM_ERROR,
    IO,
    CANCELLED,
    EXPIRED,
}

@Serializable
data class TryOnError(
    val code: TryOnErrorCode,
    val message: String,
    val requestId: String? = null,
    val retryable: Boolean = false,
)

class TryOnException(
    val tryOnError: TryOnError,
    cause: Throwable? = null,
) : Exception("${tryOnError.code}: ${tryOnError.message}", cause)

@Serializable
data class TryOnCapability(
    val decision: FeasibilityDecision,
    val supportedViews: Set<ImageView>,
    val supportedCategories: Set<Category>,
    val maxGarmentsPerJob: Int,
    val supportsNativePairedViews: Boolean,
    val runtimeVerifiedViews: Set<ImageView>,
    val providerId: String,
    val providerConfigVersion: String,
    val supportedPresentationModes: Set<TryOnPresentationMode> = setOf(TryOnPresentationMode.ORIGINAL_PHOTO),
    val executionMode: String? = null,
    val modelId: String? = null,
    val lockedReason: String? = null,
) {
    val isAvailableForTryOn: Boolean
        get() = (decision == FeasibilityDecision.GO_FRONT_ONLY || decision == FeasibilityDecision.GO_DUAL) &&
            supportedViews.isNotEmpty() &&
            maxGarmentsPerJob >= 1
}

@Serializable
data class TryOnItemSummary(
    val itemId: String,
    val label: String,
    val category: Category,
    val colors: List<String> = emptyList(),
    val accentHex: String? = null,
    val revision: Long = 1L,
)

@Serializable
data class TryOnOutfitSnapshot(
    val outfitId: String,
    val wardrobeSnapshotId: String,
    val explanation: String,
    val items: List<TryOnItemSummary>,
)

@Serializable
data class TryOnRequest(
    val schemaVersion: Int = 2,
    val requestId: String,
    val idempotencyKey: String,
    val sessionId: String = "local-session",
    val outfitSnapshot: TryOnOutfitSnapshot,
    val profileRevision: Long,
    val personImages: ImagePair,
    val garmentImages: Map<String, ImagePair>,
    val requestedViews: Set<ImageView>,
    val selectedItemIds: List<String>,
    val presentationMode: TryOnPresentationMode = TryOnPresentationMode.ORIGINAL_PHOTO,
    val consentGrant: ConsentGrant,
    val inputGeneration: Long,
    val providerConfigVersion: String,
)

@Serializable
data class TryOnJobHandle(
    val jobId: String,
    val requestId: String,
    val initialStatus: TryOnJobStatus,
)

@Serializable
data class ViewResult(
    val view: ImageView,
    val state: ViewProcessState,
    val outputRef: MediaRef? = null,
    val error: TryOnError? = null,
    val qualityReview: QualityReviewState = QualityReviewState.UNREVIEWED,
)

@Serializable
data class TryOnResult(
    val jobId: String,
    val requestId: String,
    val outfitId: String,
    val inputGeneration: Long,
    val inputRevisions: Map<String, Long>,
    val status: TryOnJobStatus,
    val views: Map<ImageView, ViewResult>,
    val appliedItemIds: List<String>,
    val unappliedItemIds: List<String>,
    val providerConfigVersion: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val source: TryOnSource,
    val warnings: List<String> = emptyList(),
    val remoteDeletionState: RemoteDeletionState = RemoteDeletionState.NOT_UPLOADED,
    val presentationMode: TryOnPresentationMode = TryOnPresentationMode.ORIGINAL_PHOTO,
    val providerId: String? = null,
) {
    val isTerminal: Boolean
        get() = status != TryOnJobStatus.QUEUED && status != TryOnJobStatus.RUNNING
}

@Serializable
data class CancellationReceipt(
    val jobId: String,
    val status: TryOnJobStatus,
    val cancelledAtEpochMillis: Long,
    val upstreamCancelAttempted: Boolean,
    val upstreamCancelConfirmed: Boolean,
    val refundGuaranteed: Boolean = false,
    val message: String,
)

@Serializable
data class DeletionReceipt(
    val jobId: String,
    val deletedAtEpochMillis: Long,
    val localFilesRemovedCount: Int,
    val failedLocalMediaIds: List<String> = emptyList(),
    val lateCompletionSuppressed: Boolean = true,
    val remoteDeletionState: RemoteDeletionState,
    val message: String,
)

/** CT-06 primary service port owned by Track C. */
interface VirtualTryOn {
    suspend fun capabilities(): Result<TryOnCapability>
    suspend fun submit(request: TryOnRequest): Result<TryOnJobHandle>
    fun observe(jobId: String): Flow<TryOnResult>
    suspend fun cancel(jobId: String): Result<CancellationReceipt>
    suspend fun delete(jobId: String): Result<DeletionReceipt>
}

/**
 * Port bridging Track A's `MediaStore` (CT-01) so Track C reads/imports/removes private media
 * without touching Room or absolute file paths directly.
 */
interface TryOnMediaStorePort {
    suspend fun readBytes(ref: MediaRef): Result<ByteArray>
    suspend fun prepareUploadDerivative(ref: MediaRef, maxBytes: Long): Result<MediaRef>
    suspend fun saveTryOnOutput(
        jobId: String,
        view: ImageView,
        mimeType: String,
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): Result<MediaRef>
    suspend fun remove(ref: MediaRef): Result<Unit>
}

/** Minimal snapshot of CT-04 UserProfile needed by Track C (never reads weight or unrelated PII). */
data class TryOnPersonProfileSnapshot(
    val profileId: String,
    val revision: Long,
    val bodyImages: ImagePair,
)

/** Port bridging Track D's `ProfileStorage` (CT-04). */
interface TryOnProfilePort {
    fun observePersonProfile(): Flow<TryOnPersonProfileSnapshot?>
}

/** Port bridging Track A/D's wardrobe revision checks to detect stale items before Try-On. */
interface TryOnWardrobeRevisionPort {
    suspend fun currentItemRevisions(itemIds: Collection<String>): Map<String, Long>
}

package com.fitcheckai.core.model.tryon

import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Outfit
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.DeletionReceipt
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.ResultSource
import kotlinx.coroutines.flow.Flow

// CT-06 — types OWNER: Track D; VirtualTryOn implementation, job store and TryOnScreen OWNER: Track C.
// UNKNOWN is never treated as supported. Only runtimeVerifiedViews may be offered to users.

const val TRYON_SCHEMA_VERSION = 2

enum class TryOnDecision { UNKNOWN, NO_GO, GO_FRONT_ONLY, GO_DUAL }

data class TryOnCapability(
    val decision: TryOnDecision = TryOnDecision.UNKNOWN,
    val supportedViews: Set<ImageView> = emptySet(),
    val supportedCategories: Set<Category> = emptySet(),
    val maxGarmentsPerJob: Int = 0,
    val supportsNativePairedViews: Boolean = false,
    val runtimeVerifiedViews: Set<ImageView> = emptySet(),
    val providerConfigVersion: String = "",
) {
    /** True only when the user can actually be offered a try-on for [views]. */
    fun canOffer(views: Set<ImageView>): Boolean = views.isNotEmpty() &&
        decision in setOf(TryOnDecision.GO_FRONT_ONLY, TryOnDecision.GO_DUAL) && runtimeVerifiedViews.containsAll(views)

    companion object {
        val UNAVAILABLE = TryOnCapability()
    }
}

data class TryOnRequest(
    val requestId: String,
    val idempotencyKey: String,
    val outfit: Outfit,
    val itemSummaries: List<ItemSummary>,
    val profileRevision: Long,
    val personImages: ImagePair,
    val garmentImages: Map<String, ImagePair>,
    val requestedViews: Set<ImageView>,
    val selectedItemIds: Set<String>,
    val consentGrantId: String,
    val inputGeneration: Long,
    val providerConfigVersion: String,
    val schemaVersion: Int = TRYON_SCHEMA_VERSION,
)

enum class ViewState { NOT_REQUESTED, QUEUED, RUNNING, SUCCEEDED, FAILED, EXPIRED, CANCELLED }
enum class QualityReview { UNREVIEWED, ACCEPTED, REJECTED }
enum class JobStatus { QUEUED, RUNNING, PARTIAL, SUCCEEDED, FAILED, TIMED_OUT, UNKNOWN_SUBMISSION, CANCELLED, DELETED }
enum class RemoteDeletionState { NOT_APPLICABLE, PENDING, CONFIRMED, UNSUPPORTED, UNKNOWN }

data class ViewResult(
    val state: ViewState = ViewState.NOT_REQUESTED,
    val outputRef: MediaRef? = null,
    val error: ErrorCode? = null,
    /** Transport SUCCEEDED ≠ ACCEPTED quality. */
    val qualityReview: QualityReview = QualityReview.UNREVIEWED,
)

data class TryOnResult(
    val jobId: String,
    val requestId: String,
    val outfitId: String,
    val inputGeneration: Long,
    val inputRevisions: Map<String, Long>,
    val status: JobStatus,
    val views: Map<ImageView, ViewResult>,
    val appliedItemIds: Set<String>,
    val unappliedItemIds: Set<String>,
    val providerConfigVersion: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val source: ResultSource,
    val warnings: List<String> = emptyList(),
    val remoteDeletionState: RemoteDeletionState = RemoteDeletionState.NOT_APPLICABLE,
)

data class JobHandle(val jobId: String, val requestId: String, val status: JobStatus)
data class CancellationReceipt(val jobId: String, val providerCancelAttempted: Boolean, val at: Long)

/**
 * OWNER: Track C. Submit failures use typed WardrobeException codes:
 * NEEDS_INPUT, CONSENT_REQUIRED, UNSUPPORTED_CAPABILITY, STALE_INPUT.
 */
interface VirtualTryOn {
    suspend fun capabilities(): Result<TryOnCapability>
    suspend fun submit(request: TryOnRequest): Result<JobHandle>
    fun observe(jobId: String): Flow<TryOnResult>
    suspend fun cancel(jobId: String): Result<CancellationReceipt>
    suspend fun delete(jobId: String): Result<DeletionReceipt>
}

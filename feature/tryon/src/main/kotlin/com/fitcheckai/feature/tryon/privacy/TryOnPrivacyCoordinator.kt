package com.fitcheckai.feature.tryon.privacy

import com.fitcheckai.core.model.Category
import com.fitcheckai.feature.tryon.contract.ConsentGrant
import com.fitcheckai.feature.tryon.contract.ConsentPurpose
import com.fitcheckai.feature.tryon.contract.DeletionReceipt
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.RemoteDeletionState
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnError
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.contract.TryOnRequest
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.TryOnWardrobeRevisionPort
import com.fitcheckai.feature.tryon.contract.VirtualTryOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class AuthorizedImageUpload(
    val mediaRef: MediaRef,
    val bytes: ByteArray,
)

data class AuthorizedTryOnUploadSlice(
    val requestId: String,
    val inputGeneration: Long,
    val personUploads: Map<ImageView, AuthorizedImageUpload>,
    val garmentUploads: Map<String, Map<ImageView, AuthorizedImageUpload>>,
    val totalBytes: Long,
)

data class PrivacyInvalidationReceipt(
    val reason: String,
    val newInputGeneration: Long,
    val cancelledJobIds: List<String>,
    val localFilesRemovedCount: Int,
    val failedLocalMediaIds: List<String>,
)

object TryOnPrivacyDisclosure {
    const val CONSENT_TITLE = "Quyền riêng tư khi mô phỏng thử đồ 2D"
    const val CONSENT_SUMMARY =
        "Chỉ những ảnh của bộ trang phục đang chọn và ảnh toàn thân thuộc góc nhìn bạn cho phép mới được gửi qua máy chủ trung gian để tạo ảnh mô phỏng 2D. Ảnh không tự động tải lên khi lưu hồ sơ."
    const val RETENTION_NOTICE =
        "Ảnh đầu vào chỉ gửi cho riêng lượt thử này sau khi bạn đồng ý; kết quả lưu trong bộ nhớ riêng tư của ứng dụng và bạn có thể xóa bản lưu cục bộ bất cứ lúc nào (chính sách lưu giữ phía nhà cung cấp AI tuân theo cấu hình tổ chức API)."
    const val FOSTA_RETENTION_NOTICE =
        "Ảnh đầu vào chỉ gửi cho riêng lượt thử này tới FOSTA (codex.fosta.app — proxy bên thứ ba thử nghiệm, model gpt-6-luna). Bạn có thể xóa ngay kết quả cục bộ bất cứ lúc nào; dịch vụ bên thứ ba này không có API xóa từ xa và chưa xác minh thời hạn lưu giữ."

    fun retentionNoticeForProvider(providerId: String?): String =
        if (providerId == "fosta") FOSTA_RETENTION_NOTICE else RETENTION_NOTICE
    const val PRODUCT_LIMITATION_DISCLAIMER =
        "Ảnh mô phỏng AI 2D chỉ mang tính tham khảo phối đồ — không đo số đo cơ thể và không đảm bảo quần áo vừa vặn kích cỡ ngoài đời thực."

    fun describeRemoteDeletion(state: RemoteDeletionState): String = when (state) {
        RemoteDeletionState.NOT_UPLOADED ->
            "Ảnh chưa từng gửi tới nhà cung cấp bên ngoài; toàn bộ bản lưu tạm trên thiết bị đã được xóa."
        RemoteDeletionState.BASE64_EPHEMERAL_60M ->
            "Đã xóa ngay ảnh kết quả trên thiết bị và máy chủ proxy. Nhà cung cấp không có API xóa tức thời nhưng dữ liệu ở chế độ base64 không lên CDN 72 giờ và tự hủy sau tối đa 60 phút."
        RemoteDeletionState.EXPIRES_AUTOMATICALLY_72H ->
            "Đã xóa kết quả cục bộ; bản lưu tạm phía nhà cung cấp sẽ tự động hết hạn sau tối đa 72 giờ."
        RemoteDeletionState.PENDING_BEST_EFFORT ->
            "Đã xóa kết quả cục bộ và gửi yêu cầu xóa best-effort tới nhà cung cấp."
        RemoteDeletionState.UNSUPPORTED ->
            "Đã xóa ảnh kết quả trên thiết bị và bản ghi tạm tại FitCheck server. Nhà cung cấp AI (OpenAI Images API hoặc proxy thử nghiệm FOSTA) không cung cấp API xóa từng request riêng lẻ từ xa."
        RemoteDeletionState.UNKNOWN ->
            "Đã xóa kết quả cục bộ; trạng thái lưu giữ từ xa chưa xác định."
    }
}

/**
 * OWNER: Track C (`C-210` — Privacy gate, per-request consent enforcement & cleanup coordinator).
 */
class TryOnPrivacyCoordinator(
    private val mediaStore: TryOnMediaStorePort,
    private val wardrobeRevisions: TryOnWardrobeRevisionPort? = null,
    private val logger: SafeTryOnLogger = SafeTryOnLogger(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    initialGeneration: Long = 1L,
) {
    private val generationCounter = AtomicLong(initialGeneration.coerceAtLeast(1L))
    private val stateMutex = Mutex()

    private val revokedConsentIds = mutableSetOf<String>()
    private val suppressedJobIds = mutableSetOf<String>()
    private val jobArtifacts = mutableMapOf<String, MutableList<MediaRef>>()
    private val transientUploadDerivatives = mutableListOf<MediaRef>()

    val currentInputGeneration: Long
        get() = generationCounter.get()

    fun createPerRequestConsentGrant(
        capability: TryOnCapability,
        selectedMediaIds: Set<String>,
        allowedViews: Set<ImageView>,
        confirmedByUser: Boolean,
        grantId: String = "consent-${UUID.randomUUID()}",
    ): ConsentGrant = ConsentGrant(
        id = grantId,
        purpose = ConsentPurpose.TRYON,
        providerId = capability.providerId,
        providerConfigVersion = capability.providerConfigVersion,
        selectedMediaIds = selectedMediaIds,
        allowedViews = allowedViews,
        requestGeneration = currentInputGeneration,
        confirmedByUser = confirmedByUser,
        issuedAtEpochMillis = clock(),
        revokedAtEpochMillis = null,
    )

    /**
     * Validates capability, staleness, per-request consent, and payload byte caps BEFORE any upload.
     * Guarantees `confirmedByUser == false` results in **0 bytes read / 0 uploads**.
     */
    suspend fun prepareAuthorizedUploadSlice(
        request: TryOnRequest,
        capability: TryOnCapability,
    ): Result<AuthorizedTryOnUploadSlice> {
        if (!capability.isAvailableForTryOn) {
            logger.log(
                event = "privacy.blocked.capability",
                requestId = request.requestId,
                errorCode = TryOnErrorCode.UNSUPPORTED_CAPABILITY,
            )
            return failure(
                TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                "Tính năng thử đồ chưa được mở ở chế độ '${capability.decision}'.",
                request.requestId,
            )
        }

        val activeGen = currentInputGeneration
        if (request.inputGeneration != activeGen) {
            return failure(
                TryOnErrorCode.STALE_INPUT,
                "Dữ liệu ảnh cá nhân hoặc trang phục đã thay đổi; vui lòng xác nhận lại yêu cầu.",
                request.requestId,
            )
        }

        val isRevoked = stateMutex.withLock { request.consentGrant.id in revokedConsentIds }
        val consent = request.consentGrant
        if (
            isRevoked ||
            !consent.confirmedByUser ||
            consent.purpose != ConsentPurpose.TRYON ||
            consent.revokedAtEpochMillis != null ||
            consent.providerId != capability.providerId ||
            consent.providerConfigVersion != capability.providerConfigVersion ||
            request.providerConfigVersion != capability.providerConfigVersion ||
            consent.requestGeneration != activeGen
        ) {
            logger.log(
                event = "privacy.blocked.consent",
                requestId = request.requestId,
                errorCode = TryOnErrorCode.CONSENT_REQUIRED,
            )
            return failure(
                TryOnErrorCode.CONSENT_REQUIRED,
                "Cần xác nhận đồng ý tải ảnh hợp lệ cho đúng lượt thử đồ này trước khi gửi ảnh.",
                request.requestId,
            )
        }

        if (request.requestedViews.isEmpty()) {
            return failure(
                TryOnErrorCode.INVALID_REQUEST,
                "Cần chọn ít nhất một góc nhìn để mô phỏng.",
                request.requestId,
            )
        }

        if (!capability.supportedViews.containsAll(request.requestedViews)) {
            return failure(
                TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                "Góc nhìn yêu cầu vượt quá khả năng đã xác minh của mô hình.",
                request.requestId,
            )
        }

        if (!consent.allowedViews.containsAll(request.requestedViews)) {
            return failure(
                TryOnErrorCode.CONSENT_REQUIRED,
                "Góc nhìn yêu cầu chưa được người dùng đồng ý cho phép tải lên.",
                request.requestId,
            )
        }

        if (request.selectedItemIds.isEmpty()) {
            return failure(
                TryOnErrorCode.NEEDS_INPUT,
                "Chưa chọn món trang phục nào để thử.",
                request.requestId,
            )
        }

        if (request.selectedItemIds.size > capability.maxGarmentsPerJob) {
            return failure(
                TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                "Mô hình hiện chỉ hỗ trợ tối đa ${capability.maxGarmentsPerJob} món trang phục mỗi lượt.",
                request.requestId,
            )
        }

        if (
            request.presentationMode != TryOnPresentationMode.ORIGINAL_PHOTO &&
            request.presentationMode !in capability.supportedPresentationModes
        ) {
            return failure(
                TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                "Chế độ hiển thị '${request.presentationMode}' chưa được mô hình hiện tại hỗ trợ.",
                request.requestId,
            )
        }

        val itemsById = request.outfitSnapshot.items.associateBy { it.itemId }
        var topCount = 0
        var bottomCount = 0
        for (itemId in request.selectedItemIds) {
            val summary = itemsById[itemId] ?: return failure(
                TryOnErrorCode.INVALID_REQUEST,
                "Món đồ '$itemId' không thuộc bộ trang phục đang chọn.",
                request.requestId,
            )
            if (summary.category !in capability.supportedCategories) {
                return failure(
                    TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                    "Danh mục '${summary.category}' chưa được hỗ trợ thử đồ.",
                    request.requestId,
                )
            }
            if (summary.category == Category.TOP || summary.category == Category.OUTERWEAR) topCount += 1
            if (summary.category == Category.BOTTOM) bottomCount += 1
        }

        if (capability.providerId == "fosta" && (topCount > 1 || bottomCount > 1)) {
            return failure(
                TryOnErrorCode.INVALID_REQUEST,
                "Trong một lượt thử FOSTA chỉ được chọn tối đa 1 Áo (TOP) và 1 Quần (BOTTOM).",
                request.requestId,
            )
        }

        // Check wardrobe revisions if repository port is wired (detects deleted/edited items)
        if (wardrobeRevisions != null) {
            val liveRevisions = wardrobeRevisions.currentItemRevisions(request.selectedItemIds)
            for (itemId in request.selectedItemIds) {
                val expectedRev = itemsById[itemId]?.revision
                val currentRev = liveRevisions[itemId]
                if (currentRev == null || currentRev != expectedRev) {
                    return failure(
                        TryOnErrorCode.STALE_INPUT,
                        "Món đồ trong trang phục đã được chỉnh sửa hoặc xóa khỏi tủ đồ.",
                        request.requestId,
                    )
                }
            }
        }

        // Prevent front-cloned-as-back whenever BACK is requested (both BACK_ONLY and FRONT_AND_BACK)
        if (ImageView.BACK in request.requestedViews) {
            val pFront = request.personImages.front
            val pBack = request.personImages.back
            if (pFront != null && pBack != null && pFront.contentHash == pBack.contentHash) {
                return failure(
                    TryOnErrorCode.INVALID_REQUEST,
                    "Không được nhân bản ảnh mặt trước làm ảnh mặt sau của người dùng.",
                    request.requestId,
                )
            }
            for (itemId in request.selectedItemIds) {
                val gPair = request.garmentImages[itemId]
                if (
                    gPair?.front != null &&
                    gPair.back != null &&
                    gPair.front.contentHash == gPair.back.contentHash
                ) {
                    return failure(
                        TryOnErrorCode.INVALID_REQUEST,
                        "Không được nhân bản ảnh mặt trước làm ảnh mặt sau của trang phục.",
                        request.requestId,
                    )
                }
            }
        }

        // Verify all required MediaRefs exist, match the requested view, and are in ConsentGrant.selectedMediaIds BEFORE reading bytes
        val requiredPersonRefs = mutableMapOf<ImageView, MediaRef>()
        val requiredGarmentRefs = mutableMapOf<String, MutableMap<ImageView, MediaRef>>()

        for (view in request.requestedViews) {
            val personRef = request.personImages.refFor(view) ?: return failure(
                TryOnErrorCode.NEEDS_INPUT,
                "Thiếu ảnh toàn thân cho góc nhìn $view.",
                request.requestId,
            )
            if (personRef.view != view) {
                return failure(
                    TryOnErrorCode.INVALID_REQUEST,
                    "Ảnh toàn thân khai báo góc nhìn '${personRef.view}' không khớp '$view'.",
                    request.requestId,
                )
            }
            if (personRef.id !in consent.selectedMediaIds) {
                return failure(
                    TryOnErrorCode.CONSENT_REQUIRED,
                    "Ảnh toàn thân (${personRef.view}) chưa nằm trong danh sách đồng ý tải lên.",
                    request.requestId,
                )
            }
            requiredPersonRefs[view] = personRef

            for (itemId in request.selectedItemIds) {
                val garmentRef = request.garmentImages[itemId]?.refFor(view) ?: return failure(
                    TryOnErrorCode.NEEDS_INPUT,
                    "Thiếu ảnh góc nhìn $view cho món đồ đã chọn.",
                    request.requestId,
                )
                if (garmentRef.view != view) {
                    return failure(
                        TryOnErrorCode.INVALID_REQUEST,
                        "Ảnh trang phục '$itemId' khai báo góc nhìn '${garmentRef.view}' không khớp '$view'.",
                        request.requestId,
                    )
                }
                if (garmentRef.id !in consent.selectedMediaIds) {
                    return failure(
                        TryOnErrorCode.CONSENT_REQUIRED,
                        "Ảnh trang phục chưa nằm trong danh sách đồng ý tải lên.",
                        request.requestId,
                    )
                }
                requiredGarmentRefs.getOrPut(itemId) { mutableMapOf() }[view] = garmentRef
            }
        }

        // Prepare upload derivatives & read bytes within CT-07 caps
        val personUploads = mutableMapOf<ImageView, AuthorizedImageUpload>()
        val garmentUploads = mutableMapOf<String, MutableMap<ImageView, AuthorizedImageUpload>>()
        var totalBytes = 0L

        for ((view, originalRef) in requiredPersonRefs) {
            val uploadRef = mediaStore.prepareUploadDerivative(originalRef, MAX_PERSON_BYTES_PER_VIEW)
                .getOrElse { cause ->
                    if (cause is TryOnException) {
                        return Result.failure(
                            TryOnException(cause.tryOnError.copy(requestId = request.requestId), cause),
                        )
                    }
                    return failure(
                        TryOnErrorCode.IO,
                        "Không thể chuẩn bị ảnh toàn thân ($view) để gửi đi.",
                        request.requestId,
                    )
                }
            if (uploadRef.id != originalRef.id) {
                trackTransientDerivative(uploadRef)
            }
            val bytes = mediaStore.readBytes(uploadRef).getOrElse { cause ->
                if (cause is TryOnException) {
                    return Result.failure(
                        TryOnException(cause.tryOnError.copy(requestId = request.requestId), cause),
                    )
                }
                return failure(
                    TryOnErrorCode.IO,
                    "Không tìm thấy dữ liệu ảnh toàn thân ($view) trong bộ nhớ riêng.",
                    request.requestId,
                )
            }
            if (bytes.isEmpty() || bytes.size.toLong() > MAX_PERSON_BYTES_PER_VIEW) {
                return failure(
                    TryOnErrorCode.PAYLOAD_TOO_LARGE,
                    "Ảnh toàn thân ($view) vượt quá giới hạn 2 MiB.",
                    request.requestId,
                )
            }
            totalBytes += bytes.size
            personUploads[view] = AuthorizedImageUpload(uploadRef, bytes)
        }

        for ((itemId, byView) in requiredGarmentRefs) {
            for ((view, originalRef) in byView) {
                val uploadRef = mediaStore.prepareUploadDerivative(originalRef, MAX_GARMENT_BYTES_PER_VIEW)
                    .getOrElse { cause ->
                        if (cause is TryOnException) {
                            return Result.failure(
                                TryOnException(cause.tryOnError.copy(requestId = request.requestId), cause),
                            )
                        }
                        return failure(
                            TryOnErrorCode.IO,
                            "Không thể chuẩn bị ảnh trang phục ($view).",
                            request.requestId,
                        )
                    }
                if (uploadRef.id != originalRef.id) {
                    trackTransientDerivative(uploadRef)
                }
                val bytes = mediaStore.readBytes(uploadRef).getOrElse { cause ->
                    if (cause is TryOnException) {
                        return Result.failure(
                            TryOnException(cause.tryOnError.copy(requestId = request.requestId), cause),
                        )
                    }
                    return failure(
                        TryOnErrorCode.IO,
                        "Không đọc được ảnh trang phục ($view) trong bộ nhớ riêng.",
                        request.requestId,
                    )
                }
                if (bytes.isEmpty() || bytes.size.toLong() > MAX_GARMENT_BYTES_PER_VIEW) {
                    return failure(
                        TryOnErrorCode.PAYLOAD_TOO_LARGE,
                        "Ảnh trang phục ($view) vượt quá giới hạn 1 MiB.",
                        request.requestId,
                    )
                }
                totalBytes += bytes.size
                garmentUploads.getOrPut(itemId) { mutableMapOf() }[view] =
                    AuthorizedImageUpload(uploadRef, bytes)
            }
        }

        if (totalBytes > MAX_TOTAL_DECODED_BYTES) {
            return failure(
                TryOnErrorCode.PAYLOAD_TOO_LARGE,
                "Tổng dung lượng bộ ảnh vượt giới hạn 8 MiB.",
                request.requestId,
            )
        }

        logger.log(
            event = "privacy.authorized.upload_slice",
            requestId = request.requestId,
        )
        return Result.success(
            AuthorizedTryOnUploadSlice(
                requestId = request.requestId,
                inputGeneration = activeGen,
                personUploads = personUploads,
                garmentUploads = garmentUploads,
                totalBytes = totalBytes,
            ),
        )
    }

    suspend fun registerJobArtifact(jobId: String, ref: MediaRef) {
        stateMutex.withLock {
            if (jobId in suppressedJobIds) {
                // Job was already deleted/revoked while output was saving -> immediately purge!
                mediaStore.remove(ref)
                return
            }
            jobArtifacts.getOrPut(jobId) { mutableListOf() }.add(ref)
        }
    }

    private suspend fun trackTransientDerivative(ref: MediaRef) {
        stateMutex.withLock {
            transientUploadDerivatives.add(ref)
        }
    }

    suspend fun shouldAcceptResult(result: TryOnResult): Boolean = stateMutex.withLock {
        result.jobId !in suppressedJobIds && result.inputGeneration == currentInputGeneration
    }

    suspend fun suppressJob(jobId: String) {
        stateMutex.withLock {
            suppressedJobIds.add(jobId)
        }
    }

    suspend fun cleanupJobArtifacts(
        jobId: String,
        remoteDeletionState: RemoteDeletionState,
    ): DeletionReceipt {
        val refsToRemove = stateMutex.withLock {
            suppressedJobIds.add(jobId)
            val jobRefs = jobArtifacts.remove(jobId).orEmpty()
            val tempDerivatives = transientUploadDerivatives.toList()
            transientUploadDerivatives.clear()
            (jobRefs + tempDerivatives).distinctBy { it.id }
        }
        var removedCount = 0
        val failedIds = mutableListOf<String>()
        for (ref in refsToRemove) {
            mediaStore.remove(ref)
                .onSuccess { removedCount += 1 }
                .onFailure { failedIds += ref.id }
        }
        val deletedAt = clock()
        logger.log(
            event = "privacy.cleanup.job",
            requestId = "del-$jobId",
            jobId = jobId,
        )
        return DeletionReceipt(
            jobId = jobId,
            deletedAtEpochMillis = deletedAt,
            localFilesRemovedCount = removedCount,
            failedLocalMediaIds = failedIds,
            lateCompletionSuppressed = true,
            remoteDeletionState = remoteDeletionState,
            message = TryOnPrivacyDisclosure.describeRemoteDeletion(remoteDeletionState),
        )
    }

    suspend fun onPersonPhotoChangedOrDeleted(
        view: ImageView,
        activeJobIds: Collection<String> = emptyList(),
        tryOnService: VirtualTryOn? = null,
    ): PrivacyInvalidationReceipt = invalidateAndPurge(
        reason = "person_photo_changed_${view.name.lowercase()}",
        activeJobIds = activeJobIds,
        tryOnService = tryOnService,
    )

    suspend fun onGarmentChangedOrDeleted(
        itemId: String,
        activeJobIds: Collection<String> = emptyList(),
        tryOnService: VirtualTryOn? = null,
    ): PrivacyInvalidationReceipt = invalidateAndPurge(
        reason = "garment_changed_$itemId",
        activeJobIds = activeJobIds,
        tryOnService = tryOnService,
    )

    suspend fun onProfileCleared(
        activeJobIds: Collection<String> = emptyList(),
        tryOnService: VirtualTryOn? = null,
    ): PrivacyInvalidationReceipt = invalidateAndPurge(
        reason = "profile_cleared",
        activeJobIds = activeJobIds,
        tryOnService = tryOnService,
    )

    suspend fun onConsentRevoked(
        consentGrantId: String,
        activeJobIds: Collection<String> = emptyList(),
        tryOnService: VirtualTryOn? = null,
    ): PrivacyInvalidationReceipt {
        stateMutex.withLock {
            revokedConsentIds.add(consentGrantId)
        }
        return invalidateAndPurge(
            reason = "consent_revoked",
            activeJobIds = activeJobIds,
            tryOnService = tryOnService,
        )
    }

    private suspend fun invalidateAndPurge(
        reason: String,
        activeJobIds: Collection<String>,
        tryOnService: VirtualTryOn?,
    ): PrivacyInvalidationReceipt {
        val nextGen = generationCounter.incrementAndGet()
        val (allTrackedJobIds, refsToDelete) = stateMutex.withLock {
            val trackedJobs = (jobArtifacts.keys + activeJobIds).toSet()
            suppressedJobIds.addAll(trackedJobs)
            val collectedRefs = mutableListOf<MediaRef>()
            jobArtifacts.values.forEach { collectedRefs.addAll(it) }
            jobArtifacts.clear()
            collectedRefs.addAll(transientUploadDerivatives)
            transientUploadDerivatives.clear()
            trackedJobs to collectedRefs
        }

        val cancelledIds = mutableListOf<String>()
        if (tryOnService != null) {
            for (jobId in activeJobIds) {
                tryOnService.cancel(jobId).onSuccess { cancelledIds += jobId }
            }
        }

        var removedCount = 0
        val failedIds = mutableListOf<String>()
        for (ref in refsToDelete.distinctBy { it.id }) {
            mediaStore.remove(ref)
                .onSuccess { removedCount += 1 }
                .onFailure { failedIds += ref.id }
        }

        logger.log(
            event = "privacy.invalidated.$reason",
            requestId = "gen-$nextGen",
        )
        return PrivacyInvalidationReceipt(
            reason = reason,
            newInputGeneration = nextGen,
            cancelledJobIds = cancelledIds.ifEmpty { allTrackedJobIds.toList() },
            localFilesRemovedCount = removedCount,
            failedLocalMediaIds = failedIds,
        )
    }

    private fun <T> failure(
        code: TryOnErrorCode,
        message: String,
        requestId: String,
    ): Result<T> = Result.failure(
        TryOnException(
            TryOnError(
                code = code,
                message = message,
                requestId = requestId,
                retryable = false,
            ),
        ),
    )

    companion object {
        const val MAX_PERSON_BYTES_PER_VIEW: Long = 2L * 1024L * 1024L // 2 MiB
        const val MAX_GARMENT_BYTES_PER_VIEW: Long = 1L * 1024L * 1024L // 1 MiB
        const val MAX_TOTAL_DECODED_BYTES: Long = 8L * 1024L * 1024L // 8 MiB
    }
}

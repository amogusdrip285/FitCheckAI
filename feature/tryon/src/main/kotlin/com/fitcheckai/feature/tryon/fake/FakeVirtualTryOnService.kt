package com.fitcheckai.feature.tryon.fake

import com.fitcheckai.feature.tryon.contract.CancellationReceipt
import com.fitcheckai.feature.tryon.contract.DeletionReceipt
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.RemoteDeletionState
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnError
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnJobHandle
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import com.fitcheckai.feature.tryon.contract.TryOnRequest
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.ViewProcessState
import com.fitcheckai.feature.tryon.contract.ViewResult
import com.fitcheckai.feature.tryon.contract.VirtualTryOn
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FakeTryOnScenario {
    SUCCEED_REQUESTED_VIEWS,
    PARTIAL_BACK_FAILED,
    ALL_VIEWS_FAILED,
    TIMED_OUT,
    UNKNOWN_SUBMISSION,
    EXPIRED_OUTPUT,
    QUALITY_REJECTED_SEVERE,
    STAY_QUEUED,
    STAY_RUNNING,
    WAIT_FOR_MANUAL_GATE,
}

/**
 * OWNER: Track C (`C-206` — Finite-state fake `VirtualTryOn` service & contract test double).
 *
 * Always marks `source = TryOnSource.FAKE` and `remoteDeletionState = NOT_UPLOADED`.
 * Never makes network calls or incurs provider billing.
 */
class FakeVirtualTryOnService(
    initialCapability: TryOnCapability = FakeTryOnFixtures.dualCapability,
    initialScenario: FakeTryOnScenario = FakeTryOnFixtures.dualCapability.let {
        FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS
    },
    private val mediaStore: TryOnMediaStorePort = FakeTryOnFixtures.createPopulatedMediaStore(),
    val privacyCoordinator: TryOnPrivacyCoordinator = TryOnPrivacyCoordinator(mediaStore),
    private val stepDelayMs: Long = 10L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : VirtualTryOn {

    @Volatile
    var capability: TryOnCapability = initialCapability

    @Volatile
    var scenario: FakeTryOnScenario = initialScenario

    private val mutex = Mutex()
    private var jobSequence = 0
    private val jobsById = LinkedHashMap<String, MutableStateFlow<TryOnResult?>>()
    private val jobIdByIdempotencyScope = LinkedHashMap<String, Pair<String, String>>() // scopeKey -> (jobId, payloadFingerprint)
    private val manualGates = LinkedHashMap<String, CompletableDeferred<Unit>>()

    override suspend fun capabilities(): Result<TryOnCapability> = Result.success(capability)

    fun releaseManualGate(jobId: String) {
        manualGates[jobId]?.complete(Unit)
    }

    override suspend fun submit(request: TryOnRequest): Result<TryOnJobHandle> {
        val cap = capability
        val scopeKey = "${request.sessionId}::${request.inputGeneration}::${request.idempotencyKey}"
        val fingerprint = canonicalFingerprint(request)

        mutex.withLock {
            val existing = jobIdByIdempotencyScope[scopeKey]
            if (existing != null) {
                val (existingJobId, existingFingerprint) = existing
                if (existingFingerprint != fingerprint) {
                    return Result.failure(
                        TryOnException(
                            TryOnError(
                                code = TryOnErrorCode.CONFLICT,
                                message = "Khóa idempotencyKey đã gắn với nội dung yêu cầu khác.",
                                requestId = request.requestId,
                            ),
                        ),
                    )
                }
                val current = jobsById[existingJobId]?.value
                if (current != null) {
                    return Result.success(
                        TryOnJobHandle(
                            jobId = current.jobId,
                            requestId = current.requestId,
                            initialStatus = current.status,
                        ),
                    )
                }
            }

            // Enforce max 1 active job per session
            val activeOther = jobsById.values
                .mapNotNull { it.value }
                .firstOrNull { !it.isTerminal || it.status == TryOnJobStatus.UNKNOWN_SUBMISSION }
            if (activeOther != null) {
                return Result.failure(
                    TryOnException(
                        TryOnError(
                            code = TryOnErrorCode.CONFLICT,
                            message = "Đang có một tiến trình thử đồ chưa kết thúc trong phiên hiện tại.",
                            requestId = request.requestId,
                        ),
                    ),
                )
            }
        }

        // Enforce privacy, consent, byte caps & input completeness BEFORE creating job
        val uploadSlice = privacyCoordinator.prepareAuthorizedUploadSlice(request, cap)
            .getOrElse { return Result.failure(it) }

        val activeScenario = scenario
        val nowMs = clock()
        val unappliedIds = request.outfitSnapshot.items
            .map { it.itemId }
            .filterNot { it in request.selectedItemIds }
        val warnings = buildList {
            if (request.requestedViews.size > 1 && !cap.supportsNativePairedViews) {
                add("INDEPENDENT_VIEW_GENERATION: Ảnh mặt trước và mặt sau được sinh bởi hai tiến trình độc lập.")
            }
            if (unappliedIds.isNotEmpty()) {
                add("SUBSET_GARMENTS_ONLY: Một số món trong outfit không nằm trong danh sách mô phỏng.")
            }
        }
        val inputRevisions = buildMap {
            put("profile", request.profileRevision)
            request.outfitSnapshot.items.forEach { put("item:${it.itemId}", it.revision) }
        }

        val handleAndFlow = mutex.withLock {
            // Re-check idempotency after suspend validation
            val existing = jobIdByIdempotencyScope[scopeKey]
            if (existing != null) {
                val (existingJobId, existingFingerprint) = existing
                if (existingFingerprint != fingerprint) {
                    return Result.failure(
                        TryOnException(
                            TryOnError(
                                code = TryOnErrorCode.CONFLICT,
                                message = "Khóa idempotencyKey bị xung đột payload.",
                                requestId = request.requestId,
                            ),
                        ),
                    )
                }
                val current = jobsById[existingJobId]?.value!!
                return Result.success(
                    TryOnJobHandle(current.jobId, current.requestId, current.status),
                )
            }

            jobSequence += 1
            val jobId = "fake-job-${jobSequence.toString().padStart(4, '0')}"
            val initialStatus = if (activeScenario == FakeTryOnScenario.UNKNOWN_SUBMISSION) {
                TryOnJobStatus.UNKNOWN_SUBMISSION
            } else {
                TryOnJobStatus.QUEUED
            }

            val initialViews = request.requestedViews.associateWith { view ->
                if (activeScenario == FakeTryOnScenario.UNKNOWN_SUBMISSION) {
                    ViewResult(
                        view = view,
                        state = ViewProcessState.FAILED,
                        error = TryOnError(
                            code = TryOnErrorCode.UPSTREAM_TIMEOUT,
                            message = "Mất phản hồi xác nhận khi gửi yêu cầu; không tự gửi lại để tránh trùng lặp.",
                            requestId = request.requestId,
                            retryable = false,
                        ),
                    )
                } else {
                    ViewResult(view = view, state = ViewProcessState.QUEUED)
                }
            }

            val initialResult = TryOnResult(
                jobId = jobId,
                requestId = request.requestId,
                outfitId = request.outfitSnapshot.outfitId,
                inputGeneration = uploadSlice.inputGeneration,
                inputRevisions = inputRevisions,
                status = initialStatus,
                views = initialViews,
                appliedItemIds = request.selectedItemIds,
                unappliedItemIds = unappliedIds,
                providerConfigVersion = request.providerConfigVersion,
                createdAtEpochMillis = nowMs,
                expiresAtEpochMillis = null,
                source = TryOnSource.FAKE,
                warnings = warnings,
                remoteDeletionState = RemoteDeletionState.NOT_UPLOADED,
                presentationMode = request.presentationMode,
                providerId = cap.providerId,
            )

            val flow = MutableStateFlow<TryOnResult?>(initialResult)
            jobsById[jobId] = flow
            jobIdByIdempotencyScope[scopeKey] = jobId to fingerprint
            if (activeScenario == FakeTryOnScenario.WAIT_FOR_MANUAL_GATE) {
                manualGates[jobId] = CompletableDeferred()
            }
            Triple(TryOnJobHandle(jobId, request.requestId, initialStatus), flow, activeScenario)
        }

        val (handle, stateFlow, chosenScenario) = handleAndFlow
        if (chosenScenario != FakeTryOnScenario.UNKNOWN_SUBMISSION &&
            chosenScenario != FakeTryOnScenario.STAY_QUEUED
        ) {
            scope.launch {
                advanceFakeJob(handle.jobId, request, stateFlow, chosenScenario)
            }
        }

        return Result.success(handle)
    }

    private suspend fun advanceFakeJob(
        jobId: String,
        request: TryOnRequest,
        stateFlow: MutableStateFlow<TryOnResult?>,
        chosenScenario: FakeTryOnScenario,
    ) {
        if (stepDelayMs > 0) delay(stepDelayMs)

        mutex.withLock {
            val cur = stateFlow.value ?: return
            if (cur.status == TryOnJobStatus.CANCELLED || cur.status == TryOnJobStatus.DELETED) return
            stateFlow.value = cur.copy(
                status = TryOnJobStatus.RUNNING,
                views = cur.views.mapValues { (v, vr) ->
                    vr.copy(view = v, state = ViewProcessState.RUNNING)
                },
            )
        }

        if (chosenScenario == FakeTryOnScenario.STAY_RUNNING) return

        if (chosenScenario == FakeTryOnScenario.WAIT_FOR_MANUAL_GATE) {
            manualGates[jobId]?.await()
        } else if (stepDelayMs > 0) {
            delay(stepDelayMs)
        }

        // Check privacy coordinator before publishing late completion!
        val snapshotBeforePublish = stateFlow.value ?: return
        if (!privacyCoordinator.shouldAcceptResult(snapshotBeforePublish)) {
            return
        }

        val completedAt = clock()
        val finalViews = mutableMapOf<ImageView, ViewResult>()

        for (view in request.requestedViews) {
            when (chosenScenario) {
                FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
                FakeTryOnScenario.WAIT_FOR_MANUAL_GATE,
                FakeTryOnScenario.QUALITY_REJECTED_SEVERE -> {
                    val savedRef = mediaStore.saveTryOnOutput(
                        jobId = jobId,
                        view = view,
                        mimeType = "image/png",
                        bytes = "FAKE_DEV_OUTPUT_${jobId}_${view.name}".encodeToByteArray(),
                        width = 768,
                        height = 1024,
                    ).getOrNull()
                    if (savedRef != null) {
                        privacyCoordinator.registerJobArtifact(jobId, savedRef)
                    }
                    finalViews[view] = ViewResult(
                        view = view,
                        state = ViewProcessState.SUCCEEDED,
                        outputRef = savedRef,
                        qualityReview = if (chosenScenario == FakeTryOnScenario.QUALITY_REJECTED_SEVERE) {
                            QualityReviewState.REJECTED
                        } else {
                            QualityReviewState.UNREVIEWED
                        },
                    )
                }

                FakeTryOnScenario.PARTIAL_BACK_FAILED -> {
                    if (view == ImageView.FRONT) {
                        val savedRef = mediaStore.saveTryOnOutput(
                            jobId = jobId,
                            view = view,
                            mimeType = "image/png",
                            bytes = "FAKE_DEV_OUTPUT_${jobId}_FRONT".encodeToByteArray(),
                            width = 768,
                            height = 1024,
                        ).getOrNull()
                        if (savedRef != null) {
                            privacyCoordinator.registerJobArtifact(jobId, savedRef)
                        }
                        finalViews[view] = ViewResult(
                            view = view,
                            state = ViewProcessState.SUCCEEDED,
                            outputRef = savedRef,
                            qualityReview = QualityReviewState.UNREVIEWED,
                        )
                    } else {
                        finalViews[view] = ViewResult(
                            view = view,
                            state = ViewProcessState.FAILED,
                            outputRef = null,
                            error = TryOnError(
                                code = TryOnErrorCode.INVALID_AI_OUTPUT,
                                message = "Mô phỏng góc nhìn mặt sau thất bại; không tạo ảnh thay thế giả.",
                                requestId = request.requestId,
                            ),
                        )
                    }
                }

                FakeTryOnScenario.ALL_VIEWS_FAILED -> {
                    finalViews[view] = ViewResult(
                        view = view,
                        state = ViewProcessState.FAILED,
                        error = TryOnError(
                            code = TryOnErrorCode.UPSTREAM_ERROR,
                            message = "Không thể tạo ảnh mô phỏng cho góc nhìn $view.",
                            requestId = request.requestId,
                            retryable = true,
                        ),
                    )
                }

                FakeTryOnScenario.TIMED_OUT -> {
                    finalViews[view] = ViewResult(
                        view = view,
                        state = ViewProcessState.FAILED,
                        error = TryOnError(
                            code = TryOnErrorCode.UPSTREAM_TIMEOUT,
                            message = "Quá thời hạn xử lý kỹ thuật cho góc nhìn $view.",
                            requestId = request.requestId,
                            retryable = false,
                        ),
                    )
                }

                FakeTryOnScenario.EXPIRED_OUTPUT -> {
                    finalViews[view] = ViewResult(
                        view = view,
                        state = ViewProcessState.EXPIRED,
                        outputRef = null,
                        error = TryOnError(
                            code = TryOnErrorCode.EXPIRED,
                            message = "Ảnh kết quả tạm thời đã hết hạn lưu trữ.",
                            requestId = request.requestId,
                        ),
                    )
                }

                else -> Unit
            }
        }

        mutex.withLock {
            val cur = stateFlow.value ?: return
            // Suppress late completion if cancelled, deleted, or generation invalidated!
            if (cur.status == TryOnJobStatus.CANCELLED ||
                cur.status == TryOnJobStatus.DELETED ||
                !privacyCoordinator.shouldAcceptResult(cur)
            ) {
                finalViews.values.mapNotNull { it.outputRef }.forEach { mediaStore.remove(it) }
                return
            }

            val succeededCount = finalViews.values.count { it.state == ViewProcessState.SUCCEEDED }
            val nextStatus = when {
                chosenScenario == FakeTryOnScenario.TIMED_OUT -> TryOnJobStatus.TIMED_OUT
                succeededCount == request.requestedViews.size && succeededCount > 0 -> TryOnJobStatus.SUCCEEDED
                succeededCount > 0 -> TryOnJobStatus.PARTIAL
                else -> TryOnJobStatus.FAILED
            }

            stateFlow.value = cur.copy(
                status = nextStatus,
                views = finalViews,
                expiresAtEpochMillis = if (succeededCount > 0) completedAt + 3_600_000L else null,
            )
        }
    }

    override fun observe(jobId: String): Flow<TryOnResult> {
        val flow = jobsById[jobId] ?: MutableStateFlow(
            TryOnResult(
                jobId = jobId,
                requestId = "unknown",
                outfitId = "unknown",
                inputGeneration = privacyCoordinator.currentInputGeneration,
                inputRevisions = emptyMap(),
                status = TryOnJobStatus.FAILED,
                views = emptyMap(),
                appliedItemIds = emptyList(),
                unappliedItemIds = emptyList(),
                providerConfigVersion = capability.providerConfigVersion,
                createdAtEpochMillis = clock(),
                source = TryOnSource.FAKE,
                remoteDeletionState = RemoteDeletionState.NOT_UPLOADED,
            ),
        )
        return flow.filterNotNull()
    }

    override suspend fun cancel(jobId: String): Result<CancellationReceipt> {
        val nowMs = clock()
        privacyCoordinator.suppressJob(jobId)
        val updated = mutex.withLock {
            val flow = jobsById[jobId]
                ?: return Result.failure(
                    TryOnException(
                        TryOnError(TryOnErrorCode.INVALID_REQUEST, "Không tìm thấy tiến trình $jobId."),
                    ),
                )
            val cur = flow.value!!
            if (!cur.isTerminal) {
                val cancelled = cur.copy(
                    status = TryOnJobStatus.CANCELLED,
                    views = cur.views.mapValues { (v, vr) ->
                        if (vr.state == ViewProcessState.QUEUED || vr.state == ViewProcessState.RUNNING) {
                            vr.copy(
                                view = v,
                                state = ViewProcessState.CANCELLED,
                                outputRef = null,
                                error = TryOnError(TryOnErrorCode.CANCELLED, "Đã hủy theo yêu cầu người dùng."),
                            )
                        } else {
                            vr
                        }
                    },
                )
                flow.value = cancelled
                cancelled
            } else {
                cur
            }
        }
        manualGates[jobId]?.complete(Unit)
        return Result.success(
            CancellationReceipt(
                jobId = jobId,
                status = updated.status,
                cancelledAtEpochMillis = nowMs,
                upstreamCancelAttempted = true,
                upstreamCancelConfirmed = true,
                refundGuaranteed = false,
                message = "Đã dừng tiến trình giả lập DEV; ở môi trường thật việc hủy không cam kết hoàn lại phí nếu mô hình đã chạy.",
            ),
        )
    }

    override suspend fun delete(jobId: String): Result<DeletionReceipt> {
        val receipt = privacyCoordinator.cleanupJobArtifacts(
            jobId = jobId,
            remoteDeletionState = RemoteDeletionState.NOT_UPLOADED,
        )
        mutex.withLock {
            val flow = jobsById[jobId] ?: return@withLock
            val cur = flow.value ?: return@withLock
            flow.value = cur.copy(
                status = TryOnJobStatus.DELETED,
                views = cur.views.mapValues { (v, vr) ->
                    vr.copy(
                        view = v,
                        state = if (vr.state == ViewProcessState.SUCCEEDED) {
                            ViewProcessState.EXPIRED
                        } else {
                            ViewProcessState.CANCELLED
                        },
                        outputRef = null,
                    )
                },
            )
        }
        manualGates[jobId]?.complete(Unit)
        return Result.success(receipt)
    }

    private fun canonicalFingerprint(request: TryOnRequest): String = buildString {
        append(request.outfitSnapshot.outfitId).append('|')
        append(request.profileRevision).append('|')
        append(request.requestedViews.map { it.name }.sorted().joinToString(",")).append('|')
        append(request.presentationMode.name).append('|')
        append(request.selectedItemIds.sorted().joinToString(",")).append('|')
        append(request.consentGrant.id)
    }
}

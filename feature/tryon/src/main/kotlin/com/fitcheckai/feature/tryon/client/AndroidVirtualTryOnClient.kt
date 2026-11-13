package com.fitcheckai.feature.tryon.client

import com.fitcheckai.core.model.Category
import com.fitcheckai.feature.tryon.contract.CancellationReceipt
import com.fitcheckai.feature.tryon.contract.ConsentPurpose
import com.fitcheckai.feature.tryon.contract.DeletionReceipt
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.RemoteDeletionState
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnError
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnJobHandle
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.contract.TryOnRequest
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.ViewProcessState
import com.fitcheckai.feature.tryon.contract.ViewResult
import com.fitcheckai.feature.tryon.contract.VirtualTryOn
import com.fitcheckai.feature.tryon.privacy.SafeTryOnLogger
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64

@Serializable
data class WireImageInputDto(
    val mediaId: String,
    val view: ImageView,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val contentHash: String,
    val revision: Long,
    val base64Data: String,
)

@Serializable
data class WireImagePairDto(
    val front: WireImageInputDto? = null,
    val back: WireImageInputDto? = null,
)

@Serializable
data class WireOutfitItemDto(
    val itemId: String,
    val revision: Long,
    val category: Category,
    val colors: List<String> = emptyList(),
)

@Serializable
data class WireOutfitSnapshotDto(
    val outfitId: String,
    val wardrobeSnapshotId: String,
    val items: List<WireOutfitItemDto>,
)

@Serializable
data class WireConsentGrantDto(
    val id: String,
    val purpose: ConsentPurpose,
    val providerId: String,
    val providerConfigVersion: String,
    val selectedMediaIds: List<String>,
    val allowedViews: List<ImageView>,
    val requestGeneration: Long,
    val confirmedByUser: Boolean,
    val issuedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
)

@Serializable
data class TryOnSubmitWireDto(
    val schemaVersion: Int = 2,
    val requestId: String,
    val idempotencyKey: String,
    val outfitSnapshot: WireOutfitSnapshotDto,
    val profileRevision: Long,
    val personImages: WireImagePairDto,
    val garmentImages: Map<String, WireImagePairDto>,
    val requestedViews: List<ImageView>,
    val selectedItemIds: List<String>,
    val presentationMode: TryOnPresentationMode = TryOnPresentationMode.ORIGINAL_PHOTO,
    val consentGrant: WireConsentGrantDto,
    val inputGeneration: Long,
    val providerConfigVersion: String,
)

@Serializable
data class TryOnSubmitResponseDto(
    val jobId: String,
    val requestId: String,
    val status: TryOnJobStatus,
)

@Serializable
data class ViewOutputPayloadDto(
    val mimeType: String,
    val base64Data: String,
    val contentHash: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class ViewResultWireDto(
    val view: ImageView,
    val state: ViewProcessState,
    val output: ViewOutputPayloadDto? = null,
    val error: TryOnError? = null,
    val qualityReview: QualityReviewState = QualityReviewState.UNREVIEWED,
)

@Serializable
data class TryOnResultWireDto(
    val jobId: String,
    val requestId: String,
    val outfitId: String,
    val inputGeneration: Long,
    val inputRevisions: Map<String, Long>,
    val status: TryOnJobStatus,
    val views: Map<ImageView, ViewResultWireDto>,
    val appliedItemIds: List<String>,
    val unappliedItemIds: List<String>,
    val providerConfigVersion: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val source: TryOnSource,
    val warnings: List<String> = emptyList(),
    val remoteDeletionState: RemoteDeletionState = RemoteDeletionState.UNKNOWN,
    val presentationMode: TryOnPresentationMode = TryOnPresentationMode.ORIGINAL_PHOTO,
    val providerId: String? = null,
)

@Serializable
data class CancellationReceiptWireDto(
    val jobId: String,
    val status: TryOnJobStatus,
    val cancelledAt: Long,
    val upstreamCancelAttempted: Boolean,
    val upstreamCancelConfirmed: Boolean,
    val refundGuaranteed: Boolean = false,
    val message: String,
)

@Serializable
data class DeletionReceiptWireDto(
    val jobId: String,
    val status: String,
    val deletedAt: Long,
    val localOutputsDeleted: Boolean,
    val lateCompletionSuppressed: Boolean,
    val remoteDeletionState: RemoteDeletionState,
    val message: String,
)

@Serializable
private data class ErrorEnvelopeDto(
    val error: TryOnError,
)

class AmbiguousTransportSubmissionException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

interface TryOnHttpTransport {
    suspend fun fetchCapabilities(sessionId: String = "android-local-session"): Result<TryOnCapability> =
        Result.failure(UnsupportedOperationException("Capabilities endpoint not implemented by transport"))
    suspend fun submitJob(sessionId: String, payload: TryOnSubmitWireDto): Result<TryOnSubmitResponseDto>
    suspend fun pollJob(sessionId: String, remoteJobId: String): Result<TryOnResultWireDto>
    suspend fun cancelJob(sessionId: String, remoteJobId: String): Result<CancellationReceiptWireDto>
    suspend fun deleteJob(sessionId: String, remoteJobId: String): Result<DeletionReceiptWireDto>
}

class KtorTryOnHttpTransport(
    private val httpClient: HttpClient,
    baseUrl: String,
    private val localAuthToken: String? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : TryOnHttpTransport {
    private val trimmedBaseUrl = baseUrl.trimEnd('/')

    private fun io.ktor.client.request.HttpRequestBuilder.applyCommonHeaders(sessionId: String) {
        header("x-session-id", sessionId)
        if (!localAuthToken.isNullOrBlank()) {
            header("x-tryon-local-token", localAuthToken)
        }
    }

    override suspend fun fetchCapabilities(sessionId: String): Result<TryOnCapability> {
        val response = try {
            httpClient.get("$trimmedBaseUrl/v2/tryon/capabilities") {
                applyCommonHeaders(sessionId)
            }
        } catch (t: Throwable) {
            return Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.NETWORK,
                        message = "Không thể kết nối tới FitCheck Try-On server ($trimmedBaseUrl).",
                        retryable = true,
                    ),
                    t,
                ),
            )
        }
        val rawText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Result.failure(parseErrorEnvelope(rawText, null))
        }
        return runCatching { json.decodeFromString<TryOnCapability>(rawText) }
    }

    override suspend fun submitJob(
        sessionId: String,
        payload: TryOnSubmitWireDto,
    ): Result<TryOnSubmitResponseDto> {
        val response = try {
            httpClient.post("$trimmedBaseUrl/v2/tryon/jobs") {
                applyCommonHeaders(sessionId)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(payload))
            }
        } catch (t: Throwable) {
            // Once HTTP POST was dispatched, timeout or socket close is ambiguous — NEVER auto-retry POST!
            return Result.failure(
                AmbiguousTransportSubmissionException(
                    "Mất kết nối trong khi gửi yêu cầu thử đồ; không tự động gửi lại để tránh tính phí hai lần.",
                    t,
                ),
            )
        }

        val rawText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Result.failure(parseErrorEnvelope(rawText, payload.requestId))
        }
        return runCatching {
            json.decodeFromString<TryOnSubmitResponseDto>(rawText)
        }.recoverCatching { cause ->
            throw TryOnException(
                TryOnError(
                    code = TryOnErrorCode.INVALID_AI_OUTPUT,
                    message = "Phản hồi xác nhận tiến trình từ máy chủ không đúng định dạng.",
                    requestId = payload.requestId,
                ),
                cause,
            )
        }
    }

    override suspend fun pollJob(
        sessionId: String,
        remoteJobId: String,
    ): Result<TryOnResultWireDto> {
        val response = try {
            httpClient.get("$trimmedBaseUrl/v2/tryon/jobs/$remoteJobId") {
                applyCommonHeaders(sessionId)
            }
        } catch (t: Throwable) {
            return Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.NETWORK,
                        message = "Mất kết nối tạm thời khi kiểm tra trạng thái tiến trình.",
                        retryable = true,
                    ),
                    t,
                ),
            )
        }

        val rawText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Result.failure(parseErrorEnvelope(rawText, null))
        }
        return runCatching { json.decodeFromString<TryOnResultWireDto>(rawText) }
    }

    override suspend fun cancelJob(
        sessionId: String,
        remoteJobId: String,
    ): Result<CancellationReceiptWireDto> {
        val response = try {
            httpClient.post("$trimmedBaseUrl/v2/tryon/jobs/$remoteJobId/cancel") {
                applyCommonHeaders(sessionId)
            }
        } catch (t: Throwable) {
            return Result.failure(
                TryOnException(
                    TryOnError(TryOnErrorCode.NETWORK, "Không thể gửi yêu cầu hủy tới máy chủ.", retryable = true),
                    t,
                ),
            )
        }
        val rawText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Result.failure(parseErrorEnvelope(rawText, null))
        }
        return runCatching { json.decodeFromString<CancellationReceiptWireDto>(rawText) }
    }

    override suspend fun deleteJob(
        sessionId: String,
        remoteJobId: String,
    ): Result<DeletionReceiptWireDto> {
        val response = try {
            httpClient.delete("$trimmedBaseUrl/v2/tryon/jobs/$remoteJobId") {
                applyCommonHeaders(sessionId)
            }
        } catch (t: Throwable) {
            return Result.failure(
                TryOnException(
                    TryOnError(TryOnErrorCode.NETWORK, "Không thể gửi yêu cầu xóa tới máy chủ.", retryable = true),
                    t,
                ),
            )
        }
        val rawText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Result.failure(parseErrorEnvelope(rawText, null))
        }
        return runCatching { json.decodeFromString<DeletionReceiptWireDto>(rawText) }
    }

    private fun parseErrorEnvelope(rawBody: String, fallbackRequestId: String?): TryOnException {
        val parsed = runCatching { json.decodeFromString<ErrorEnvelopeDto>(rawBody).error }.getOrNull()
        return TryOnException(
            parsed ?: TryOnError(
                code = TryOnErrorCode.UPSTREAM_ERROR,
                message = "Máy chủ thử đồ trả về lỗi không xác định.",
                requestId = fallbackRequestId,
                retryable = false,
            ),
        )
    }
}

/**
 * OWNER: Track C (`C-208` — Reliable Android VTO client with pre-POST reservation,
 * bounded backoff polling, and process-death recovery without duplicate POSTs).
 */
class AndroidVirtualTryOnClient(
    private val capabilityProvider: suspend () -> TryOnCapability,
    private val transport: TryOnHttpTransport,
    private val localJobStore: TryOnLocalJobStore,
    private val mediaStore: TryOnMediaStorePort,
    private val privacyCoordinator: TryOnPrivacyCoordinator,
    private val logger: SafeTryOnLogger = SafeTryOnLogger(),
    private val pollBackoffScheduleMs: List<Long> = listOf(2_000L, 4_000L, 8_000L),
    private val frontDeadlineMs: Long = 120_000L,
    private val dualDeadlineMs: Long = 180_000L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : VirtualTryOn {

    private val submitMutex = Mutex()
    private val stateMutex = Mutex()
    private val stateFlows = LinkedHashMap<String, MutableStateFlow<TryOnResult?>>()
    private val activePollerJobs = LinkedHashMap<String, Job>()
    private var recoveredOnce = false

    override suspend fun capabilities(): Result<TryOnCapability> =
        runCatching { capabilityProvider() }

    /**
     * Reconciles persisted local jobs after process death/relaunch:
     * - Any job caught in `DISPATCHING_POST` (lost HTTP response) becomes `UNKNOWN_SUBMISSION`
     *   and is NEVER re-POSTed automatically.
     * - Any job in `SUBMITTED_KNOWN_JOB` with non-terminal status is ready to resume `GET` polling.
     */
    suspend fun recoverPersistedJobs(): List<TryOnResult> {
        val persisted = localJobStore.loadAll()
        val recoveredResults = mutableListOf<TryOnResult>()
        for (record in persisted) {
            val reconciled = if (record.submissionPhase == SubmissionPhase.DISPATCHING_POST) {
                val unknownViews = record.requestedViews.associateWith { v ->
                    ViewResult(
                        view = v,
                        state = ViewProcessState.FAILED,
                        error = TryOnError(
                            code = TryOnErrorCode.UPSTREAM_TIMEOUT,
                            message = "Ứng dụng đã khởi động lại khi chưa nhận được phản hồi gửi yêu cầu; không tự gửi lại để tránh tính phí trùng.",
                            requestId = record.requestId,
                            retryable = false,
                        ),
                    )
                }
                val updatedResult = record.latestResult.copy(
                    status = TryOnJobStatus.UNKNOWN_SUBMISSION,
                    views = unknownViews,
                )
                val updatedRec = record.copy(
                    submissionPhase = SubmissionPhase.AMBIGUOUS_LOST_RESPONSE,
                    latestResult = updatedResult,
                )
                localJobStore.upsert(updatedRec)
                updatedRec
            } else {
                record
            }

            stateMutex.withLock {
                val flow = stateFlows.getOrPut(reconciled.jobId) {
                    MutableStateFlow(reconciled.latestResult)
                }
                flow.value = reconciled.latestResult
                if (reconciled.remoteJobId != null) {
                    stateFlows[reconciled.remoteJobId] = flow
                }
            }
            recoveredResults += reconciled.latestResult
        }
        recoveredOnce = true
        return recoveredResults
    }

    private suspend fun ensureRecovered() {
        if (!recoveredOnce) {
            recoverPersistedJobs()
        }
    }

    override suspend fun submit(request: TryOnRequest): Result<TryOnJobHandle> = submitMutex.withLock {
        ensureRecovered()
        val capability = capabilities().getOrElse { return@withLock Result.failure(it) }
        val fingerprint = computeFingerprint(request)

        // 1. Check persisted idempotency record BEFORE any upload or POST
        val existingIdempotent = localJobStore.findByIdempotency(
            sessionId = request.sessionId,
            inputGeneration = request.inputGeneration,
            idempotencyKey = request.idempotencyKey,
        )
        if (existingIdempotent != null) {
            if (existingIdempotent.payloadFingerprint != fingerprint) {
                return@withLock Result.failure(
                    TryOnException(
                        TryOnError(
                            code = TryOnErrorCode.CONFLICT,
                            message = "Khóa idempotencyKey đã được sử dụng với dữ liệu đầu vào khác.",
                            requestId = request.requestId,
                        ),
                    ),
                )
            }
            val effectiveId = existingIdempotent.remoteJobId ?: existingIdempotent.jobId
            return@withLock Result.success(
                TryOnJobHandle(
                    jobId = effectiveId,
                    requestId = existingIdempotent.requestId,
                    initialStatus = existingIdempotent.latestResult.status,
                ),
            )
        }

        // 2. Enforce max 1 active job per session
        val allPersisted = localJobStore.loadAll()
        val otherActive = allPersisted.firstOrNull {
            it.sessionId == request.sessionId && !it.latestResult.isTerminal
        }
        if (otherActive != null) {
            return@withLock Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.CONFLICT,
                        message = "Phiên hiện tại đang có một yêu cầu thử đồ đang chạy.",
                        requestId = request.requestId,
                    ),
                ),
            )
        }

        // 3. Enforce privacy gate, per-request consent & payload byte caps
        val authorizedSlice = privacyCoordinator.prepareAuthorizedUploadSlice(request, capability)
            .getOrElse { return@withLock Result.failure(it) }

        val nowMs = clock()
        val baseDeadlineMs = if (request.requestedViews.size > 1) dualDeadlineMs else frontDeadlineMs
        val deadlineMs = if (capability.providerId == "fosta") {
            maxOf(baseDeadlineMs, 260_000L)
        } else {
            baseDeadlineMs
        }
        val localReservationId = "local-${request.requestId}"
        val unappliedIds = request.outfitSnapshot.items
            .map { it.itemId }
            .filterNot { it in request.selectedItemIds }
        val inputRevisions = buildMap {
            put("profile", request.profileRevision)
            request.outfitSnapshot.items.forEach { put("item:${it.itemId}", it.revision) }
        }

        val initialSource = if (
            capability.providerId.startsWith("fake") ||
            capability.executionMode == "FAKE" ||
            capability.executionMode == "OFFLINE_MOCK_OPENAI" ||
            capability.executionMode == "OFFLINE_MOCK_FOSTA"
        ) {
            TryOnSource.FAKE
        } else {
            TryOnSource.AI_LIVE
        }

        val initialResult = TryOnResult(
            jobId = localReservationId,
            requestId = request.requestId,
            outfitId = request.outfitSnapshot.outfitId,
            inputGeneration = authorizedSlice.inputGeneration,
            inputRevisions = inputRevisions,
            status = TryOnJobStatus.QUEUED,
            views = request.requestedViews.associateWith { ViewResult(view = it, state = ViewProcessState.QUEUED) },
            appliedItemIds = request.selectedItemIds,
            unappliedItemIds = unappliedIds,
            providerConfigVersion = request.providerConfigVersion,
            createdAtEpochMillis = nowMs,
            source = initialSource,
            remoteDeletionState = RemoteDeletionState.UNKNOWN,
            presentationMode = request.presentationMode,
            providerId = capability.providerId,
        )

        val prePostRecord = PersistedTryOnJobRecord(
            jobId = localReservationId,
            remoteJobId = null,
            sessionId = request.sessionId,
            requestId = request.requestId,
            idempotencyKey = request.idempotencyKey,
            payloadFingerprint = fingerprint,
            outfitId = request.outfitSnapshot.outfitId,
            inputGeneration = authorizedSlice.inputGeneration,
            inputRevisions = inputRevisions,
            requestedViews = request.requestedViews,
            selectedItemIds = request.selectedItemIds,
            unappliedItemIds = unappliedIds,
            providerConfigVersion = request.providerConfigVersion,
            submissionPhase = SubmissionPhase.DISPATCHING_POST,
            createdAtEpochMillis = nowMs,
            deadlineEpochMillis = nowMs + deadlineMs,
            latestResult = initialResult,
        )

        // Persist BEFORE sending HTTP POST so process death never causes blind re-POST!
        localJobStore.upsert(prePostRecord)

        val wireDto = buildWireDto(request, authorizedSlice)
        val submitResult = transport.submitJob(request.sessionId, wireDto)

        submitResult.fold(
            onSuccess = { dto ->
                val remoteAlreadyTerminal =
                    dto.status != TryOnJobStatus.QUEUED && dto.status != TryOnJobStatus.RUNNING
                // Keep UI in RUNNING state during the few ms while GET /jobs/{id} downloads & saves view PNGs,
                // preventing any unhydrated error-state flash on synchronous providers like OpenAI.
                val preHydrationStatus = if (remoteAlreadyTerminal) TryOnJobStatus.RUNNING else dto.status
                val preHydrationViewState =
                    if (remoteAlreadyTerminal) ViewProcessState.RUNNING else ViewProcessState.QUEUED
                val knownResult = initialResult.copy(
                    jobId = dto.jobId,
                    status = preHydrationStatus,
                    views = request.requestedViews.associateWith {
                        ViewResult(view = it, state = preHydrationViewState)
                    },
                )
                val submittedRecord = prePostRecord.copy(
                    remoteJobId = dto.jobId,
                    submissionPhase = SubmissionPhase.SUBMITTED_KNOWN_JOB,
                    latestResult = knownResult,
                    viewsHydrated = false,
                    remoteReportedTerminalOnSubmit = remoteAlreadyTerminal,
                )
                localJobStore.upsert(submittedRecord)
                val sharedFlow = MutableStateFlow<TryOnResult?>(knownResult)
                stateMutex.withLock {
                    stateFlows[localReservationId] = sharedFlow
                    stateFlows[dto.jobId] = sharedFlow
                }
                if (shouldHydrateOrPoll(submittedRecord)) {
                    ensurePollerStarted(submittedRecord, sharedFlow)
                }
                logger.log(
                    event = "client.submit.accepted",
                    requestId = request.requestId,
                    jobId = dto.jobId,
                    status = dto.status,
                    latencyMs = clock() - nowMs,
                )
                Result.success(
                    TryOnJobHandle(
                        jobId = dto.jobId,
                        requestId = dto.requestId,
                        initialStatus = dto.status,
                    ),
                )
            },
            onFailure = { err ->
                if (err is AmbiguousTransportSubmissionException) {
                    val unknownResult = initialResult.copy(
                        status = TryOnJobStatus.UNKNOWN_SUBMISSION,
                        views = request.requestedViews.associateWith { v ->
                            ViewResult(
                                view = v,
                                state = ViewProcessState.FAILED,
                                error = TryOnError(
                                    code = TryOnErrorCode.UPSTREAM_TIMEOUT,
                                    message = err.message ?: "Trạng thái gửi yêu cầu không xác định.",
                                    requestId = request.requestId,
                                    retryable = false,
                                ),
                            )
                        },
                    )
                    val ambiguousRec = prePostRecord.copy(
                        submissionPhase = SubmissionPhase.AMBIGUOUS_LOST_RESPONSE,
                        latestResult = unknownResult,
                    )
                    localJobStore.upsert(ambiguousRec)
                    stateMutex.withLock {
                        stateFlows[localReservationId] = MutableStateFlow(unknownResult)
                    }
                    logger.log(
                        event = "client.submit.ambiguous",
                        requestId = request.requestId,
                        jobId = localReservationId,
                        status = TryOnJobStatus.UNKNOWN_SUBMISSION,
                        errorCode = TryOnErrorCode.UPSTREAM_TIMEOUT,
                    )
                    Result.success(
                        TryOnJobHandle(
                            jobId = localReservationId,
                            requestId = request.requestId,
                            initialStatus = TryOnJobStatus.UNKNOWN_SUBMISSION,
                        ),
                    )
                } else {
                    // Deterministic rejection before job creation: remove pre-POST reservation
                    localJobStore.remove(localReservationId)
                    Result.failure(err)
                }
            },
        )
    }

    override fun observe(jobId: String): Flow<TryOnResult> = flow {
        ensureRecovered()
        val record = localJobStore.getByJobId(jobId)
        val stateFlow = stateMutex.withLock {
            stateFlows.getOrPut(jobId) {
                MutableStateFlow(record?.latestResult)
            }
        }
        if (record != null && shouldHydrateOrPoll(record)) {
            ensurePollerStarted(record, stateFlow)
        }
        emitAll(stateFlow.filterNotNull())
    }

    private fun shouldHydrateOrPoll(record: PersistedTryOnJobRecord): Boolean {
        if (record.submissionPhase != SubmissionPhase.SUBMITTED_KNOWN_JOB || record.remoteJobId == null) {
            return false
        }
        val status = record.latestResult.status
        if (
            status == TryOnJobStatus.CANCELLED ||
            status == TryOnJobStatus.DELETED ||
            status == TryOnJobStatus.UNKNOWN_SUBMISSION
        ) {
            return false
        }
        return !record.latestResult.isTerminal || !record.viewsHydrated
    }

    private suspend fun ensurePollerStarted(
        record: PersistedTryOnJobRecord,
        stateFlow: MutableStateFlow<TryOnResult?>,
    ) {
        val remoteJobId = record.remoteJobId ?: return
        stateMutex.withLock {
            val existingJob = activePollerJobs[remoteJobId]
            if (existingJob != null && existingJob.isActive) return
            activePollerJobs[remoteJobId] = scope.launch {
                runBoundedPollLoop(record.jobId, remoteJobId, stateFlow)
            }
        }
    }

    private suspend fun runBoundedPollLoop(
        localKey: String,
        remoteJobId: String,
        stateFlow: MutableStateFlow<TryOnResult?>,
    ) {
        var attempt = 0
        val cachedSavedRefs = mutableMapOf<ImageView, MediaRef>()

        while (true) {
            val currentRecord = localJobStore.getByJobId(localKey) ?: break
            val latest = currentRecord.latestResult
            if (!shouldHydrateOrPoll(currentRecord)) break
            if (!privacyCoordinator.shouldAcceptResult(latest)) break

            // Synchronous providers (e.g. OpenAI /v1/images/edits) return terminal status on submit
            // without inline image bytes: hydrate via GET immediately with 0ms delay on attempt 0.
            val immediateHydration =
                (latest.isTerminal || currentRecord.remoteReportedTerminalOnSubmit) &&
                    !currentRecord.viewsHydrated &&
                    attempt == 0
            val pollTargetRecord = if (immediateHydration) {
                currentRecord
            } else {
                val delayMs = pollBackoffScheduleMs
                    .getOrElse(attempt) { pollBackoffScheduleMs.lastOrNull() ?: 8_000L }
                val remainingMs = currentRecord.deadlineEpochMillis - clock()
                if (remainingMs <= 0L) {
                    markClientTimedOut(currentRecord, stateFlow)
                    break
                }

                delay(minOf(delayMs, remainingMs))

                val refreshedRecord = localJobStore.getByJobId(localKey) ?: break
                if (!shouldHydrateOrPoll(refreshedRecord)) break
                if (clock() >= refreshedRecord.deadlineEpochMillis) {
                    markClientTimedOut(refreshedRecord, stateFlow)
                    break
                }
                refreshedRecord
            }

            val pollRes = transport.pollJob(pollTargetRecord.sessionId, remoteJobId)
            attempt += 1

            // Transient network error during poll: continue backoff until deadline without re-POSTing!
            val dto = pollRes.getOrNull() ?: continue

            // Re-check cancellation/deletion/revocation right after network suspension
            val postPollRecord = localJobStore.getByJobId(localKey) ?: break
            if (postPollRecord.latestResult.status == TryOnJobStatus.CANCELLED ||
                postPollRecord.latestResult.status == TryOnJobStatus.DELETED ||
                !privacyCoordinator.shouldAcceptResult(postPollRecord.latestResult)
            ) {
                break
            }

            val mappedResult = mapWireDtoToDomainResult(dto, cachedSavedRefs)
            if (!privacyCoordinator.shouldAcceptResult(mappedResult)) {
                break
            }

            val updatedRecord = postPollRecord.copy(
                latestResult = mappedResult,
                viewsHydrated = mappedResult.isTerminal,
            )
            localJobStore.upsert(updatedRecord)
            stateFlow.value = mappedResult

            if (mappedResult.isTerminal) {
                break
            }
        }
    }

    private suspend fun markClientTimedOut(
        record: PersistedTryOnJobRecord,
        stateFlow: MutableStateFlow<TryOnResult?>,
    ) {
        val current = record.latestResult
        if (current.isTerminal) return
        val updatedViews = current.views.mapValues { (v, vr) ->
            if (vr.state == ViewProcessState.QUEUED || vr.state == ViewProcessState.RUNNING) {
                vr.copy(
                    view = v,
                    state = ViewProcessState.FAILED,
                    error = TryOnError(
                        code = TryOnErrorCode.UPSTREAM_TIMEOUT,
                        message = "Đã quá thời hạn chờ kỹ thuật (${(record.deadlineEpochMillis - record.createdAtEpochMillis) / 1000}s).",
                        requestId = record.requestId,
                        retryable = false,
                    ),
                )
            } else {
                vr
            }
        }
        val succeededCount = updatedViews.values.count { it.state == ViewProcessState.SUCCEEDED }
        val timedOutResult = current.copy(
            status = if (succeededCount > 0) TryOnJobStatus.PARTIAL else TryOnJobStatus.TIMED_OUT,
            views = updatedViews,
        )
        localJobStore.upsert(record.copy(latestResult = timedOutResult))
        stateFlow.value = timedOutResult
    }

    private suspend fun mapWireDtoToDomainResult(
        dto: TryOnResultWireDto,
        cachedSavedRefs: MutableMap<ImageView, MediaRef>,
    ): TryOnResult {
        val nowMs = clock()
        val isExpiredByClock = dto.expiresAt != null && nowMs >= dto.expiresAt
        val domainViews = mutableMapOf<ImageView, ViewResult>()

        for ((view, wireView) in dto.views) {
            if (wireView.state == ViewProcessState.SUCCEEDED && isExpiredByClock) {
                domainViews[view] = ViewResult(
                    view = view,
                    state = ViewProcessState.EXPIRED,
                    outputRef = null,
                    error = TryOnError(
                        code = TryOnErrorCode.EXPIRED,
                        message = "Ảnh kết quả tạm thời đã hết hạn lưu trữ.",
                        requestId = dto.requestId,
                    ),
                    qualityReview = wireView.qualityReview,
                )
                continue
            }

            val savedRef = if (wireView.state == ViewProcessState.SUCCEEDED && wireView.output != null) {
                cachedSavedRefs[view] ?: run {
                    val decodedBytes = runCatching {
                        Base64.getDecoder().decode(wireView.output.base64Data)
                    }.getOrNull()
                    if (decodedBytes != null && decodedBytes.isNotEmpty()) {
                        val ref = mediaStore.saveTryOnOutput(
                            jobId = dto.jobId,
                            view = view,
                            mimeType = wireView.output.mimeType,
                            bytes = decodedBytes,
                            width = wireView.output.width,
                            height = wireView.output.height,
                        ).getOrNull()
                        if (ref != null) {
                            privacyCoordinator.registerJobArtifact(dto.jobId, ref)
                            cachedSavedRefs[view] = ref
                        }
                        ref
                    } else {
                        null
                    }
                }
            } else {
                null
            }

            if (wireView.state == ViewProcessState.SUCCEEDED && savedRef == null) {
                domainViews[view] = ViewResult(
                    view = view,
                    state = ViewProcessState.FAILED,
                    outputRef = null,
                    error = TryOnError(
                        code = TryOnErrorCode.INVALID_AI_OUTPUT,
                        message = "Không thể lưu hoặc giải mã ảnh kết quả của góc nhìn $view.",
                        requestId = dto.requestId,
                    ),
                )
            } else {
                domainViews[view] = ViewResult(
                    view = view,
                    state = wireView.state,
                    outputRef = savedRef,
                    error = wireView.error,
                    qualityReview = wireView.qualityReview,
                )
            }
        }

        val effectiveStatus = if (isExpiredByClock && dto.status == TryOnJobStatus.SUCCEEDED) {
            TryOnJobStatus.FAILED
        } else {
            dto.status
        }

        return TryOnResult(
            jobId = dto.jobId,
            requestId = dto.requestId,
            outfitId = dto.outfitId,
            inputGeneration = dto.inputGeneration,
            inputRevisions = dto.inputRevisions,
            status = effectiveStatus,
            views = domainViews,
            appliedItemIds = dto.appliedItemIds,
            unappliedItemIds = dto.unappliedItemIds,
            providerConfigVersion = dto.providerConfigVersion,
            createdAtEpochMillis = dto.createdAt,
            expiresAtEpochMillis = dto.expiresAt,
            source = dto.source,
            warnings = dto.warnings,
            remoteDeletionState = dto.remoteDeletionState,
            presentationMode = dto.presentationMode,
            providerId = dto.providerId,
        )
    }

    override suspend fun cancel(jobId: String): Result<CancellationReceipt> {
        ensureRecovered()
        privacyCoordinator.suppressJob(jobId)
        val record = localJobStore.getByJobId(jobId)
            ?: return Result.failure(
                TryOnException(TryOnError(TryOnErrorCode.INVALID_REQUEST, "Không tìm thấy tiến trình $jobId.")),
            )

        stateMutex.withLock {
            record.remoteJobId?.let { activePollerJobs.remove(it)?.cancel() }
        }

        val remoteReceipt = record.remoteJobId?.let { remoteId ->
            transport.cancelJob(record.sessionId, remoteId).getOrNull()
        }

        val cancelledAt = clock()
        val cancelledResult = record.latestResult.copy(
            status = TryOnJobStatus.CANCELLED,
            views = record.latestResult.views.mapValues { (v, vr) ->
                if (vr.state == ViewProcessState.QUEUED || vr.state == ViewProcessState.RUNNING) {
                    vr.copy(
                        view = v,
                        state = ViewProcessState.CANCELLED,
                        outputRef = null,
                        error = TryOnError(TryOnErrorCode.CANCELLED, "Người dùng đã hủy tiến trình thử đồ."),
                    )
                } else {
                    vr
                }
            },
        )
        localJobStore.upsert(record.copy(latestResult = cancelledResult))
        stateMutex.withLock {
            stateFlows[record.jobId]?.value = cancelledResult
            record.remoteJobId?.let { stateFlows[it]?.value = cancelledResult }
        }

        return Result.success(
            CancellationReceipt(
                jobId = record.remoteJobId ?: record.jobId,
                status = TryOnJobStatus.CANCELLED,
                cancelledAtEpochMillis = remoteReceipt?.cancelledAt ?: cancelledAt,
                upstreamCancelAttempted = remoteReceipt?.upstreamCancelAttempted ?: (record.remoteJobId != null),
                upstreamCancelConfirmed = remoteReceipt?.upstreamCancelConfirmed ?: false,
                refundGuaranteed = false,
                message = remoteReceipt?.message
                    ?: "Đã hủy theo dõi trên thiết bị; không cam kết hoàn lại hạn mức nếu nhà cung cấp đã bắt đầu xử lý.",
            ),
        )
    }

    override suspend fun delete(jobId: String): Result<DeletionReceipt> {
        ensureRecovered()
        val record = localJobStore.getByJobId(jobId)
        stateMutex.withLock {
            record?.remoteJobId?.let { activePollerJobs.remove(it)?.cancel() }
        }

        val remoteDel = if (record?.remoteJobId != null) {
            transport.deleteJob(record.sessionId, record.remoteJobId).getOrNull()
        } else {
            null
        }

        val remoteState = remoteDel?.remoteDeletionState
            ?: record?.latestResult?.remoteDeletionState
            ?: RemoteDeletionState.NOT_UPLOADED

        val localCleanup = privacyCoordinator.cleanupJobArtifacts(
            jobId = record?.remoteJobId ?: jobId,
            remoteDeletionState = remoteState,
        )

        if (record != null) {
            val deletedResult = record.latestResult.copy(
                status = TryOnJobStatus.DELETED,
                views = record.latestResult.views.mapValues { (v, vr) ->
                    vr.copy(view = v, state = ViewProcessState.EXPIRED, outputRef = null)
                },
                remoteDeletionState = remoteState,
            )
            localJobStore.remove(record.jobId)
            stateMutex.withLock {
                stateFlows[record.jobId]?.value = deletedResult
                record.remoteJobId?.let { stateFlows[it]?.value = deletedResult }
            }
        }

        return Result.success(localCleanup)
    }

    private fun buildWireDto(
        request: TryOnRequest,
        slice: com.fitcheckai.feature.tryon.privacy.AuthorizedTryOnUploadSlice,
    ): TryOnSubmitWireDto {
        val encoder = Base64.getEncoder()
        fun toDto(upload: com.fitcheckai.feature.tryon.privacy.AuthorizedImageUpload): WireImageInputDto =
            WireImageInputDto(
                mediaId = upload.mediaRef.derivedFromMediaId ?: upload.mediaRef.id,
                view = upload.mediaRef.view,
                mimeType = upload.mediaRef.mimeType,
                width = upload.mediaRef.width,
                height = upload.mediaRef.height,
                contentHash = upload.mediaRef.contentHash,
                revision = upload.mediaRef.revision,
                base64Data = encoder.encodeToString(upload.bytes),
            )

        val personPairDto = WireImagePairDto(
            front = slice.personUploads[ImageView.FRONT]?.let(::toDto),
            back = slice.personUploads[ImageView.BACK]?.let(::toDto),
        )
        val garmentDtos = slice.garmentUploads.mapValues { (_, byView) ->
            WireImagePairDto(
                front = byView[ImageView.FRONT]?.let(::toDto),
                back = byView[ImageView.BACK]?.let(::toDto),
            )
        }

        return TryOnSubmitWireDto(
            schemaVersion = 2,
            requestId = request.requestId,
            idempotencyKey = request.idempotencyKey,
            outfitSnapshot = WireOutfitSnapshotDto(
                outfitId = request.outfitSnapshot.outfitId,
                wardrobeSnapshotId = request.outfitSnapshot.wardrobeSnapshotId,
                items = request.outfitSnapshot.items.map {
                    WireOutfitItemDto(
                        itemId = it.itemId,
                        revision = it.revision,
                        category = it.category,
                        colors = it.colors,
                    )
                },
            ),
            profileRevision = request.profileRevision,
            personImages = personPairDto,
            garmentImages = garmentDtos,
            requestedViews = request.requestedViews.toList(),
            selectedItemIds = request.selectedItemIds,
            presentationMode = request.presentationMode,
            consentGrant = WireConsentGrantDto(
                id = request.consentGrant.id,
                purpose = request.consentGrant.purpose,
                providerId = request.consentGrant.providerId,
                providerConfigVersion = request.consentGrant.providerConfigVersion,
                selectedMediaIds = request.consentGrant.selectedMediaIds.toList(),
                allowedViews = request.consentGrant.allowedViews.toList(),
                requestGeneration = request.consentGrant.requestGeneration,
                confirmedByUser = request.consentGrant.confirmedByUser,
                issuedAtEpochMillis = request.consentGrant.issuedAtEpochMillis,
                revokedAtEpochMillis = request.consentGrant.revokedAtEpochMillis,
            ),
            inputGeneration = request.inputGeneration,
            providerConfigVersion = request.providerConfigVersion,
        )
    }

    private fun computeFingerprint(request: TryOnRequest): String = buildString {
        append(request.outfitSnapshot.outfitId).append('|')
        append(request.profileRevision).append('|')
        append(request.presentationMode.name).append('|')
        append(request.requestedViews.map { it.name }.sorted().joinToString(",")).append('|')
        append(request.selectedItemIds.sorted().joinToString(",")).append('|')
        append(request.consentGrant.id)
    }
}

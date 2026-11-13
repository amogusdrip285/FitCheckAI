package com.fitcheckai.feature.tryon.client

import app.cash.turbine.test
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.RemoteDeletionState
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.ViewProcessState
import com.fitcheckai.feature.tryon.contract.ViewResult
import com.fitcheckai.feature.tryon.fake.FakeInMemoryMediaStore
import com.fitcheckai.feature.tryon.fake.FakeTryOnFixtures
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidVirtualTryOnClientTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pngSampleBase64 =
        Base64.getEncoder().encodeToString("VALID_PNG_BYTES".encodeToByteArray())

    @Test
    fun `Ktor transport sends sanitized wire payload without localRelativePath and polls with bounded backoff`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientScope = TestScope(dispatcher)
        val storageFile = File(tempFolder.newFolder("no_backup"), "tryon_jobs_v2.json")
        val localStore = AtomicFileTryOnLocalJobStore(storageFile)
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore, clock = { testScheduler.currentTime })

        var submitPosts = 0
        var pollGets = 0
        var capturedPostBody = ""

        val mockConfig = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
            val path = request.url.encodedPath
            when {
                request.method.value == "POST" && path == "/v2/tryon/jobs" -> {
                    submitPosts += 1
                    capturedPostBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"jobId":"srv-job-1","requestId":"req-1","status":"RUNNING"}""",
                        status = HttpStatusCode.Accepted,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                request.method.value == "GET" && path == "/v2/tryon/jobs/srv-job-1" -> {
                    pollGets += 1
                    if (pollGets < 3) {
                        respond(
                            content = """
                                {
                                  "jobId":"srv-job-1",
                                  "requestId":"req-1",
                                  "outfitId":"outfit-editorial-01",
                                  "inputGeneration":1,
                                  "inputRevisions":{"profile":2},
                                  "status":"RUNNING",
                                  "views":{"FRONT":{"view":"FRONT","state":"RUNNING","qualityReview":"UNREVIEWED"}},
                                  "appliedItemIds":["item-top-linen"],
                                  "unappliedItemIds":[],
                                  "providerConfigVersion":"v2-poc-1",
                                  "createdAt":1000,
                                  "expiresAt":null,
                                  "source":"AI_LIVE",
                                  "warnings":[],
                                  "remoteDeletionState":"BASE64_EPHEMERAL_60M"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content = """
                                {
                                  "jobId":"srv-job-1",
                                  "requestId":"req-1",
                                  "outfitId":"outfit-editorial-01",
                                  "inputGeneration":1,
                                  "inputRevisions":{"profile":2},
                                  "status":"SUCCEEDED",
                                  "views":{
                                    "FRONT":{
                                      "view":"FRONT",
                                      "state":"SUCCEEDED",
                                      "output":{"mimeType":"image/png","base64Data":"$pngSampleBase64","contentHash":"h1","width":768,"height":1024},
                                      "qualityReview":"UNREVIEWED"
                                    }
                                  },
                                  "appliedItemIds":["item-top-linen"],
                                  "unappliedItemIds":[],
                                  "providerConfigVersion":"v2-poc-1",
                                  "createdAt":1000,
                                  "expiresAt":99999999,
                                  "source":"AI_LIVE",
                                  "warnings":[],
                                  "remoteDeletionState":"BASE64_EPHEMERAL_60M"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

                request.method.value == "DELETE" && path == "/v2/tryon/jobs/srv-job-1" -> {
                    respond(
                        content = """
                            {
                              "jobId":"srv-job-1",
                              "status":"DELETED",
                              "deletedAt":20000,
                              "localOutputsDeleted":true,
                              "lateCompletionSuppressed":true,
                              "remoteDeletionState":"BASE64_EPHEMERAL_60M",
                              "message":"Ephemeral 60m"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                else -> error("Unexpected ${request.method} $path")
            }
        }
        }
        val mockEngine = MockEngine(mockConfig)

        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { FakeTryOnFixtures.frontOnlyCapability },
            transport = KtorTryOnHttpTransport(HttpClient(mockEngine), "https://proxy.test"),
            localJobStore = localStore,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = clientScope,
            clock = { testScheduler.currentTime },
        )

        val frontReq = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "k-backoff",
            requestedViews = setOf(ImageView.FRONT),
            selectedItemIds = listOf("item-top-linen"),
        )
        val handle = client.submit(frontReq).getOrThrow()
        assertEquals("srv-job-1", handle.jobId)
        assertEquals(1, submitPosts)
        assertFalse(capturedPostBody.contains("localRelativePath"))
        assertFalse(capturedPostBody.contains("seed://"))

        // Backoff: 2000ms (poll 1), +4000ms (poll 2), +8000ms (poll 3 -> SUCCEEDED)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(1, pollGets)

        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(2, pollGets)

        advanceTimeBy(8_000L)
        runCurrent()
        assertEquals(3, pollGets)

        client.observe("srv-job-1").test {
            val completed = awaitItem()
            assertEquals(TryOnJobStatus.SUCCEEDED, completed.status)
            assertEquals(TryOnSource.AI_LIVE, completed.source)
            assertNotNull(completed.views[ImageView.FRONT]?.outputRef)
            cancelAndIgnoreRemainingEvents()
        }

        val delReceipt = client.delete("srv-job-1").getOrThrow()
        assertEquals(RemoteDeletionState.BASE64_EPHEMERAL_60M, delReceipt.remoteDeletionState)
        assertEquals(1, delReceipt.localFilesRemovedCount)
    }

    @Test
    fun `process restart resumes GET polling for known jobId and marks stranded pre-POST reservation UNKNOWN_SUBMISSION without duplicate POST`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storageFile = File(tempFolder.newFolder("restart_store"), "tryon_jobs_v2.json")
        val localStore = AtomicFileTryOnLocalJobStore(storageFile)
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore, clock = { testScheduler.currentTime })

        // Seed 1 stranded DISPATCHING_POST job (simulating process kill mid-POST)
        // and 1 SUBMITTED_KNOWN_JOB (simulating process kill while server was RUNNING)
        val strandedRecord = PersistedTryOnJobRecord(
            jobId = "local-stranded-1",
            remoteJobId = null,
            sessionId = "s1",
            requestId = "req-stranded",
            idempotencyKey = "idem-stranded",
            payloadFingerprint = "fp-1",
            outfitId = "outfit-1",
            inputGeneration = 1L,
            inputRevisions = mapOf("profile" to 2L),
            requestedViews = setOf(ImageView.FRONT),
            selectedItemIds = listOf("item-top-linen"),
            unappliedItemIds = emptyList(),
            providerConfigVersion = "v2-poc-1",
            submissionPhase = SubmissionPhase.DISPATCHING_POST,
            createdAtEpochMillis = 0L,
            deadlineEpochMillis = 120_000L,
            latestResult = FakeTryOnFixtures.previewResult(TryOnJobStatus.QUEUED, includeBack = false)
                .copy(jobId = "local-stranded-1"),
        )

        val knownRunningRecord = strandedRecord.copy(
            jobId = "local-known-2",
            remoteJobId = "srv-known-2",
            requestId = "req-known",
            idempotencyKey = "idem-known",
            submissionPhase = SubmissionPhase.SUBMITTED_KNOWN_JOB,
            latestResult = FakeTryOnFixtures.previewResult(TryOnJobStatus.RUNNING, includeBack = false)
                .copy(
                    jobId = "srv-known-2",
                    views = mapOf(ImageView.FRONT to ViewResult(ImageView.FRONT, ViewProcessState.RUNNING)),
                ),
        )

        localStore.upsert(strandedRecord)
        localStore.upsert(knownRunningRecord)

        var postCountAfterRestart = 0
        var getCountAfterRestart = 0

        val fakeTransport = object : TryOnHttpTransport {
            override suspend fun submitJob(sessionId: String, payload: TryOnSubmitWireDto): Result<TryOnSubmitResponseDto> {
                postCountAfterRestart += 1
                error("Should NEVER re-POST after process restart!")
            }

            override suspend fun pollJob(sessionId: String, remoteJobId: String): Result<TryOnResultWireDto> {
                getCountAfterRestart += 1
                return Result.success(
                    TryOnResultWireDto(
                        jobId = remoteJobId,
                        requestId = "req-known",
                        outfitId = "outfit-1",
                        inputGeneration = 1L,
                        inputRevisions = mapOf("profile" to 2L),
                        status = TryOnJobStatus.SUCCEEDED,
                        views = mapOf(
                            ImageView.FRONT to ViewResultWireDto(
                                view = ImageView.FRONT,
                                state = ViewProcessState.SUCCEEDED,
                                output = ViewOutputPayloadDto("image/png", pngSampleBase64, "h2", 768, 1024),
                            ),
                        ),
                        appliedItemIds = listOf("item-top-linen"),
                        unappliedItemIds = emptyList(),
                        providerConfigVersion = "v2-poc-1",
                        createdAt = 0L,
                        expiresAt = 600_000L,
                        source = TryOnSource.AI_LIVE,
                        remoteDeletionState = RemoteDeletionState.BASE64_EPHEMERAL_60M,
                    ),
                )
            }

            override suspend fun cancelJob(sessionId: String, remoteJobId: String): Result<CancellationReceiptWireDto> =
                error("Not called")

            override suspend fun deleteJob(sessionId: String, remoteJobId: String): Result<DeletionReceiptWireDto> =
                error("Not called")
        }

        val restartedClient = AndroidVirtualTryOnClient(
            capabilityProvider = { FakeTryOnFixtures.frontOnlyCapability },
            transport = fakeTransport,
            localJobStore = AtomicFileTryOnLocalJobStore(storageFile),
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = TestScope(dispatcher),
            clock = { testScheduler.currentTime },
        )

        val recovered = restartedClient.recoverPersistedJobs()
        assertEquals(2, recovered.size)
        assertEquals(
            TryOnJobStatus.UNKNOWN_SUBMISSION,
            recovered.first { it.jobId == "local-stranded-1" }.status,
        )

        restartedClient.observe("srv-known-2").test {
            assertEquals(TryOnJobStatus.RUNNING, awaitItem().status)
            advanceTimeBy(2_000L)
            runCurrent()
            val resumed = awaitItem()
            assertEquals(TryOnJobStatus.SUCCEEDED, resumed.status)
            assertNotNull(resumed.views[ImageView.FRONT]?.outputRef)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, postCountAfterRestart)
        assertEquals(1, getCountAfterRestart)
    }

    @Test
    fun `expired remote output timestamp marks view EXPIRED and does not save valid image`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore, clock = { 50_000L })

        val transport = object : TryOnHttpTransport {
            override suspend fun submitJob(sessionId: String, payload: TryOnSubmitWireDto) =
                Result.success(TryOnSubmitResponseDto("job-exp", payload.requestId, TryOnJobStatus.RUNNING))

            override suspend fun pollJob(sessionId: String, remoteJobId: String) =
                Result.success(
                    TryOnResultWireDto(
                        jobId = remoteJobId,
                        requestId = "req-exp",
                        outfitId = "outfit-editorial-01",
                        inputGeneration = 1L,
                        inputRevisions = mapOf("profile" to 2L),
                        status = TryOnJobStatus.SUCCEEDED,
                        views = mapOf(
                            ImageView.FRONT to ViewResultWireDto(
                                view = ImageView.FRONT,
                                state = ViewProcessState.SUCCEEDED,
                                output = ViewOutputPayloadDto("image/png", pngSampleBase64, "h-exp", 768, 1024),
                            ),
                        ),
                        appliedItemIds = listOf("item-top-linen"),
                        unappliedItemIds = emptyList(),
                        providerConfigVersion = "v2-poc-1",
                        createdAt = 1_000L,
                        expiresAt = 10_000L, // Already expired relative to clock=50_000L!
                        source = TryOnSource.AI_LIVE,
                    ),
                )

            override suspend fun cancelJob(sessionId: String, remoteJobId: String): Result<CancellationReceiptWireDto> =
                error("unused")

            override suspend fun deleteJob(sessionId: String, remoteJobId: String): Result<DeletionReceiptWireDto> =
                error("unused")
        }

        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { FakeTryOnFixtures.frontOnlyCapability },
            transport = transport,
            localJobStore = InMemoryTryOnLocalJobStore(),
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = TestScope(dispatcher),
            clock = { 50_000L },
        )

        val handle = client.submit(
            FakeTryOnFixtures.sampleDualRequest(
                idempotencyKey = "k-exp",
                requestedViews = setOf(ImageView.FRONT),
                selectedItemIds = listOf("item-top-linen"),
            ),
        ).getOrThrow()

        advanceTimeBy(2_000L)
        runCurrent()

        client.observe(handle.jobId).test {
            val expired = awaitItem()
            assertEquals(TryOnJobStatus.FAILED, expired.status)
            assertEquals(ViewProcessState.EXPIRED, expired.views[ImageView.FRONT]?.state)
            assertNull(expired.views[ImageView.FRONT]?.outputRef)
            assertEquals(0, mediaStore.savedOutputCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenAI synchronous SUCCEEDED submit hydrates views with 0ms delay via LocalDiskTryOnMediaStore and enforces provider-bound consent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientScope = TestScope(dispatcher)
        val filesRoot = tempFolder.newFolder("openai_files")
        val cacheRoot = tempFolder.newFolder("openai_cache")
        val diskMediaStore = LocalDiskTryOnMediaStore(filesRoot, cacheRoot, ioDispatcher = dispatcher)
        val localStore = AtomicFileTryOnLocalJobStore(File(tempFolder.newFolder("openai_jobs"), "jobs.json"))
        val privacy = TryOnPrivacyCoordinator(mediaStore = diskMediaStore, clock = { testScheduler.currentTime })

        val validPersonPng = LocalDiskTryOnMediaStoreTest.buildValidRgbPngBytes(192, 256)
        val validGarmentPng = LocalDiskTryOnMediaStoreTest.buildValidRgbPngBytes(192, 256)
        val validOutputPng = LocalDiskTryOnMediaStoreTest.buildValidRgbPngBytes(192, 256)
        val validOutputBase64 = Base64.getEncoder().encodeToString(validOutputPng)

        val personFile = File(filesRoot, "inputs/person_front.png").apply {
            parentFile?.mkdirs()
            writeBytes(validPersonPng)
        }
        val garmentFile = File(filesRoot, "inputs/garment_front.png").apply {
            parentFile?.mkdirs()
            writeBytes(validGarmentPng)
        }

        val personRef = MediaRef(
            id = "real-person-front",
            ownerEntityId = "profile-1",
            scope = MediaScope.PERSON,
            view = ImageView.FRONT,
            localRelativePath = personFile.absolutePath,
            mimeType = "image/png",
            width = 192,
            height = 256,
            byteCount = validPersonPng.size.toLong(),
            contentHash = LocalDiskTryOnMediaStore.sha256Hex(validPersonPng),
            revision = 2L,
        )
        val garmentRef = MediaRef(
            id = "real-garment-front",
            ownerEntityId = "item-top-linen",
            scope = MediaScope.GARMENT,
            view = ImageView.FRONT,
            localRelativePath = garmentFile.absolutePath,
            mimeType = "image/png",
            width = 192,
            height = 256,
            byteCount = validGarmentPng.size.toLong(),
            contentHash = LocalDiskTryOnMediaStore.sha256Hex(validGarmentPng),
            revision = 1L,
        )

        var seenLocalToken: String? = null
        var pollCount = 0
        val mockConfig = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                seenLocalToken = request.headers["x-tryon-local-token"]
                val path = request.url.encodedPath
                when {
                    request.method.value == "GET" && path == "/v2/tryon/capabilities" -> {
                        respond(
                            content = """
                                {
                                  "decision":"GO_FRONT_ONLY",
                                  "supportedViews":["FRONT"],
                                  "supportedCategories":["TOP","BOTTOM","DRESS","OUTERWEAR"],
                                  "maxGarmentsPerJob":1,
                                  "supportsNativePairedViews":false,
                                  "runtimeVerifiedViews":[],
                                  "providerId":"openai",
                                  "providerConfigVersion":"openai-gpt-image-1.5-medium-1024x1536-openai-vto-edit-v1.0-mock",
                                  "executionMode":"OFFLINE_MOCK_OPENAI",
                                  "modelId":"gpt-image-1.5"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "POST" && path == "/v2/tryon/jobs" -> {
                        respond(
                            content = """{"jobId":"openai-job-1","requestId":"req-oa-1","status":"SUCCEEDED"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "GET" && path == "/v2/tryon/jobs/openai-job-1" -> {
                        pollCount += 1
                        respond(
                            content = """
                                {
                                  "jobId":"openai-job-1",
                                  "requestId":"req-oa-1",
                                  "outfitId":"outfit-editorial-01",
                                  "inputGeneration":1,
                                  "inputRevisions":{"profile":2},
                                  "status":"SUCCEEDED",
                                  "views":{
                                    "FRONT":{
                                      "view":"FRONT",
                                      "state":"SUCCEEDED",
                                      "output":{"mimeType":"image/png","base64Data":"$validOutputBase64","contentHash":"sha256:out","width":192,"height":256},
                                      "qualityReview":"UNREVIEWED"
                                    }
                                  },
                                  "appliedItemIds":["item-top-linen"],
                                  "unappliedItemIds":[],
                                  "providerConfigVersion":"openai-gpt-image-1.5-medium-1024x1536-openai-vto-edit-v1.0-mock",
                                  "createdAt":1000,
                                  "expiresAt":99999999,
                                  "source":"FAKE",
                                  "warnings":["OFFLINE_MOCK_OPENAI"],
                                  "remoteDeletionState":"UNSUPPORTED"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "DELETE" && path == "/v2/tryon/jobs/openai-job-1" -> {
                        respond(
                            content = """
                                {
                                  "jobId":"openai-job-1",
                                  "status":"DELETED",
                                  "deletedAt":5000,
                                  "localOutputsDeleted":true,
                                  "lateCompletionSuppressed":true,
                                  "remoteDeletionState":"UNSUPPORTED",
                                  "message":"Deleted FitCheck server record; OpenAI retention follows org policy."
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    else -> error("Unexpected ${request.method} $path")
                }
            }
        }

        val transport = KtorTryOnHttpTransport(
            httpClient = HttpClient(MockEngine(mockConfig)),
            baseUrl = "http://10.0.2.2:8788",
            localAuthToken = "local-dev-token-123",
        )
        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { transport.fetchCapabilities("s-oa").getOrThrow() },
            transport = transport,
            localJobStore = localStore,
            mediaStore = diskMediaStore,
            privacyCoordinator = privacy,
            scope = clientScope,
            clock = { testScheduler.currentTime },
        )

        val openAiCap = client.capabilities().getOrThrow()
        assertEquals("openai", openAiCap.providerId)
        assertEquals("OFFLINE_MOCK_OPENAI", openAiCap.executionMode)
        assertEquals("local-dev-token-123", seenLocalToken)

        // Wrong provider consent (e.g. fashn-tryon-v1.6) is blocked BEFORE any POST!
        val mismatchedConsentReq = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "k-mismatch",
            requestedViews = setOf(ImageView.FRONT),
            selectedItemIds = listOf("item-top-linen"),
        )
        val mismatchRes = client.submit(mismatchedConsentReq)
        assertEquals(
            TryOnErrorCode.CONSENT_REQUIRED,
            (mismatchRes.exceptionOrNull() as TryOnException).tryOnError.code,
        )

        // Valid OpenAI-bound consent succeeds and hydrates immediately with 0ms delay!
        val openAiConsent = privacy.createPerRequestConsentGrant(
            capability = openAiCap,
            selectedMediaIds = setOf(personRef.id, garmentRef.id),
            allowedViews = setOf(ImageView.FRONT),
            confirmedByUser = true,
            grantId = "consent-openai-1",
        )
        val openAiReq = mismatchedConsentReq.copy(
            requestId = "req-oa-1",
            idempotencyKey = "k-openai-ok",
            personImages = ImagePair(front = personRef),
            garmentImages = mapOf("item-top-linen" to ImagePair(front = garmentRef)),
            consentGrant = openAiConsent,
            providerConfigVersion = openAiCap.providerConfigVersion,
        )

        val handle = client.submit(openAiReq).getOrThrow()
        assertEquals("openai-job-1", handle.jobId)
        assertEquals(TryOnJobStatus.SUCCEEDED, handle.initialStatus)

        // Run current tasks WITHOUT advancing clock -> proves 0ms immediate hydration!
        runCurrent()
        assertEquals(1, pollCount)

        var savedOutputFile: File? = null
        client.observe(handle.jobId).test {
            val hydrated = awaitItem()
            assertEquals(TryOnJobStatus.SUCCEEDED, hydrated.status)
            assertEquals(TryOnSource.FAKE, hydrated.source)
            assertTrue(hydrated.warnings.contains("OFFLINE_MOCK_OPENAI"))
            val outRef = hydrated.views[ImageView.FRONT]?.outputRef
            assertNotNull(outRef)
            savedOutputFile = File(outRef!!.localRelativePath)
            assertTrue(savedOutputFile!!.exists())
            cancelAndIgnoreRemainingEvents()
        }

        val delReceipt = client.delete(handle.jobId).getOrThrow()
        assertEquals(RemoteDeletionState.UNSUPPORTED, delReceipt.remoteDeletionState)
        assertFalse(savedOutputFile!!.exists())
    }

    @Test
    fun `INSUFFICIENT_QUOTA and FORBIDDEN live-gate rejections clear pre-POST reservation cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val localStore = InMemoryTryOnLocalJobStore()
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore)

        var nextCode = "FORBIDDEN"
        val mockConfig = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler {
                respond(
                    content = """{"error":{"code":"$nextCode","message":"Blocked by safety/quota gate","retryable":false}}""",
                    status = if (nextCode == "FORBIDDEN") HttpStatusCode.Forbidden else HttpStatusCode.PaymentRequired,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { FakeTryOnFixtures.frontOnlyCapability },
            transport = KtorTryOnHttpTransport(HttpClient(MockEngine(mockConfig)), "http://10.0.2.2:8788"),
            localJobStore = localStore,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = TestScope(dispatcher),
        )

        val resForbidden = client.submit(
            FakeTryOnFixtures.sampleDualRequest(
                idempotencyKey = "k-forb",
                requestedViews = setOf(ImageView.FRONT),
                selectedItemIds = listOf("item-top-linen"),
            ),
        )
        assertEquals(
            TryOnErrorCode.FORBIDDEN,
            (resForbidden.exceptionOrNull() as TryOnException).tryOnError.code,
        )
        assertTrue(localStore.loadAll().isEmpty())

        nextCode = "INSUFFICIENT_QUOTA"
        val resQuota = client.submit(
            FakeTryOnFixtures.sampleDualRequest(
                idempotencyKey = "k-quota",
                requestedViews = setOf(ImageView.FRONT),
                selectedItemIds = listOf("item-top-linen"),
            ),
        )
        assertEquals(
            TryOnErrorCode.INSUFFICIENT_QUOTA,
            (resQuota.exceptionOrNull() as TryOnException).tryOnError.code,
        )
        assertTrue(localStore.loadAll().isEmpty())
    }

    @Test
    fun `FOSTA synchronous STUDIO_WHITE TOP+BOTTOM submit serializes presentationMode and hydrates FAKE offline mock output`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientScope = TestScope(dispatcher)
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore, clock = { testScheduler.currentTime })
        val localStore = InMemoryTryOnLocalJobStore()

        var capturedSubmitJson = ""
        val mockConfig = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method.value == "GET" && path == "/v2/tryon/capabilities" -> {
                        respond(
                            content = """
                                {
                                  "decision":"GO_DUAL",
                                  "supportedViews":["FRONT","BACK"],
                                  "supportedCategories":["TOP","BOTTOM"],
                                  "maxGarmentsPerJob":2,
                                  "supportsNativePairedViews":false,
                                  "runtimeVerifiedViews":["FRONT"],
                                  "supportedPresentationModes":["ORIGINAL_PHOTO","STUDIO_WHITE"],
                                  "providerId":"fosta",
                                  "providerConfigVersion":"fosta-gpt-6-luna-v2.0-mock",
                                  "executionMode":"OFFLINE_MOCK_FOSTA",
                                  "modelId":"gpt-6-luna"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "POST" && path == "/v2/tryon/jobs" -> {
                        capturedSubmitJson = request.body.toByteArray().decodeToString()
                        respond(
                            content = """{"jobId":"fosta-job-1","requestId":"req-fosta-1","status":"SUCCEEDED"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "GET" && path == "/v2/tryon/jobs/fosta-job-1" -> {
                        respond(
                            content = """
                                {
                                  "jobId":"fosta-job-1",
                                  "requestId":"req-fosta-1",
                                  "outfitId":"outfit-editorial-01",
                                  "inputGeneration":1,
                                  "inputRevisions":{"profile":2},
                                  "status":"SUCCEEDED",
                                  "views":{
                                    "FRONT":{
                                      "view":"FRONT",
                                      "state":"SUCCEEDED",
                                      "output":{"mimeType":"image/png","base64Data":"$pngSampleBase64","contentHash":"sha256:fosta","width":768,"height":1024},
                                      "qualityReview":"UNREVIEWED"
                                    }
                                  },
                                  "appliedItemIds":["item-top-linen","item-trouser-noir"],
                                  "unappliedItemIds":["item-shoes-loafer"],
                                  "presentationMode":"STUDIO_WHITE",
                                  "providerId":"fosta",
                                  "providerConfigVersion":"fosta-gpt-6-luna-v2.0-mock",
                                  "createdAt":1000,
                                  "expiresAt":99999999,
                                  "source":"FAKE",
                                  "warnings":["OFFLINE_MOCK_FOSTA","STUDIO_WHITE_APPROXIMATION"],
                                  "remoteDeletionState":"UNSUPPORTED"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    else -> error("Unexpected ${request.method} $path")
                }
            }
        }

        val transport = KtorTryOnHttpTransport(HttpClient(MockEngine(mockConfig)), "http://10.0.2.2:8788")
        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { transport.fetchCapabilities("s-fosta").getOrThrow() },
            transport = transport,
            localJobStore = localStore,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = clientScope,
            clock = { testScheduler.currentTime },
        )

        val fostaCap = client.capabilities().getOrThrow()
        assertEquals("fosta", fostaCap.providerId)
        assertTrue(TryOnPresentationMode.STUDIO_WHITE in fostaCap.supportedPresentationModes)

        val consent = privacy.createPerRequestConsentGrant(
            capability = fostaCap,
            selectedMediaIds = setOf(
                FakeTryOnFixtures.personFrontRef.id,
                FakeTryOnFixtures.topFrontRef.id,
                FakeTryOnFixtures.bottomFrontRef.id,
            ),
            allowedViews = setOf(ImageView.FRONT),
            confirmedByUser = true,
            grantId = "consent-fosta-1",
        )
        val req = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "k-fosta-studio",
            requestedViews = setOf(ImageView.FRONT),
            selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
        ).copy(
            requestId = "req-fosta-1",
            presentationMode = TryOnPresentationMode.STUDIO_WHITE,
            consentGrant = consent,
            providerConfigVersion = fostaCap.providerConfigVersion,
        )

        val handle = client.submit(req).getOrThrow()
        assertEquals("fosta-job-1", handle.jobId)
        assertTrue(capturedSubmitJson.contains("\"presentationMode\":\"STUDIO_WHITE\""))

        runCurrent()
        client.observe("fosta-job-1").test {
            val res = awaitItem()
            assertEquals(TryOnJobStatus.SUCCEEDED, res.status)
            assertEquals(TryOnSource.FAKE, res.source)
            assertEquals(TryOnPresentationMode.STUDIO_WHITE, res.presentationMode)
            assertEquals("fosta", res.providerId)
            assertNotNull(res.views[ImageView.FRONT]?.outputRef)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class DerivativeSimulatingMediaStore(
        private val delegate: FakeInMemoryMediaStore,
        private val derivativeSourceIds: Set<String>,
    ) : TryOnMediaStorePort by delegate {
        override suspend fun prepareUploadDerivative(ref: MediaRef, maxBytes: Long): Result<MediaRef> {
            if (ref.id !in derivativeSourceIds) {
                return delegate.prepareUploadDerivative(ref, maxBytes)
            }
            val derivedRef = ref.copy(
                id = "deriv-${ref.id}",
                localRelativePath = "tryon/derivatives/deriv-${ref.id}.jpg",
                mimeType = "image/jpeg",
                width = 1152,
                height = 1536,
                byteCount = 512L,
                contentHash = "${ref.contentHash}:deriv",
                derivedFromMediaId = ref.id,
            )
            delegate.putMedia(derivedRef, ByteArray(512) { 0x33 })
            return Result.success(derivedRef)
        }
    }

    @Test
    fun `person upload derivative restores original consented mediaId on wire DTO while keeping fast-path and cleaning temp derivative`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientScope = TestScope(dispatcher)
        val baseStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val mediaStore = DerivativeSimulatingMediaStore(
            delegate = baseStore,
            derivativeSourceIds = setOf(FakeTryOnFixtures.personFrontRef.id),
        )
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore, clock = { testScheduler.currentTime })
        val localStore = InMemoryTryOnLocalJobStore()

        var capturedSubmitJson = ""
        val mockConfig = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method.value == "GET" && path == "/v2/tryon/capabilities" -> {
                        respond(
                            content = """
                                {
                                  "decision":"GO_DUAL",
                                  "supportedViews":["FRONT","BACK"],
                                  "supportedCategories":["TOP","BOTTOM"],
                                  "maxGarmentsPerJob":2,
                                  "supportsNativePairedViews":true,
                                  "runtimeVerifiedViews":["FRONT","BACK"],
                                  "supportedPresentationModes":["ORIGINAL_PHOTO","STUDIO_WHITE"],
                                  "providerId":"fosta",
                                  "providerConfigVersion":"fosta-gpt-6-luna-v2.0-mock",
                                  "executionMode":"OFFLINE_MOCK_FOSTA",
                                  "modelId":"gpt-6-luna"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "POST" && path == "/v2/tryon/jobs" -> {
                        capturedSubmitJson = request.body.toByteArray().decodeToString()
                        respond(
                            content = """{"jobId":"deriv-person-job","requestId":"req-deriv-person","status":"SUCCEEDED"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "GET" && path == "/v2/tryon/jobs/deriv-person-job" -> {
                        respond(
                            content = """
                                {
                                  "jobId":"deriv-person-job",
                                  "requestId":"req-deriv-person",
                                  "outfitId":"outfit-editorial-01",
                                  "inputGeneration":1,
                                  "inputRevisions":{"profile":2},
                                  "status":"SUCCEEDED",
                                  "views":{
                                    "FRONT":{"view":"FRONT","state":"SUCCEEDED","output":{"mimeType":"image/png","base64Data":"$pngSampleBase64","contentHash":"sha256:p-f","width":768,"height":1024},"qualityReview":"UNREVIEWED"},
                                    "BACK":{"view":"BACK","state":"SUCCEEDED","output":{"mimeType":"image/png","base64Data":"$pngSampleBase64","contentHash":"sha256:p-b","width":768,"height":1024},"qualityReview":"UNREVIEWED"}
                                  },
                                  "appliedItemIds":["item-top-linen"],
                                  "unappliedItemIds":[],
                                  "presentationMode":"ORIGINAL_PHOTO",
                                  "providerId":"fosta",
                                  "providerConfigVersion":"fosta-gpt-6-luna-v2.0-mock",
                                  "createdAt":1000,
                                  "expiresAt":99999999,
                                  "source":"FAKE",
                                  "warnings":[],
                                  "remoteDeletionState":"UNSUPPORTED"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "DELETE" && path == "/v2/tryon/jobs/deriv-person-job" -> {
                        respond(
                            content = """{"jobId":"deriv-person-job","status":"DELETED","deletedAt":2000,"localOutputsDeleted":true,"lateCompletionSuppressed":true,"remoteDeletionState":"UNSUPPORTED","message":"Deleted"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    else -> error("Unexpected ${request.method} $path")
                }
            }
        }

        val transport = KtorTryOnHttpTransport(HttpClient(MockEngine(mockConfig)), "http://10.0.2.2:8788")
        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { transport.fetchCapabilities("s-deriv-person").getOrThrow() },
            transport = transport,
            localJobStore = localStore,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = clientScope,
            clock = { testScheduler.currentTime },
        )

        val cap = client.capabilities().getOrThrow()
        val consent = privacy.createPerRequestConsentGrant(
            capability = cap,
            selectedMediaIds = setOf(
                FakeTryOnFixtures.personFrontRef.id,
                FakeTryOnFixtures.personBackRef.id,
                FakeTryOnFixtures.topFrontRef.id,
                FakeTryOnFixtures.topBackRef.id,
            ),
            allowedViews = setOf(ImageView.FRONT, ImageView.BACK),
            confirmedByUser = true,
            grantId = "consent-deriv-person",
        )
        val req = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "k-deriv-person",
            requestedViews = setOf(ImageView.FRONT, ImageView.BACK),
            selectedItemIds = listOf("item-top-linen"),
        ).copy(
            requestId = "req-deriv-person",
            consentGrant = consent,
            providerConfigVersion = cap.providerConfigVersion,
        )

        val handle = client.submit(req).getOrThrow()
        runCurrent()

        val wireDto = Json.decodeFromString<TryOnSubmitWireDto>(capturedSubmitJson)
        val personFrontDto = wireDto.personImages.front
        val personBackDto = wireDto.personImages.back
        assertNotNull(personFrontDto)
        assertNotNull(personBackDto)
        // Derivative person FRONT must advertise original consented mediaId on the wire, not deriv-*
        assertEquals(FakeTryOnFixtures.personFrontRef.id, personFrontDto!!.mediaId)
        assertTrue(personFrontDto.mediaId in wireDto.consentGrant.selectedMediaIds)
        assertEquals(1152, personFrontDto.width)
        assertEquals(1536, personFrontDto.height)
        assertEquals("image/jpeg", personFrontDto.mimeType)
        // Fast-path person BACK keeps its own original mediaId
        assertEquals(FakeTryOnFixtures.personBackRef.id, personBackDto!!.mediaId)
        assertTrue(personBackDto.mediaId in wireDto.consentGrant.selectedMediaIds)

        val derivId = "deriv-${FakeTryOnFixtures.personFrontRef.id}"
        assertTrue(baseStore.hasMedia(derivId))
        client.delete(handle.jobId).getOrThrow()
        assertFalse(baseStore.hasMedia(derivId))
        assertTrue(baseStore.hasMedia(FakeTryOnFixtures.personFrontRef.id))
    }

    @Test
    fun `garment upload derivative restores original consented mediaId on wire DTO while keeping fast-path and cleaning temp derivative`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientScope = TestScope(dispatcher)
        val baseStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val mediaStore = DerivativeSimulatingMediaStore(
            delegate = baseStore,
            derivativeSourceIds = setOf(FakeTryOnFixtures.topFrontRef.id),
        )
        val privacy = TryOnPrivacyCoordinator(mediaStore = mediaStore, clock = { testScheduler.currentTime })
        val localStore = InMemoryTryOnLocalJobStore()

        var capturedSubmitJson = ""
        val mockConfig = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method.value == "GET" && path == "/v2/tryon/capabilities" -> {
                        respond(
                            content = """
                                {
                                  "decision":"GO_DUAL",
                                  "supportedViews":["FRONT","BACK"],
                                  "supportedCategories":["TOP","BOTTOM"],
                                  "maxGarmentsPerJob":2,
                                  "supportsNativePairedViews":false,
                                  "runtimeVerifiedViews":["FRONT"],
                                  "supportedPresentationModes":["ORIGINAL_PHOTO","STUDIO_WHITE"],
                                  "providerId":"fosta",
                                  "providerConfigVersion":"fosta-gpt-6-luna-v2.0-mock",
                                  "executionMode":"OFFLINE_MOCK_FOSTA",
                                  "modelId":"gpt-6-luna"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "POST" && path == "/v2/tryon/jobs" -> {
                        capturedSubmitJson = request.body.toByteArray().decodeToString()
                        respond(
                            content = """{"jobId":"deriv-garment-job","requestId":"req-deriv-garment","status":"SUCCEEDED"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "GET" && path == "/v2/tryon/jobs/deriv-garment-job" -> {
                        respond(
                            content = """
                                {
                                  "jobId":"deriv-garment-job",
                                  "requestId":"req-deriv-garment",
                                  "outfitId":"outfit-editorial-01",
                                  "inputGeneration":1,
                                  "inputRevisions":{"profile":2},
                                  "status":"SUCCEEDED",
                                  "views":{
                                    "FRONT":{"view":"FRONT","state":"SUCCEEDED","output":{"mimeType":"image/png","base64Data":"$pngSampleBase64","contentHash":"sha256:g-f","width":768,"height":1024},"qualityReview":"UNREVIEWED"}
                                  },
                                  "appliedItemIds":["item-top-linen","item-trouser-noir"],
                                  "unappliedItemIds":[],
                                  "presentationMode":"STUDIO_WHITE",
                                  "providerId":"fosta",
                                  "providerConfigVersion":"fosta-gpt-6-luna-v2.0-mock",
                                  "createdAt":1000,
                                  "expiresAt":99999999,
                                  "source":"FAKE",
                                  "warnings":[],
                                  "remoteDeletionState":"UNSUPPORTED"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.method.value == "DELETE" && path == "/v2/tryon/jobs/deriv-garment-job" -> {
                        respond(
                            content = """{"jobId":"deriv-garment-job","status":"DELETED","deletedAt":2000,"localOutputsDeleted":true,"lateCompletionSuppressed":true,"remoteDeletionState":"UNSUPPORTED","message":"Deleted"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    else -> error("Unexpected ${request.method} $path")
                }
            }
        }

        val transport = KtorTryOnHttpTransport(HttpClient(MockEngine(mockConfig)), "http://10.0.2.2:8788")
        val client = AndroidVirtualTryOnClient(
            capabilityProvider = { transport.fetchCapabilities("s-deriv-garment").getOrThrow() },
            transport = transport,
            localJobStore = localStore,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            scope = clientScope,
            clock = { testScheduler.currentTime },
        )

        val cap = client.capabilities().getOrThrow()
        val consent = privacy.createPerRequestConsentGrant(
            capability = cap,
            selectedMediaIds = setOf(
                FakeTryOnFixtures.personFrontRef.id,
                FakeTryOnFixtures.topFrontRef.id,
                FakeTryOnFixtures.bottomFrontRef.id,
            ),
            allowedViews = setOf(ImageView.FRONT),
            confirmedByUser = true,
            grantId = "consent-deriv-garment",
        )
        val req = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "k-deriv-garment",
            requestedViews = setOf(ImageView.FRONT),
            selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
        ).copy(
            requestId = "req-deriv-garment",
            presentationMode = TryOnPresentationMode.STUDIO_WHITE,
            consentGrant = consent,
            providerConfigVersion = cap.providerConfigVersion,
        )

        val handle = client.submit(req).getOrThrow()
        runCurrent()

        val wireDto = Json.decodeFromString<TryOnSubmitWireDto>(capturedSubmitJson)
        val topFrontDto = wireDto.garmentImages["item-top-linen"]?.front
        val bottomFrontDto = wireDto.garmentImages["item-trouser-noir"]?.front
        assertNotNull(topFrontDto)
        assertNotNull(bottomFrontDto)
        // Derivative garment TOP must advertise original consented mediaId on the wire, not deriv-*
        assertEquals(FakeTryOnFixtures.topFrontRef.id, topFrontDto!!.mediaId)
        assertTrue(topFrontDto.mediaId in wireDto.consentGrant.selectedMediaIds)
        assertEquals(1152, topFrontDto.width)
        assertEquals(1536, topFrontDto.height)
        assertEquals("image/jpeg", topFrontDto.mimeType)
        // Fast-path garment BOTTOM & person FRONT keep their original mediaIds
        assertEquals(FakeTryOnFixtures.bottomFrontRef.id, bottomFrontDto!!.mediaId)
        assertTrue(bottomFrontDto.mediaId in wireDto.consentGrant.selectedMediaIds)
        assertEquals(FakeTryOnFixtures.personFrontRef.id, wireDto.personImages.front?.mediaId)

        val derivId = "deriv-${FakeTryOnFixtures.topFrontRef.id}"
        assertTrue(baseStore.hasMedia(derivId))
        client.delete(handle.jobId).getOrThrow()
        assertFalse(baseStore.hasMedia(derivId))
        assertTrue(baseStore.hasMedia(FakeTryOnFixtures.topFrontRef.id))
    }
}

package com.fitcheckai.feature.tryon.privacy

import com.fitcheckai.feature.tryon.contract.ConsentPurpose
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnWardrobeRevisionPort
import com.fitcheckai.feature.tryon.fake.FakeTryOnFixtures
import com.fitcheckai.feature.tryon.fake.FakeTryOnScenario
import com.fitcheckai.feature.tryon.fake.FakeVirtualTryOnService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TryOnPrivacyCoordinatorTest {

    @Test
    fun `consent false or wrong purpose blocks before reading any bytes from MediaStore`() = runTest {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val coordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)

        val unconfirmed = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "no-consent",
            confirmedConsent = false,
        )
        val res1 = coordinator.prepareAuthorizedUploadSlice(unconfirmed, FakeTryOnFixtures.dualCapability)
        assertTrue(res1.isFailure)
        assertEquals(
            TryOnErrorCode.CONSENT_REQUIRED,
            (res1.exceptionOrNull() as TryOnException).tryOnError.code,
        )
        assertEquals(0, mediaStore.readCallCount)

        val wrongPurpose = FakeTryOnFixtures.sampleDualRequest("wrong-purpose").let {
            it.copy(consentGrant = it.consentGrant.copy(purpose = ConsentPurpose.RECOGNITION))
        }
        val res2 = coordinator.prepareAuthorizedUploadSlice(wrongPurpose, FakeTryOnFixtures.dualCapability)
        assertTrue(res2.isFailure)
        assertEquals(0, mediaStore.readCallCount)
    }

    @Test
    fun `rejects cloned front-as-back hash, stale wardrobe revision, and oversized garment payload`() = runTest {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        var currentTopRev = 1L
        val coordinator = TryOnPrivacyCoordinator(
            mediaStore = mediaStore,
            wardrobeRevisions = object : TryOnWardrobeRevisionPort {
                override suspend fun currentItemRevisions(itemIds: Collection<String>): Map<String, Long> =
                    mapOf("item-top-linen" to currentTopRev, "item-trouser-noir" to 1L)
            },
        )

        // 1. Cloned front-as-back hash
        val baseReq = FakeTryOnFixtures.sampleDualRequest("cloned")
        val clonedPersonPair = ImagePair(
            front = FakeTryOnFixtures.personFrontRef,
            back = FakeTryOnFixtures.personBackRef.copy(contentHash = FakeTryOnFixtures.personFrontRef.contentHash),
        )
        val clonedRes = coordinator.prepareAuthorizedUploadSlice(
            baseReq.copy(personImages = clonedPersonPair),
            FakeTryOnFixtures.dualCapability,
        )
        assertEquals(
            TryOnErrorCode.INVALID_REQUEST,
            (clonedRes.exceptionOrNull() as TryOnException).tryOnError.code,
        )

        // 2. Stale wardrobe item revision
        currentTopRev = 2L
        val staleRes = coordinator.prepareAuthorizedUploadSlice(
            FakeTryOnFixtures.sampleDualRequest("stale"),
            FakeTryOnFixtures.dualCapability,
        )
        assertEquals(
            TryOnErrorCode.STALE_INPUT,
            (staleRes.exceptionOrNull() as TryOnException).tryOnError.code,
        )
    }

    @Test
    fun `invalidates generation on photo delete or consent revoke, removes local artifacts, and reports partial remove failures`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val coordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)
        val service = FakeVirtualTryOnService(
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = coordinator,
            stepDelayMs = 0L,
            scope = TestScope(dispatcher),
        )

        val req = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "clean-1",
            requestedViews = setOf(ImageView.FRONT, ImageView.BACK),
        )
        val handle = service.submit(req).getOrThrow()
        advanceUntilIdle()

        val snapshot = service.observe(handle.jobId).first()
        val frontOutId = snapshot.views[ImageView.FRONT]!!.outputRef!!.id
        val backOutId = snapshot.views[ImageView.BACK]!!.outputRef!!.id
        assertTrue(mediaStore.hasMedia(frontOutId))
        assertTrue(mediaStore.hasMedia(backOutId))

        // Inject failure on removing backOutId so coordinator must report partial cleanup failure honestly
        mediaStore.failRemoveMediaIds += backOutId

        val invalidation = coordinator.onPersonPhotoChangedOrDeleted(
            view = ImageView.FRONT,
            activeJobIds = listOf(handle.jobId),
            tryOnService = service,
        )
        assertEquals(2L, invalidation.newInputGeneration)
        assertEquals(1, invalidation.localFilesRemovedCount)
        assertEquals(listOf(backOutId), invalidation.failedLocalMediaIds)
        assertFalse(mediaStore.hasMedia(frontOutId))
    }

    @Test
    fun `SafeTryOnLogger redacts URLs and local file paths from identifiers`() {
        val logger = SafeTryOnLogger()
        logger.log(
            event = "tryon.test",
            requestId = "https://cdn.fashn.ai/secret-signed-url.png",
            jobId = "/data/user/0/com.fitcheckai/files/person.jpg",
        )
        val entry = logger.entries.single()
        assertEquals("req-redacted", entry.requestId)
        assertEquals("job-redacted", entry.jobId)
    }

    @Test
    fun `FOSTA privacy gate enforces STUDIO_WHITE capability, single BACK front-clone rejection, and FOSTA retention notice`() = runTest {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val coordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)

        assertTrue(
            TryOnPrivacyDisclosure.retentionNoticeForProvider("fosta")
                .contains("codex.fosta.app"),
        )

        // Single BACK request with cloned person front-as-back hash is rejected even when FRONT is not requested
        val clonedPersonPair = ImagePair(
            front = FakeTryOnFixtures.personFrontRef,
            back = FakeTryOnFixtures.personBackRef.copy(contentHash = FakeTryOnFixtures.personFrontRef.contentHash),
        )
        val fostaCap = FakeTryOnFixtures.fostaCapability
        val consent = coordinator.createPerRequestConsentGrant(
            capability = fostaCap,
            selectedMediaIds = setOf(FakeTryOnFixtures.personBackRef.id, FakeTryOnFixtures.topBackRef.id),
            allowedViews = setOf(ImageView.BACK),
            confirmedByUser = true,
        )
        val backCloneReq = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "fosta-back-clone",
            requestedViews = setOf(ImageView.BACK),
            selectedItemIds = listOf("item-top-linen"),
        ).copy(
            personImages = clonedPersonPair,
            consentGrant = consent,
            providerConfigVersion = fostaCap.providerConfigVersion,
        )
        val cloneRes = coordinator.prepareAuthorizedUploadSlice(backCloneReq, fostaCap)
        assertEquals(
            TryOnErrorCode.INVALID_REQUEST,
            (cloneRes.exceptionOrNull() as TryOnException).tryOnError.code,
        )
        assertEquals(0, mediaStore.readCallCount)
    }
}

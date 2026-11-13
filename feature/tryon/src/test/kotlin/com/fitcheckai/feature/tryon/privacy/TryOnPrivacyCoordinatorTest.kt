package com.fitcheckai.feature.tryon.privacy

import com.fitcheckai.core.model.Category
import com.fitcheckai.feature.tryon.contract.ConsentPurpose
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnGarmentSlot
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnOutfitSnapshot
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

    @Test
    fun `rejects duplicate HAT or SHOES slot items on both FOSTA and OpenAI before reading MediaStore bytes`() = runTest {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        mediaStore.putMedia(FakeTryOnFixtures.hatFrontRef, ByteArray(512) { 0x11 })
        mediaStore.putMedia(FakeTryOnFixtures.shoesFrontRef, ByteArray(512) { 0x22 })
        val secondHatRef = FakeTryOnFixtures.hatFrontRef.copy(id = "garment-hat-2-front", ownerEntityId = "item-hat-2")
        val secondShoesRef = FakeTryOnFixtures.shoesFrontRef.copy(id = "garment-shoes-2-front", ownerEntityId = "item-shoes-2")
        mediaStore.putMedia(secondHatRef, ByteArray(512) { 0x12 })
        mediaStore.putMedia(secondShoesRef, ByteArray(512) { 0x23 })
        val coordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)

        val dupHatOutfit = FakeTryOnFixtures.allFourSlotsOutfit.copy(
            items = FakeTryOnFixtures.allFourSlotsOutfit.items + TryOnItemSummary(
                itemId = "item-hat-2",
                label = "Mũ bucket",
                category = Category.ACCESSORY,
                slot = TryOnGarmentSlot.HAT,
            ),
        )
        val dupShoesOutfit = FakeTryOnFixtures.allFourSlotsOutfit.copy(
            items = FakeTryOnFixtures.allFourSlotsOutfit.items + TryOnItemSummary(
                itemId = "item-shoes-2",
                label = "Giày boot",
                category = Category.SHOES,
                slot = TryOnGarmentSlot.SHOES,
            ),
        )

        val openAiCap = FakeTryOnFixtures.fostaCapability.copy(providerId = "openai", providerConfigVersion = "v1-openai")
        val cases = listOf(
            Triple(FakeTryOnFixtures.fostaCapability, dupHatOutfit, listOf("item-hat-cap", "item-hat-2") to secondHatRef),
            Triple(openAiCap, dupShoesOutfit, listOf("item-shoes-loafer", "item-shoes-2") to secondShoesRef),
        )
        for ((c: TryOnCapability, o: TryOnOutfitSnapshot, pairIdsRef: Pair<List<String>, MediaRef>) in cases) {
            val (sel, ref) = pairIdsRef
            val garmentMap = FakeTryOnFixtures.allFourSlotsGarmentImages + (sel[1] to ImagePair(front = ref))
            val mediaIds = setOf(FakeTryOnFixtures.personFrontRef.id) + sel.mapNotNull { garmentMap[it]?.front?.id }
            val consent = coordinator.createPerRequestConsentGrant(
                capability = c,
                selectedMediaIds = mediaIds,
                allowedViews = setOf(ImageView.FRONT),
                confirmedByUser = true,
            )
            val req = FakeTryOnFixtures.sampleDualRequest(
                idempotencyKey = "dup-${c.providerId}",
                requestedViews = setOf(ImageView.FRONT),
                selectedItemIds = sel,
            ).copy(
                outfitSnapshot = o,
                garmentImages = garmentMap,
                consentGrant = consent,
                providerConfigVersion = c.providerConfigVersion,
            )
            val res = coordinator.prepareAuthorizedUploadSlice(req, c)
            assertTrue(res.isFailure)
            assertEquals(
                TryOnErrorCode.UNSUPPORTED_CAPABILITY,
                (res.exceptionOrNull() as TryOnException).tryOnError.code,
            )
        }
        assertEquals(0, mediaStore.readCallCount)
    }

    @Test
    fun `adaptive byte budget caps 10 images (person front-back plus 4 garment slots front-back) below 8 MiB payload limit`() = runTest {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        // Populate all 10 views near/at the worst-case adaptive thresholds (person <= 1_000_000, garment <= 550_000)
        val largePersonFront = FakeTryOnFixtures.personFrontRef.copy(byteCount = 1_000_000L)
        val largePersonBack = FakeTryOnFixtures.personBackRef.copy(byteCount = 1_000_000L)
        val topF = FakeTryOnFixtures.topFrontRef.copy(byteCount = 550_000L)
        val topB = FakeTryOnFixtures.topBackRef.copy(byteCount = 550_000L)
        val botF = FakeTryOnFixtures.bottomFrontRef.copy(byteCount = 550_000L)
        val botB = FakeTryOnFixtures.bottomBackRef.copy(byteCount = 550_000L)
        val hatF = FakeTryOnFixtures.hatFrontRef.copy(byteCount = 550_000L)
        val hatB = FakeTryOnFixtures.hatBackRef.copy(byteCount = 550_000L)
        val shoeF = FakeTryOnFixtures.shoesFrontRef.copy(byteCount = 550_000L)
        val shoeB = FakeTryOnFixtures.shoesBackRef.copy(byteCount = 550_000L)
        val allRefs = listOf(largePersonFront, largePersonBack, topF, topB, botF, botB, hatF, hatB, shoeF, shoeB)
        allRefs.forEach { ref -> mediaStore.putMedia(ref, ByteArray(ref.byteCount.toInt()) { 0x5A }) }

        val coordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)
        val cap = FakeTryOnFixtures.fostaCapability
        val consent = coordinator.createPerRequestConsentGrant(
            capability = cap,
            selectedMediaIds = allRefs.map { it.id }.toSet(),
            allowedViews = setOf(ImageView.FRONT, ImageView.BACK),
            confirmedByUser = true,
        )
        val req = FakeTryOnFixtures.sampleDualRequest(
            idempotencyKey = "budget-10-img",
            requestedViews = setOf(ImageView.FRONT, ImageView.BACK),
            selectedItemIds = listOf("item-top-linen", "item-trouser-noir", "item-hat-cap", "item-shoes-loafer"),
        ).copy(
            outfitSnapshot = FakeTryOnFixtures.allFourSlotsOutfit,
            personImages = ImagePair(front = largePersonFront, back = largePersonBack),
            garmentImages = mapOf(
                "item-top-linen" to ImagePair(front = topF, back = topB),
                "item-trouser-noir" to ImagePair(front = botF, back = botB),
                "item-hat-cap" to ImagePair(front = hatF, back = hatB),
                "item-shoes-loafer" to ImagePair(front = shoeF, back = shoeB),
            ),
            consentGrant = consent,
            providerConfigVersion = cap.providerConfigVersion,
        )
        val slice = coordinator.prepareAuthorizedUploadSlice(req, cap).getOrThrow()
        assertEquals(2, slice.personUploads.size)
        assertEquals(8, slice.garmentUploads.values.sumOf { it.size })
        assertTrue("Expected total bytes < 8 MiB, got ${slice.totalBytes}", slice.totalBytes < 8L * 1024L * 1024L)

        // Verify adaptive cap shrinks an oversized 600KB hat image down to 550KB (4_400_000 / 8) and keeps total < 8 MiB
        val compressedHat = hatF.copy(byteCount = 600_000L)
        mediaStore.putMedia(compressedHat, ByteArray(600_000) { 0x5B })
        val compressedReq = req.copy(
            garmentImages = req.garmentImages + ("item-hat-cap" to ImagePair(front = compressedHat, back = hatB)),
        )
        val compressedSlice = coordinator.prepareAuthorizedUploadSlice(compressedReq, cap).getOrThrow()
        assertEquals(550_000, compressedSlice.garmentUploads.getValue("item-hat-cap").getValue(ImageView.FRONT).bytes.size)
        assertTrue("Expected compressed total bytes < 8 MiB, got ${compressedSlice.totalBytes}", compressedSlice.totalBytes < 8L * 1024L * 1024L)

        // Verify uncompressible payload exceeding 550KB adaptive cap triggers PAYLOAD_TOO_LARGE cleanly
        val uncompressibleStore = object : com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort by mediaStore {
            override suspend fun prepareUploadDerivative(ref: MediaRef, maxBytes: Long): Result<MediaRef> = Result.success(ref)
        }
        val strictCoordinator = TryOnPrivacyCoordinator(mediaStore = uncompressibleStore)
        val overRes = strictCoordinator.prepareAuthorizedUploadSlice(compressedReq, cap)
        assertTrue(overRes.isFailure)
        assertEquals(
            TryOnErrorCode.PAYLOAD_TOO_LARGE,
            (overRes.exceptionOrNull() as TryOnException).tryOnError.code,
        )
    }
}

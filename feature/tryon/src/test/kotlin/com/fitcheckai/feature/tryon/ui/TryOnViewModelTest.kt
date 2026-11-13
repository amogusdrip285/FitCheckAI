package com.fitcheckai.feature.tryon.ui

import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.TryOnGarmentSlot
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.fake.FakeTryOnFixtures
import com.fitcheckai.feature.tryon.fake.FakeTryOnProfilePort
import com.fitcheckai.feature.tryon.fake.FakeTryOnScenario
import com.fitcheckai.feature.tryon.fake.FakeVirtualTryOnService
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TryOnViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `never auto-submits on screen open and requires explicit consent confirmation`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )
        advanceUntilIdle()

        val initial = vm.uiState.value
        assertNull(initial.activeOrLatestResult)
        assertFalse(initial.consentConfirmed)
        assertFalse(initial.canSubmitTryOn)
        assertEquals(0, mediaStore.readCallCount)

        // Attempting submit without consent produces CONSENT_REQUIRED banner without uploading
        vm.submitTryOn()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.bannerError)
        assertEquals(0, mediaStore.readCallCount)

        // Enabling consent allows submission and completes job
        vm.setConsentConfirmed(true)
        assertTrue(vm.uiState.value.canSubmitTryOn)
        vm.submitTryOn()
        advanceUntilIdle()

        val completed = vm.uiState.value.activeOrLatestResult
        assertNotNull(completed)
        assertEquals(TryOnJobStatus.SUCCEEDED, completed?.status)
    }

    @Test
    fun `disables dual view when person back photo is missing and resets consent when mode changes`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot)
        val fakeService = FakeVirtualTryOnService(
            initialScenario = FakeTryOnScenario.PARTIAL_BACK_FAILED,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = profilePort,
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )
        advanceUntilIdle()

        vm.setConsentConfirmed(true)
        vm.selectViewMode(TryOnViewMode.FRONT_AND_BACK)
        // Changing view scope MUST reset consentConfirmed so user explicitly consents to back upload!
        assertFalse(vm.uiState.value.consentConfirmed)

        // If user deletes person back photo from ProfileStorage, ViewModel falls back to FRONT_ONLY
        profilePort.update(FakeTryOnFixtures.personFrontOnlySnapshot)
        advanceUntilIdle()
        assertEquals(TryOnViewMode.FRONT_ONLY, vm.uiState.value.selectedViewMode)
        assertFalse(vm.uiState.value.canSelectDualView)

        // Restore dual photos, confirm consent, submit partial-back-failed scenario, then delete result
        profilePort.update(FakeTryOnFixtures.personDualSnapshot)
        advanceUntilIdle()
        vm.selectViewMode(TryOnViewMode.FRONT_AND_BACK)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()

        assertEquals(TryOnJobStatus.PARTIAL, vm.uiState.value.activeOrLatestResult?.status)
        vm.selectResultTab(ImageView.BACK)
        assertEquals(ImageView.BACK, vm.uiState.value.activeResultTab)

        vm.deleteLatestResult()
        advanceUntilIdle()
        assertNull(vm.uiState.value.activeOrLatestResult)
        assertNotNull(vm.uiState.value.statusNotice)
    }

    @Test
    fun `FOSTA capability supports ORIGINAL_PHOTO and STUDIO_WHITE, TOP BOTTOM TOP+BOTTOM combos, and BACK_ONLY with real rear inputs`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = profilePort,
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )
        advanceUntilIdle()

        // Default FOSTA photo-driven selection applies all slots that currently have photos (TOP + BOTTOM)
        val init = vm.uiState.value
        assertTrue(init.isFostaProvider)
        assertTrue(init.isFakeEnvironment)
        assertEquals(TryOnViewMode.FRONT_ONLY, init.selectedViewMode)
        assertEquals(TryOnPresentationMode.ORIGINAL_PHOTO, init.selectedPresentationMode)
        assertEquals(listOf("item-top-linen", "item-trouser-noir"), init.selectedItemIds)
        assertEquals("Sẽ thử: Áo + Quần", init.tryOnSummaryText)
        assertTrue(init.hasExperimentalSelection)

        // Switching to STUDIO_WHITE resets consentConfirmed
        vm.setConsentConfirmed(true)
        vm.selectPresentationMode(TryOnPresentationMode.STUDIO_WHITE)
        assertEquals(TryOnPresentationMode.STUDIO_WHITE, vm.uiState.value.selectedPresentationMode)
        assertFalse(vm.uiState.value.consentConfirmed)

        // Removing TOP slot leaves only BOTTOM active, marks single non-TOP experimental, and resets consent
        vm.setConsentConfirmed(true)
        vm.removeGarmentSlot(TryOnGarmentSlot.TOP)
        assertEquals(listOf("item-trouser-noir"), vm.uiState.value.selectedItemIds)
        assertEquals("Sẽ thử: Quần", vm.uiState.value.tryOnSummaryText)
        assertTrue(vm.uiState.value.hasExperimentalSelection)
        assertFalse(vm.uiState.value.consentConfirmed)

        // Re-adding TOP restores TOP + BOTTOM canonically without experimental notice (1-2 basic slots ok vs 3+ or SHOES/HAT)
        vm.updateGarmentImages(FakeTryOnFixtures.defaultGarmentImages)
        assertEquals(listOf("item-top-linen", "item-trouser-noir"), vm.uiState.value.selectedItemIds)
        assertEquals("Sẽ thử: Áo + Quần", vm.uiState.value.tryOnSummaryText)

        // Selecting BACK_ONLY with real rear person + rear garment photos succeeds and sets activeResultTab = BACK
        assertTrue(vm.uiState.value.canSelectBackView)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        assertEquals(setOf(ImageView.BACK), vm.uiState.value.requestedViews)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()

        val result = vm.uiState.value.activeOrLatestResult
        assertNotNull(result)
        assertEquals(TryOnJobStatus.SUCCEEDED, result?.status)
        assertEquals(TryOnPresentationMode.STUDIO_WHITE, result?.presentationMode)
        assertEquals("fosta", result?.providerId)
        assertEquals(setOf(ImageView.BACK), result?.views?.keys)
        assertEquals(ImageView.BACK, vm.uiState.value.activeResultTab)
    }

    @Test
    fun `rejects BACK_ONLY when rear garment photo is missing or cloned from front`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        // Only TOP has photos initially (with rear photo) -> BACK_ONLY is unlocked
        val topOnlyGarments = mapOf(
            "item-top-linen" to FakeTryOnFixtures.defaultGarmentImages.getValue("item-top-linen"),
            "item-trouser-noir" to ImagePair(),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = topOnlyGarments,
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.canSelectBackView)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        assertEquals(TryOnViewMode.BACK_ONLY, vm.uiState.value.selectedViewMode)

        // Adding BOTTOM front photo (without back photo) automatically adds BOTTOM and locks BACK_ONLY -> falls back to FRONT_ONLY
        val addedBottomWithoutBack = topOnlyGarments.toMutableMap().apply {
            put("item-trouser-noir", ImagePair(front = FakeTryOnFixtures.bottomFrontRef, back = null))
        }
        vm.updateGarmentImages(addedBottomWithoutBack)
        assertEquals(TryOnViewMode.FRONT_ONLY, vm.uiState.value.selectedViewMode)
        assertFalse(vm.uiState.value.canSelectBackView)
        assertEquals(listOf("Quần"), vm.uiState.value.missingBackSlotLabels)
    }

    @Test
    fun `updateGarmentImages preserves view and presentation mode while invalidating consent and replaced garment result`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )
        advanceUntilIdle()

        vm.selectPresentationMode(TryOnPresentationMode.STUDIO_WHITE)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()

        val firstResult = vm.uiState.value.activeOrLatestResult
        assertNotNull(firstResult)
        val firstOutputId = firstResult?.views?.get(ImageView.BACK)?.outputRef?.id
        assertNotNull(firstOutputId)
        assertTrue(mediaStore.hasMedia(firstOutputId!!))

        // Replace top back photo in place -> active slots, presentation mode, and BACK_ONLY stay intact
        val newTopBackRef = FakeTryOnFixtures.topBackRef.copy(
            id = "top-back-picked-2",
            contentHash = "sha256:new-top-back-2",
            revision = 2L,
        )
        mediaStore.putMedia(newTopBackRef)
        val updatedWithNewTopBack = FakeTryOnFixtures.defaultGarmentImages.toMutableMap().apply {
            put(
                "item-top-linen",
                ImagePair(front = FakeTryOnFixtures.topFrontRef, back = newTopBackRef),
            )
        }
        vm.updateGarmentImages(updatedWithNewTopBack)
        advanceUntilIdle()

        val afterUpdate = vm.uiState.value
        assertEquals(listOf("item-top-linen", "item-trouser-noir"), afterUpdate.selectedItemIds)
        assertEquals(TryOnPresentationMode.STUDIO_WHITE, afterUpdate.selectedPresentationMode)
        assertEquals(TryOnViewMode.BACK_ONLY, afterUpdate.selectedViewMode)
        assertFalse(afterUpdate.consentConfirmed)
        assertNull(afterUpdate.activeOrLatestResult)
        assertFalse(mediaStore.hasMedia(firstOutputId))
        assertEquals(newTopBackRef, afterUpdate.garmentImages["item-top-linen"]?.back)

        // Fresh consent uses new image and completes cleanly
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()
        assertEquals(TryOnJobStatus.SUCCEEDED, vm.uiState.value.activeOrLatestResult?.status)

        // Removing BOTTOM -> runs TOP-only; empty updates on inactive BOTTOM preserve TOP-only result
        vm.removeGarmentSlot(TryOnGarmentSlot.BOTTOM)
        advanceUntilIdle()
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()
        val topOnlyJobId = vm.uiState.value.activeOrLatestResult?.jobId
        assertNotNull(topOnlyJobId)

        val sameActiveMap = mapOf(
            "item-top-linen" to ImagePair(front = FakeTryOnFixtures.topFrontRef, back = newTopBackRef),
            "item-trouser-noir" to ImagePair(),
        )
        vm.updateGarmentImages(sameActiveMap)
        advanceUntilIdle()
        assertEquals(topOnlyJobId, vm.uiState.value.activeOrLatestResult?.jobId)
    }

    @Test
    fun `updateGarmentImages locks BACK view with clear reason when live rear photos are missing and unlocks once real rear photos are provided`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personFrontOnlySnapshot)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability.copy(executionMode = "AI_LIVE"),
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val liveFrontOnlyGarments = mapOf(
            "item-top-linen" to ImagePair(front = FakeTryOnFixtures.topFrontRef, back = null),
            "item-trouser-noir" to ImagePair(front = FakeTryOnFixtures.bottomFrontRef, back = null),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = profilePort,
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = liveFrontOnlyGarments,
        )
        advanceUntilIdle()

        // Missing person back -> BACK_ONLY is locked
        assertFalse(vm.uiState.value.canSelectBackView)
        assertEquals(
            "Chưa có ảnh toàn thân mặt sau trong Hồ sơ cá nhân.",
            vm.uiState.value.backViewUnavailableReason,
        )
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        assertEquals(TryOnViewMode.FRONT_ONLY, vm.uiState.value.selectedViewMode)

        // Add person back photo -> still locked because TOP and BOTTOM lack back photos
        profilePort.update(FakeTryOnFixtures.personDualSnapshot)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSelectBackView)
        assertEquals(listOf("Áo", "Quần"), vm.uiState.value.missingBackSlotLabels)
        assertEquals(
            "Một số món đồ đã chọn chưa có ảnh mặt sau trong Tủ đồ.",
            vm.uiState.value.backViewUnavailableReason,
        )

        // Supply TOP back only -> still locked naming only Quần
        val withTopBackOnly = liveFrontOnlyGarments.toMutableMap().apply {
            put("item-top-linen", ImagePair(front = FakeTryOnFixtures.topFrontRef, back = FakeTryOnFixtures.topBackRef))
        }
        vm.updateGarmentImages(withTopBackOnly)
        assertFalse(vm.uiState.value.canSelectBackView)
        assertEquals(listOf("Quần"), vm.uiState.value.missingBackSlotLabels)
        assertEquals(
            "Một số món đồ đã chọn chưa có ảnh mặt sau trong Tủ đồ.",
            vm.uiState.value.backViewUnavailableReason,
        )

        // Supply BOTTOM back -> BACK_ONLY unlocks!
        val withAllBacks = withTopBackOnly.toMutableMap().apply {
            put("item-trouser-noir", ImagePair(front = FakeTryOnFixtures.bottomFrontRef, back = FakeTryOnFixtures.bottomBackRef))
        }
        vm.updateGarmentImages(withAllBacks)
        assertTrue(vm.uiState.value.canSelectBackView)
        assertNull(vm.uiState.value.backViewUnavailableReason)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        assertEquals(TryOnViewMode.BACK_ONLY, vm.uiState.value.selectedViewMode)

        // Removing BOTTOM back while in BACK_ONLY auto-falls back to FRONT_ONLY cleanly
        vm.updateGarmentImages(withTopBackOnly)
        assertEquals(TryOnViewMode.FRONT_ONLY, vm.uiState.value.selectedViewMode)
        assertFalse(vm.uiState.value.canSelectBackView)
    }

    @Test
    fun `replacing person front photo resets consent and invalidates and deletes prior front result`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val replacedPersonFront = FakeTryOnFixtures.personFrontRef.copy(
            id = "dev-person-front-2",
            contentHash = "sha256:dev-person-front-v2",
            revision = 3L,
        )
        mediaStore.putMedia(replacedPersonFront, ByteArray(1024) { 0x3C })
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = profilePort,
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )
        advanceUntilIdle()

        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()
        val firstResult = vm.uiState.value.activeOrLatestResult
        val firstJobId = firstResult?.jobId
        val firstOutputRef = firstResult?.views?.get(ImageView.FRONT)?.outputRef
        assertNotNull(firstJobId)
        assertNotNull(firstOutputRef)
        assertTrue(mediaStore.hasMedia(firstOutputRef!!.id))
        assertTrue(vm.uiState.value.consentConfirmed)

        // Replace person front photo -> must reset consent and purge prior FRONT result
        profilePort.update(
            FakeTryOnFixtures.personDualSnapshot.copy(
                revision = 3L,
                bodyImages = ImagePair(
                    front = replacedPersonFront,
                    back = FakeTryOnFixtures.personBackRef,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(replacedPersonFront, vm.uiState.value.personProfile?.bodyImages?.front)
        assertFalse(vm.uiState.value.consentConfirmed)
        assertNull(vm.uiState.value.activeOrLatestResult)
        assertEquals(TryOnJobStatus.DELETED, fakeService.observe(firstJobId!!).first().status)
        assertFalse(mediaStore.hasMedia(firstOutputRef.id))
    }

    @Test
    fun `replacing person back photo resets consent and invalidates prior back result while keeping view unlocked`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val replacedPersonBack = FakeTryOnFixtures.personBackRef.copy(
            id = "dev-person-back-2",
            contentHash = "sha256:dev-person-back-v2",
            revision = 3L,
        )
        mediaStore.putMedia(replacedPersonBack, ByteArray(1024) { 0x4D })
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = profilePort,
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )
        advanceUntilIdle()

        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()
        val backResult = vm.uiState.value.activeOrLatestResult
        val backJobId = backResult?.jobId
        val backOutputRef = backResult?.views?.get(ImageView.BACK)?.outputRef
        assertNotNull(backJobId)
        assertNotNull(backOutputRef)
        assertTrue(mediaStore.hasMedia(backOutputRef!!.id))

        // Replace person back photo with another genuine rear photo
        profilePort.update(
            FakeTryOnFixtures.personDualSnapshot.copy(
                revision = 3L,
                bodyImages = ImagePair(
                    front = FakeTryOnFixtures.personFrontRef,
                    back = replacedPersonBack,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(TryOnViewMode.BACK_ONLY, vm.uiState.value.selectedViewMode)
        assertTrue(vm.uiState.value.canSelectBackView)
        assertFalse(vm.uiState.value.consentConfirmed)
        assertNull(vm.uiState.value.activeOrLatestResult)
        assertEquals(TryOnJobStatus.DELETED, fakeService.observe(backJobId!!).first().status)
        assertFalse(mediaStore.hasMedia(backOutputRef.id))
    }

    @Test
    fun `photo-driven 4-slot selection supports all 15 non-empty subsets in canonical order and disables submit at 0 slots`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        mediaStore.putMedia(FakeTryOnFixtures.hatFrontRef, ByteArray(512) { 0x11 })
        mediaStore.putMedia(FakeTryOnFixtures.shoesFrontRef, ByteArray(512) { 0x22 })
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.allFourSlotsOutfit,
            initialGarmentImages = emptyMap(),
        )
        advanceUntilIdle()

        // 0 slots -> summary warns and submit cannot enable even after consent
        assertEquals(emptyList<String>(), vm.uiState.value.selectedItemIds)
        assertEquals("Chọn ít nhất một món", vm.uiState.value.tryOnSummaryText)
        assertFalse(vm.uiState.value.hasShoesImage)
        vm.setConsentConfirmed(true)
        assertFalse(vm.uiState.value.canSubmitTryOn)

        val slotMeta = listOf(
            Triple(TryOnGarmentSlot.TOP, "item-top-linen", "Áo") to ImagePair(front = FakeTryOnFixtures.topFrontRef),
            Triple(TryOnGarmentSlot.BOTTOM, "item-trouser-noir", "Quần") to ImagePair(front = FakeTryOnFixtures.bottomFrontRef),
            Triple(TryOnGarmentSlot.HAT, "item-hat-cap", "Mũ") to ImagePair(front = FakeTryOnFixtures.hatFrontRef),
            Triple(TryOnGarmentSlot.SHOES, "item-shoes-loafer", "Giày") to ImagePair(front = FakeTryOnFixtures.shoesFrontRef),
        )

        // Check all 15 non-empty subsets (bitmask 1..15)
        for (mask in 1..15) {
            val map = linkedMapOf<String, ImagePair>()
            val expectedIds = mutableListOf<String>()
            val expectedNames = mutableListOf<String>()
            var expectShoes = false
            slotMeta.forEachIndexed { idx, (meta, pair) ->
                if ((mask and (1 shl idx)) != 0) {
                    map[meta.second] = pair
                    expectedIds += meta.second
                    expectedNames += meta.third
                    if (meta.first == TryOnGarmentSlot.SHOES) expectShoes = true
                }
            }
            vm.updateGarmentImages(map)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(expectedIds, state.selectedItemIds)
            assertEquals("Sẽ thử: ${expectedNames.joinToString(" + ")}", state.tryOnSummaryText)
            assertEquals(expectShoes, state.hasShoesImage)
            vm.setConsentConfirmed(true)
            assertTrue("Expected canSubmitTryOn for mask=$mask", vm.uiState.value.canSubmitTryOn)
        }
    }

    @Test
    fun `removeGarmentSlot clears slot photos, resets consent, deletes prior result, and toggles shoes hint`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        mediaStore.putMedia(FakeTryOnFixtures.hatFrontRef, ByteArray(512) { 0x11 })
        mediaStore.putMedia(FakeTryOnFixtures.shoesFrontRef, ByteArray(512) { 0x22 })
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability,
            initialScenario = FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.allFourSlotsOutfit,
            initialGarmentImages = FakeTryOnFixtures.allFourSlotsGarmentImages,
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasShoesImage)
        assertEquals("Sẽ thử: Áo + Quần + Mũ + Giày", vm.uiState.value.tryOnSummaryText)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()

        val jobId = vm.uiState.value.activeOrLatestResult?.jobId
        val outId = vm.uiState.value.activeOrLatestResult?.views?.get(ImageView.FRONT)?.outputRef?.id
        assertNotNull(jobId)
        assertNotNull(outId)
        assertTrue(mediaStore.hasMedia(outId!!))

        vm.removeGarmentSlot(TryOnGarmentSlot.SHOES)
        advanceUntilIdle()

        val afterRemove = vm.uiState.value
        assertFalse(afterRemove.hasShoesImage)
        assertEquals("Sẽ thử: Áo + Quần + Mũ", afterRemove.tryOnSummaryText)
        assertTrue(afterRemove.garmentImages["item-shoes-loafer"] == null || afterRemove.garmentImages["item-shoes-loafer"] == ImagePair())
        assertFalse("item-shoes-loafer" in afterRemove.selectedItemIds)
        assertFalse(afterRemove.consentConfirmed)
        assertNull(afterRemove.activeOrLatestResult)
        assertEquals(TryOnJobStatus.DELETED, fakeService.observe(jobId!!).first().status)
        assertFalse(mediaStore.hasMedia(outId))
    }

    @Test
    fun `missingBackSlotLabels explicitly names Mu and Giay when HAT or SHOES lack rear photos`() = runTest(testDispatcher) {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        mediaStore.putMedia(FakeTryOnFixtures.hatFrontRef, ByteArray(512) { 0x11 })
        mediaStore.putMedia(FakeTryOnFixtures.shoesFrontRef, ByteArray(512) { 0x22 })
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            initialCapability = FakeTryOnFixtures.fostaCapability.copy(executionMode = "AI_LIVE"),
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 0L,
            scope = TestScope(testDispatcher),
        )
        val hatAndShoesFrontOnly = mapOf(
            "item-top-linen" to FakeTryOnFixtures.defaultGarmentImages.getValue("item-top-linen"),
            "item-trouser-noir" to FakeTryOnFixtures.defaultGarmentImages.getValue("item-trouser-noir"),
            "item-hat-cap" to ImagePair(front = FakeTryOnFixtures.hatFrontRef, back = null),
            "item-shoes-loafer" to ImagePair(front = FakeTryOnFixtures.shoesFrontRef, back = null),
        )
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.allFourSlotsOutfit,
            initialGarmentImages = hatAndShoesFrontOnly,
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canSelectBackView)
        assertEquals(listOf("Mũ", "Giày"), vm.uiState.value.missingBackSlotLabels)
        assertEquals(
            "Một số món đồ đã chọn chưa có ảnh mặt sau trong Tủ đồ.",
            vm.uiState.value.backViewUnavailableReason,
        )
    }
}

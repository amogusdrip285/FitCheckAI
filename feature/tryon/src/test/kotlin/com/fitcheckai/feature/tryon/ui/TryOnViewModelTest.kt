package com.fitcheckai.feature.tryon.ui

import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
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

        // Default FOSTA selection is FRONT_ONLY + ORIGINAL_PHOTO + TOP_ONLY (validated single-example baseline)
        val init = vm.uiState.value
        assertTrue(init.isFostaProvider)
        assertTrue(init.isFakeEnvironment)
        assertEquals(TryOnViewMode.FRONT_ONLY, init.selectedViewMode)
        assertEquals(TryOnPresentationMode.ORIGINAL_PHOTO, init.selectedPresentationMode)
        assertEquals(listOf("item-top-linen"), init.selectedItemIds)
        assertEquals(TryOnGarmentCombo.TOP_ONLY, init.activeGarmentCombo)
        assertFalse(init.hasExperimentalSelection)

        // Switching to STUDIO_WHITE resets consentConfirmed
        vm.setConsentConfirmed(true)
        vm.selectPresentationMode(TryOnPresentationMode.STUDIO_WHITE)
        assertEquals(TryOnPresentationMode.STUDIO_WHITE, vm.uiState.value.selectedPresentationMode)
        assertFalse(vm.uiState.value.consentConfirmed)

        // Selecting BOTTOM_ONLY or TOP_AND_BOTTOM marks experimental selection and resets consent
        vm.setConsentConfirmed(true)
        vm.selectGarmentCombo(TryOnGarmentCombo.BOTTOM_ONLY)
        assertEquals(listOf("item-trouser-noir"), vm.uiState.value.selectedItemIds)
        assertEquals(TryOnGarmentCombo.BOTTOM_ONLY, vm.uiState.value.activeGarmentCombo)
        assertTrue(vm.uiState.value.hasExperimentalSelection)
        assertFalse(vm.uiState.value.consentConfirmed)

        vm.selectGarmentCombo(TryOnGarmentCombo.TOP_AND_BOTTOM)
        assertEquals(listOf("item-top-linen", "item-trouser-noir"), vm.uiState.value.selectedItemIds)
        assertEquals(TryOnGarmentCombo.TOP_AND_BOTTOM, vm.uiState.value.activeGarmentCombo)
        assertTrue(vm.uiState.value.hasExperimentalSelection)

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
        // Bottom item lacks rear photo
        val garmentsMissingBottomBack = FakeTryOnFixtures.defaultGarmentImages.toMutableMap().apply {
            put("item-trouser-noir", ImagePair(front = FakeTryOnFixtures.bottomFrontRef, back = null))
        }
        val vm = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = garmentsMissingBottomBack,
        )
        advanceUntilIdle()

        // TOP has rear photo -> BACK_ONLY is allowed for TOP_ONLY
        assertTrue(vm.uiState.value.canSelectBackView)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        assertEquals(TryOnViewMode.BACK_ONLY, vm.uiState.value.selectedViewMode)

        // Switching to TOP_AND_BOTTOM where BOTTOM lacks rear photo auto-fallbacks to FRONT_ONLY!
        vm.selectGarmentCombo(TryOnGarmentCombo.TOP_AND_BOTTOM)
        assertEquals(TryOnViewMode.FRONT_ONLY, vm.uiState.value.selectedViewMode)
        assertFalse(vm.uiState.value.canSelectBackView)
    }

    @Test
    fun `updateGarmentImages preserves selected combo and view mode while invalidating consent and replaced garment result`() = runTest(testDispatcher) {
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
        vm.selectGarmentCombo(TryOnGarmentCombo.TOP_AND_BOTTOM)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()

        val firstResult = vm.uiState.value.activeOrLatestResult
        assertNotNull(firstResult)
        val firstOutputId = firstResult?.views?.get(ImageView.BACK)?.outputRef?.id
        assertNotNull(firstOutputId)
        assertTrue(mediaStore.hasMedia(firstOutputId!!))

        // Replace top back photo in place -> combo, presentation mode, and BACK_ONLY stay intact
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
        assertEquals(TryOnGarmentCombo.TOP_AND_BOTTOM, afterUpdate.activeGarmentCombo)
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

        // When only TOP_ONLY was applied, replacing an unselected BOTTOM item preserves TOP_ONLY result
        vm.selectGarmentCombo(TryOnGarmentCombo.TOP_ONLY)
        vm.setConsentConfirmed(true)
        vm.submitTryOn()
        advanceUntilIdle()
        val topOnlyJobId = vm.uiState.value.activeOrLatestResult?.jobId
        assertNotNull(topOnlyJobId)

        val newBottomBackRef = FakeTryOnFixtures.bottomBackRef.copy(
            id = "bottom-back-picked-2",
            contentHash = "sha256:new-bottom-back-2",
            revision = 2L,
        )
        val updatedUnselectedBottom = updatedWithNewTopBack.toMutableMap().apply {
            put(
                "item-trouser-noir",
                ImagePair(front = FakeTryOnFixtures.bottomFrontRef, back = newBottomBackRef),
            )
        }
        vm.updateGarmentImages(updatedUnselectedBottom)
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

        // Add person back photo -> still locked because garment back photo is missing
        profilePort.update(FakeTryOnFixtures.personDualSnapshot)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSelectBackView)
        assertEquals(
            "Một số món đồ đã chọn chưa có ảnh mặt sau trong Tủ đồ.",
            vm.uiState.value.backViewUnavailableReason,
        )

        // Select TOP_AND_BOTTOM and supply only TOP back -> combo kept, BACK_ONLY still locked
        vm.selectGarmentCombo(TryOnGarmentCombo.TOP_AND_BOTTOM)
        val withTopBackOnly = liveFrontOnlyGarments.toMutableMap().apply {
            put("item-top-linen", ImagePair(front = FakeTryOnFixtures.topFrontRef, back = FakeTryOnFixtures.topBackRef))
        }
        vm.updateGarmentImages(withTopBackOnly)
        assertEquals(TryOnGarmentCombo.TOP_AND_BOTTOM, vm.uiState.value.activeGarmentCombo)
        assertFalse(vm.uiState.value.canSelectBackView)

        // Supply BOTTOM back -> BACK_ONLY unlocks while preserving TOP_AND_BOTTOM
        val withAllBacks = withTopBackOnly.toMutableMap().apply {
            put("item-trouser-noir", ImagePair(front = FakeTryOnFixtures.bottomFrontRef, back = FakeTryOnFixtures.bottomBackRef))
        }
        vm.updateGarmentImages(withAllBacks)
        assertEquals(TryOnGarmentCombo.TOP_AND_BOTTOM, vm.uiState.value.activeGarmentCombo)
        assertTrue(vm.uiState.value.canSelectBackView)
        assertNull(vm.uiState.value.backViewUnavailableReason)
        vm.selectViewMode(TryOnViewMode.BACK_ONLY)
        assertEquals(TryOnViewMode.BACK_ONLY, vm.uiState.value.selectedViewMode)

        // Removing BOTTOM back while in BACK_ONLY auto-falls back to FRONT_ONLY cleanly
        vm.updateGarmentImages(withTopBackOnly)
        assertEquals(TryOnGarmentCombo.TOP_AND_BOTTOM, vm.uiState.value.activeGarmentCombo)
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
}

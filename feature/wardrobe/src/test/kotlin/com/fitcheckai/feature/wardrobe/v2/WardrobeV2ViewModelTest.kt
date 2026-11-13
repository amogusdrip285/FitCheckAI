package com.fitcheckai.feature.wardrobe.v2

import com.fitcheckai.core.ai.AiTimeouts
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.core.model.wardrobe.fakes.*
import com.fitcheckai.core.ui.component.resolveOutfitItems
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardrobeV2ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeWardrobeRepository(emptyList())
    private val media = FakeMediaStore()
    private val recognition = FakeRecognition()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }
    private fun editor(handle: SavedStateHandle = SavedStateHandle(), ai: ClothingRecognition = recognition) =
        WardrobeEditorViewModel(repo, media, ai, handle).also { vm -> stores += ViewModelStore().apply { put("editor", vm) } }
    private fun prepare(vm: WardrobeEditorViewModel) {
        vm.editName("Áo mới"); vm.chooseImage("front-photo", ImageView.FRONT)
    }
    @Test fun `manual save needs front and guards duplicate taps with nullable unknown fields`() = runTest(dispatcher) {
        val vm = editor()
        vm.save(); runCurrent(); assertEquals(0, repo.writeCount)
        prepare(vm); runCurrent()
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, repo.writeCount); assertTrue(vm.state.value.saved)
        assertEquals(Category.UNKNOWN, vm.state.value.item.attributes.category)
        assertNull(vm.state.value.item.attributes.formality)
        assertTrue(recognition.requests.isEmpty())
        vm.save(); runCurrent(); assertEquals(1, repo.writeCount)
    }
    @Test fun `per request consent and manual edits win over late AI`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<RecognitionResult>>()
        recognition.respond = { response.await() }
        val vm = editor(); prepare(vm); runCurrent()
        vm.chooseImage("back-photo", ImageView.BACK); runCurrent()
        vm.recognize(); runCurrent()
        val request = recognition.requests.single()
        assertEquals(vm.state.value.item.images.refs().map { it.id }.toSet(), request.consentGrant.mediaIds)
        assertEquals(request.requestId, request.consentGrant.requestId)
        vm.editAttributes(vm.state.value.item.attributes.copy(category = Category.DRESS), AttributeField.CATEGORY)
        response.complete(Result.success(RecognitionResult(request.requestId, request.itemId, request.inputRevision,
            ClothingAttributes(Category.TOP, listOf(GarmentColor.RED), garmentType = "SHIRT"), source = ResultSource.AI_LIVE)))
        runCurrent()
        assertEquals(Category.DRESS, vm.state.value.item.attributes.category)
        assertEquals(listOf(GarmentColor.RED), vm.state.value.item.attributes.colors)
        assertEquals(AttributeSource.MANUAL, vm.state.value.item.attributes.attributeSources[AttributeField.CATEGORY])
        assertNull(vm.state.value.item.attributes.garmentType)
        assertTrue(AttributeField.GARMENT_TYPE in vm.state.value.item.attributes.needsReviewFields)
    }
    @Test fun `manual type stays searchable after a late AI response and clears when group changes`() = runTest(dispatcher) {
        val vm = editor()
        prepare(vm)
        runCurrent()
        vm.editAttributes(vm.state.value.item.attributes.copy(category = Category.TOP), AttributeField.CATEGORY)
        vm.editAttributes(vm.state.value.item.attributes.copy(garmentType = "SHIRT"), AttributeField.GARMENT_TYPE)
        recognition.respond = { request ->
            Result.success(RecognitionResult(request.requestId, request.itemId, request.inputRevision,
                ClothingAttributes(Category.TOP, garmentType = "POLO"), source = ResultSource.AI_LIVE))
        }
        vm.recognize()
        runCurrent()
        val shirt = vm.state.value.item
        assertEquals("SHIRT", shirt.attributes.garmentType)
        assertEquals(AttributeSource.MANUAL, shirt.attributes.attributeSources[AttributeField.GARMENT_TYPE])
        assertTrue(Filter(query = "ao so mi", garmentType = "SHIRT").matches(shirt))
        assertFalse(Filter(garmentType = "POLO").matches(shirt))

        vm.editAttributes(shirt.attributes.copy(category = Category.BOTTOM), AttributeField.CATEGORY)
        assertNull(vm.state.value.item.attributes.garmentType)
        assertFalse(AttributeField.GARMENT_TYPE in vm.state.value.item.attributes.attributeSources)
    }
    @Test fun `replacement invalidates in flight response and keeps opposite view`() = runTest(dispatcher) {
        recognition.respond = { request ->
            withContext(NonCancellable) { delay(2000) }
            Result.success(RecognitionResult(request.requestId, request.itemId, request.inputRevision,
                ClothingAttributes(Category.SHOES), source = ResultSource.FAKE))
        }
        val vm = editor(); prepare(vm); runCurrent()
        vm.chooseImage("back-photo", ImageView.BACK); runCurrent()
        val originalBack = vm.state.value.item.images.back
        vm.recognize(); runCurrent()
        vm.chooseImage("new-front", ImageView.FRONT); runCurrent()
        advanceTimeBy(2500); runCurrent()
        assertEquals(Category.UNKNOWN, vm.state.value.item.attributes.category)
        assertEquals(originalBack, vm.state.value.item.images.back)
        assertFalse(vm.state.value.recognizing)
    }
    @Test fun `stale consent preview never sends changed photos or provider config`() = runTest(dispatcher) {
        val vm = editor(); prepare(vm); runCurrent()
        val preview = vm.state.value.item.images.refs().associate { it.id to it.view }
        vm.chooseImage("replacement-after-dialog", ImageView.FRONT); runCurrent()
        vm.recognize(vm.capability, preview); runCurrent()
        assertTrue(recognition.requests.isEmpty()); assertNotNull(vm.state.value.error)
        val current = vm.state.value.item.images.refs().associate { it.id to it.view }
        vm.recognize(vm.capability.copy(configVersion = "previous-version"), current); runCurrent()
        assertTrue(recognition.requests.isEmpty())
        vm.recognize(vm.capability, current); runCurrent()
        val request = recognition.requests.single()
        assertEquals(current, request.consentGrant.mediaViews)
        assertEquals(request.inputRevision, request.consentGrant.requestGeneration)
        assertTrue(request.consentGrant.confirmedByUser)
    }
    @Test fun `timeout and unavailable never become fake success`() = runTest(dispatcher) {
        recognition.respond = { delay(AiTimeouts.requestMs + 10_000); error("must time out") }
        val vm = editor(); prepare(vm); runCurrent(); vm.recognize(); runCurrent()
        advanceTimeBy(AiTimeouts.requestMs + 1); runCurrent()
        assertEquals(RecognitionState.FAILED, vm.state.value.item.recognitionState)
        assertFalse(vm.state.value.fakeResult)
        vm.save(); runCurrent(); assertTrue(vm.state.value.saved)
        val unavailable = editor(ai = UnavailableRecognition())
        prepare(unavailable); runCurrent(); unavailable.recognize(); runCurrent()
        assertNotNull(unavailable.state.value.error)
        assertFalse(unavailable.state.value.recognizing)
    }
    @Test fun `front-only consent sends only front while keeping both saved views`() = runTest(dispatcher) {
        val requests = mutableListOf<RecognitionRequest>()
        val frontOnly = object : ClothingRecognition {
            override val capability = RecognitionCapability("DEV front", "fixture-v2", true, false)
            override suspend fun recognize(request: RecognitionRequest): Result<RecognitionResult> {
                request.validate(capability)
                requests += request
                return recognition.respond(request)
            }
        }
        val vm = editor(ai = frontOnly)
        prepare(vm)
        runCurrent()
        vm.chooseImage("back-photo", ImageView.BACK)
        runCurrent()
        val original = vm.state.value.item.images
        val preview = vm.recognitionViews(frontOnly.capability)
        assertEquals(mapOf(original.front!!.id to ImageView.FRONT), preview)
        vm.recognize(frontOnly.capability, preview)
        runCurrent()
        assertNull(requests.single().images.back)
        assertEquals(preview.keys, requests.single().consentGrant.mediaIds)
        vm.save()
        runCurrent()
        assertEquals(original, repo.get(vm.state.value.item.id).getOrThrow()?.images)
    }
    @Test fun `restored pending draft does not auto upload or stay pending`() = runTest(dispatcher) {
        val item = sixFixtures().first().copy(recognitionState = RecognitionState.PENDING)
        val handle = SavedStateHandle(mapOf("v2Draft" to WardrobeCodec.encodeItem(item)))
        val vm = editor(handle); runCurrent()
        assertEquals(RecognitionState.NOT_REQUESTED, vm.state.value.item.recognitionState)
        assertTrue(recognition.requests.isEmpty())
        assertEquals(item.images, vm.state.value.item.images)
    }
    @Test fun `discard removes draft refs but never saved views or data on failed read`() = runTest(dispatcher) {
        val original = repo.upsert(sixFixtures().first()).getOrThrow()
        val vm = editor(SavedStateHandle(mapOf("itemId" to original.id))); runCurrent()
        vm.chooseImage("replacement", ImageView.FRONT); runCurrent()
        val draft = vm.state.value.item.images.front!!
        var closed = false
        repo.failNext = ErrorCode.IO
        vm.discard { closed = true }; runCurrent()
        assertFalse(closed); assertFalse(draft.id in media.removed)
        vm.discard { closed = true }; runCurrent()
        assertTrue(closed); assertTrue(draft.id in media.removed)
        assertFalse(original.images.front!!.id in media.removed)
        assertEquals(original, repo.get(original.id).getOrThrow())
    }
    @Test fun `save conflict preserves draft then explicit reload resolves it`() = runTest(dispatcher) {
        val original = repo.upsert(sixFixtures().first()).getOrThrow()
        val vm = editor(SavedStateHandle(mapOf("itemId" to original.id))); runCurrent()
        vm.editName("My draft")
        val external = repo.upsert(original.copy(name = "Other edit"), 1).getOrThrow()
        vm.save(); runCurrent()
        assertFalse(vm.state.value.saved); assertEquals("My draft", vm.state.value.item.name)
        assertNotNull(vm.state.value.error)
        vm.reload(); runCurrent()
        assertEquals(external, vm.state.value.item)
        assertEquals(2L, vm.state.value.expectedRevision)
    }
    @Test fun `accent filtering and outfit missing stale references use exact IDs`() {
        val item = sixFixtures().first().copy(name = "Áo sơ mi trắng", attributes = ClothingAttributes(Category.TOP, listOf(GarmentColor.WHITE)))
        assertTrue(Filter(query = "ao trang", color = GarmentColor.WHITE).matches(item))
        assertFalse(Filter(category = Category.SHOES).matches(item))
        val snapshot = WardrobeSnapshot(fixtureId("snapshot"), listOf(item))
        val cards = resolveOutfitItems(snapshot, listOf(ItemRevision(item.id, 2), ItemRevision(fixtureId("deleted"), 1)), emptyMap())
        assertTrue(cards[0].stale); assertFalse(cards[0].missing)
        assertTrue(cards[1].missing)
    }
}

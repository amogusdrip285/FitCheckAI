package com.fitcheckai.feature.wardrobe.v2

import androidx.lifecycle.ViewModelStore
import com.fitcheckai.core.imaging.GarmentCutoutProcessor
import com.fitcheckai.core.imaging.SegmentationReadiness
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.core.model.wardrobe.fakes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentationPreviewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val media = FakeMediaStore()
    private val stores = mutableListOf<ViewModelStore>()
    private val original = sixFixtures().first().images.front!!
    private val created = mutableListOf<MediaRef>()
    private var ready: suspend () -> SegmentationReadiness = { SegmentationReadiness.READY }
    private var render: suspend (MediaRef) -> Result<MediaRef> = { ref -> Result.success(output(ref)) }
    private fun output(ref: MediaRef) = fixtureMedia(ref.ownerEntityId, ref.view, "cutout-${created.size}")
        .copy(derivedFromMediaId = ref.id).also { created += it }
    private val processor = object : GarmentCutoutProcessor {
        override suspend fun readiness(requestDownload: Boolean) = ready()
        override suspend fun create(original: MediaRef) = render(original)
    }
    private fun model() = SegmentationPreviewModel(processor, media).also { vm ->
        stores += ViewModelStore().apply { put("preview", vm) }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { stores.forEach(ViewModelStore::clear); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun `unavailable and downloading models do not invent successful previews`() = runTest(dispatcher) {
        for (value in listOf(SegmentationReadiness.UNAVAILABLE, SegmentationReadiness.DOWNLOADING)) {
            ready = { value }; val vm = model(); vm.run(original); runCurrent()
            assertFalse(vm.state.value.busy); assertNull(vm.state.value.path)
            assertEquals(value, vm.state.value.readiness); assertNotNull(vm.state.value.message)
        }
        assertTrue(created.isEmpty()); assertFalse(original.id in media.removed)
    }
    @Test fun `retry and skip release only owned previews and keep the original`() = runTest(dispatcher) {
        val vm = model()
        repeat(3) {
            vm.run(original); vm.state.first { !it.busy }
            assertNotNull(vm.state.value.path)
        }
        runCurrent()
        assertTrue(created.dropLast(1).all { it.id in media.removed })
        assertFalse(created.last().id in media.removed)
        vm.cancel(); runCurrent()
        assertNull(vm.state.value.path); assertTrue(created.all { it.id in media.removed })
        assertFalse(original.id in media.removed)
    }
    @Test fun `cancelled late render cannot overwrite a newer view and is cleaned`() = runTest(dispatcher) {
        var slow = true
        render = { ref ->
            val result = output(ref)
            if (slow) { slow = false; withContext(NonCancellable) { delay(1_000) } }
            Result.success(result)
        }
        val vm = model(); vm.run(original); runCurrent(); vm.cancel(); runCurrent()
        val back = sixFixtures().first().images.back!!
        vm.run(back); vm.state.first { !it.busy }
        val path = vm.state.value.path
        advanceTimeBy(1_001); runCurrent()
        assertEquals(back.id, vm.state.value.originalId); assertEquals(path, vm.state.value.path)
        assertTrue(created.first().id in media.removed); assertFalse(created.last().id in media.removed)
    }
    @Test fun `reset during readiness does not start rendering after the old request finishes`() = runTest(dispatcher) {
        ready = { withContext(NonCancellable) { delay(1_000) }; SegmentationReadiness.READY }
        val vm = model(); vm.run(original); runCurrent(); vm.reset()
        advanceTimeBy(1_001); runCurrent()
        assertEquals(CutoutState(), vm.state.value); assertTrue(created.isEmpty())
    }
    @Test fun `failed or missing output keeps source and releases failed derivative`() = runTest(dispatcher) {
        val vm = model()
        render = { Result.failure(IllegalStateException("no subject")) }
        vm.run(original); runCurrent(); assertNull(vm.state.value.path); assertFalse(vm.state.value.busy)
        render = { ref -> Result.success(output(ref).also { media.missing += it.id }) }
        vm.run(original); vm.state.first { !it.busy }; runCurrent()
        assertTrue(created.single().id in media.removed); assertFalse(original.id in media.removed)
    }
    @Test fun `clearing screen releases the displayed preview`() = runTest(dispatcher) {
        val vm = model(); vm.run(original); vm.state.first { !it.busy }
        stores.single().clear(); runCurrent()
        assertTrue(created.single().id in media.removed); assertFalse(original.id in media.removed)
    }
    @Test fun `processor returning an original is rejected without deleting it`() = runTest(dispatcher) {
        render = { Result.success(it) }
        val vm = model(); vm.run(original); runCurrent()
        assertNull(vm.state.value.path); assertFalse(vm.state.value.busy); assertTrue(media.removed.isEmpty())
    }
}

package com.fitcheckai.feature.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.fitcheckai.core.model.*
import com.fitcheckai.core.model.nav.Routes
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WardrobeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWardrobe()
    private val photos = FakePhotos()
    private val metrics = RecordingMetrics()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun <T : ViewModel> own(vm: T): T = vm.also { stores += ViewModelStore().apply { put("test", vm) } }
    private fun add(handle: SavedStateHandle = SavedStateHandle()) = own(AddItemViewModel(handle, repository, photos, metrics))

    @Test fun `successful flow previews AI form and guards repeated saves`() = runTest(dispatcher) {
        val vm = add()
        vm.selectPhoto("content://photo")
        advanceUntilIdle()
        assertEquals(AddStage.EDITING, vm.state.value.stage)
        assertEquals("/draft/segmented.png", vm.state.value.imagePath)
        vm.edit(vm.state.value.form.copy(name = "Áo sửa", material = "cotton"))
        vm.save(); vm.save()
        advanceUntilIdle()
        assertEquals(1, repository.saved)
        assertEquals(AddStage.SAVED, vm.state.value.stage)
        val corrected = metrics.events.single { it.name == MetricNames.TAG_CORRECTED }
        assertEquals("subCategory,material", corrected.properties["fields"])
        assertTrue(metrics.events.single { it.name == MetricNames.ITEM_ADDED }.properties["duration_ms"]!!.toLong() >= 0)
        assertTrue(photos.discarded.containsAll(listOf("/draft/original.png", "/draft/segmented.png")))
    }

    @Test fun `offline failures still allow manual save of the original`() = runTest(dispatcher) {
        photos.segmentResult = Result.failure(IllegalStateException("model missing"))
        photos.tagResult = Result.failure(IllegalStateException("offline"))
        val vm = add()
        vm.selectPhoto("content://photo")
        advanceUntilIdle()
        assertEquals("/draft/original.png", vm.state.value.imagePath)
        assertEquals(2, vm.state.value.notices.size)
        vm.save(); advanceUntilIdle()
        assertEquals(0, repository.saved)
        assertNotNull(vm.state.value.error)
        vm.edit(AttributeForm.from(ATTRS))
        vm.save(); advanceUntilIdle()
        assertEquals(1, repository.saved)
        assertEquals("manual", metrics.events.single().properties["tag_source"])
    }

    @Test fun `timeouts bound both stages and do not let late AI overwrite edits`() = runTest(dispatcher) {
        photos.segmentDelay = 20_000
        photos.tagDelay = 20_000
        val vm = add()
        vm.selectPhoto("source")
        advanceTimeBy(16_001); runCurrent()
        assertEquals(AddStage.EDITING, vm.state.value.stage)
        vm.edit(AttributeForm.from(ATTRS).copy(name = "Tên riêng"))
        advanceUntilIdle()
        assertEquals("Tên riêng", vm.state.value.form.name)
    }

    @Test fun `skip tag and original choice work without repeating the request`() = runTest(dispatcher) {
        photos.tagDelay = 5_000
        val vm = add()
        vm.selectPhoto("source"); runCurrent()
        assertEquals(AddStage.TAGGING, vm.state.value.stage)
        vm.skipTagging(); runCurrent()
        vm.keepOriginal(true)
        vm.edit(AttributeForm.from(ATTRS))
        vm.save(); advanceUntilIdle()
        assertEquals("/draft/original.png", repository.lastPath)
        assertEquals("manual", metrics.events.single().properties["tag_source"])
    }

    @Test fun `draft and predicted fields survive saved state restoration`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = add(handle)
        vm.selectPhoto("source"); advanceUntilIdle()
        vm.keepOriginal(true)
        vm.edit(vm.state.value.form.copy(name = "Đã sửa"))
        val restored = add(handle)
        assertEquals("Đã sửa", restored.state.value.form.name)
        assertTrue(restored.state.value.keepOriginal)
        restored.save(); advanceUntilIdle()
        assertEquals("subCategory", metrics.events.single { it.name == MetricNames.TAG_CORRECTED }.properties["fields"])
        val saved = add(handle)
        saved.save(); advanceUntilIdle()
        assertEquals(1, repository.saved)
        assertNotNull(saved.state.value.savedId)
    }

    @Test fun `save failure preserves editable draft and emits no success metrics`() = runTest(dispatcher) {
        repository.failWrite = true
        val vm = add()
        vm.selectPhoto("source"); advanceUntilIdle()
        vm.save(); advanceUntilIdle()
        assertEquals(AddStage.EDITING, vm.state.value.stage)
        assertNotNull(vm.state.value.error)
        assertTrue(metrics.events.isEmpty())
        assertFalse(photos.discarded.contains("/draft/original.png"))
        repository.failWrite = false
        vm.save(); advanceUntilIdle()
        assertEquals(AddStage.SAVED, vm.state.value.stage)
    }

    @Test fun `discard during tagging cancels work cleans drafts and invokes navigation once`() = runTest(dispatcher) {
        photos.tagDelay = 9_000
        val vm = add()
        vm.selectPhoto("/camera.jpg", captured = true); runCurrent()
        var callbacks = 0
        vm.discard { callbacks++ }; advanceUntilIdle()
        assertEquals(1, callbacks)
        assertEquals(AddStage.CHOOSE, vm.state.value.stage)
        assertTrue(photos.discarded.containsAll(listOf("/camera.jpg", "/draft/original.png", "/draft/segmented.png")))
        assertEquals(0, repository.saved)
    }

    @Test fun `unreadable source reports an error and can retry`() = runTest(dispatcher) {
        photos.failImport = true
        val vm = add()
        vm.selectPhoto("source"); advanceUntilIdle()
        assertEquals(AddStage.CHOOSE, vm.state.value.stage)
        assertNotNull(vm.state.value.error)
        photos.failImport = false
        vm.selectPhoto("other"); advanceUntilIdle()
        assertEquals(AddStage.EDITING, vm.state.value.stage)
    }

    @Test fun `filters combine category color and season and restore their selection`() = runTest(dispatcher) {
        repository.items.value = listOf(ITEM, ITEM.copy(id = "2", attributes = ATTRS.copy(category = Category.SHOES)))
        val handle = SavedStateHandle()
        val vm = own(WardrobeViewModel(repository, handle))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        vm.filter(WardrobeFilter(Category.TOP, "#ffffff", Season.SUMMER)); runCurrent()
        assertEquals(listOf(ITEM), vm.state.value.items)
        vm.filter(vm.state.value.filter.copy(color = "#000000")); runCurrent()
        assertTrue(vm.state.value.items.isEmpty())
        assertEquals(2, vm.state.value.total)
        assertEquals("#000000", handle.get<String>("color"))
    }

    @Test fun `detail handles update failure successful edit deletion and missing item`() = runTest(dispatcher) {
        repository.items.value = listOf(ITEM)
        val vm = own(ItemDetailViewModel(SavedStateHandle(mapOf(Routes.ARG_ITEM_ID to ITEM.id)), repository))
        runCurrent()
        vm.startEditing()
        vm.edit(vm.state.value.form.copy(name = "Edited"))
        repository.failWrite = true
        vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.editing)
        assertNotNull(vm.state.value.error)
        repository.failWrite = false
        vm.save(); advanceUntilIdle()
        assertEquals("Edited", vm.state.value.item!!.attributes.subCategory)
        assertFalse(vm.state.value.editing)
        vm.delete(); advanceUntilIdle()
        assertTrue(vm.state.value.deleted)
        assertNull(vm.state.value.item)
    }

    @Test fun `search ignores Vietnamese accents combines filters and restores the query`() = runTest(dispatcher) {
        val linen = ITEM.copy(attributes = ATTRS.copy(subCategory = "Áo sơ mi đỏ", material = "Linen"))
        val shoes = ITEM.copy(id = "shoes", attributes = ATTRS.copy(category = Category.SHOES, subCategory = "Giày đỏ"))
        val dress = ITEM.copy(id = "dress", attributes = ATTRS.copy(category = Category.DRESS,
            subCategory = "Đầm đen", pattern = "solid", styleTags = setOf("minimal")))
        repository.items.value = listOf(linen, shoes, dress)
        val handle = SavedStateHandle()
        val vm = own(WardrobeViewModel(repository, handle))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        vm.filter(WardrobeFilter(query = " AO   do ")); runCurrent()
        assertEquals(listOf(linen), vm.state.value.items)
        vm.filter(WardrobeFilter(query = "so mi")); runCurrent()
        assertEquals(listOf(linen), vm.state.value.items)
        vm.filter(vm.state.value.filter.copy(query = "linen", category = Category.SHOES)); runCurrent()
        assertTrue(vm.state.value.items.isEmpty())
        vm.filter(vm.state.value.filter.copy(category = Category.TOP)); runCurrent()
        assertEquals(listOf(linen), vm.state.value.items)
        val restored = own(WardrobeViewModel(repository, handle))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.state.collect {} }
        runCurrent()
        assertEquals("linen", restored.state.value.filter.query)
        assertEquals(listOf(linen), restored.state.value.items)
        restored.filter(WardrobeFilter()); runCurrent()
        assertEquals(3, restored.state.value.items.size)
    }
}

internal val ATTRS = ClothingAttributes(Category.TOP, "Áo thun", listOf("#FFFFFF"))
internal val ITEM = ClothingItem("item", "/saved.png", ATTRS, Instant.EPOCH)

internal class FakeWardrobe : WardrobeRepository {
    val items = MutableStateFlow<List<ClothingItem>>(emptyList())
    var failWrite = false
    var saved = 0
    var lastPath: String? = null
    override fun observeItems() = items
    override suspend fun getItem(id: String) = items.value.find { it.id == id }
    override suspend fun save(imagePath: String, attributes: ClothingAttributes): Result<ClothingItem> {
        if (failWrite) return Result.failure(IllegalStateException("disk full"))
        saved++; lastPath = imagePath
        val item = ITEM.copy(id = "item-$saved", attributes = attributes)
        items.value += item
        return Result.success(item)
    }
    override suspend fun update(item: ClothingItem): Result<Unit> {
        if (failWrite) return Result.failure(IllegalStateException("disk full"))
        items.value = items.value.map { if (it.id == item.id) item else it }
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        if (failWrite) return Result.failure(IllegalStateException("disk full"))
        items.value = items.value.filterNot { it.id == id }; return Result.success(Unit)
    }
    override suspend fun markWorn(itemIds: List<String>, at: Instant) = Result.success(Unit)
}

internal class FakePhotos : PhotoProcessor {
    var segmentResult = Result.success("/draft/segmented.png")
    var tagResult = Result.success(ATTRS)
    var segmentDelay = 0L
    var tagDelay = 0L
    var failImport = false
    val discarded = mutableListOf<String>()
    override suspend fun importSource(uri: String): String {
        check(!failImport); return "/draft/original.png"
    }
    override suspend fun segment(original: String): Result<String> { delay(segmentDelay); return segmentResult }
    override suspend fun tag(image: String): Result<ClothingAttributes> { delay(tagDelay); return tagResult }
    override suspend fun discard(paths: List<String>) { discarded += paths }
    override fun createCaptureFile() = File("/camera.jpg")
}

internal class RecordingMetrics : MetricsTracker {
    val events = mutableListOf<MetricEvent>()
    override fun track(event: MetricEvent) { events += event }
}

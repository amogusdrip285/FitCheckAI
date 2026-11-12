package com.fitcheckai.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.MetricsTracker
import com.fitcheckai.core.model.Occasion
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.OutfitFeedback
import com.fitcheckai.core.model.OutfitService
import com.fitcheckai.core.model.OutfitSource
import com.fitcheckai.core.model.OutfitSuggestions
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.WearLogRepository
import com.fitcheckai.core.model.wardrobe.ClothingItem as VersionedItem
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.fakes.FakeWardrobeRepository
import com.fitcheckai.core.model.wardrobe.fakes.sixFixtures
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val fixtures = sixFixtures()
    private val first = fixtures[0]
    private val second = fixtures[1]
    private val repository = FakeWardrobeRepository(fixtures)
    private val worn = mutableListOf<Outfit>()
    private val occasions = mutableListOf<Occasion>()
    private val legacy = LegacyReader(repository)
    private val wearLog = object : WearLogRepository {
        override fun observeLogs() = flowOf(emptyList<com.fitcheckai.core.model.WearLogEntry>())
        override suspend fun logWear(outfit: Outfit, at: Instant): Result<Unit> {
            worn += outfit
            return Result.success(Unit)
        }
        override suspend fun recordFeedback(feedback: OutfitFeedback) = Result.success(Unit)
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun `edit and delete remove affected cards and stale wear actions`() = runTest(dispatcher) {
        val edited = outfit("edited", first)
        val deleted = outfit("deleted", second)
        val viewModel = create { listOf(edited, deleted) }
        runCurrent()
        assertEquals(listOf(edited, deleted), viewModel.state.value.suggestions?.outfits)
        viewModel.onWear(edited)
        runCurrent()
        assertEquals(edited.id, viewModel.state.value.wornOutfitId)

        repository.upsert(first.copy(name = "Edited item"), first.revision).getOrThrow()
        // Check the action against storage even before UI receives the change.
        viewModel.onWear(edited)
        runCurrent()
        assertEquals(listOf(deleted), viewModel.state.value.suggestions?.outfits)
        assertEquals(setOf(second.id), viewModel.state.value.itemsById.keys)
        assertNull(viewModel.state.value.wornOutfitId)
        assertEquals(listOf(edited), worn)

        repository.delete(second.id, second.revision).getOrThrow()
        runCurrent()
        assertTrue(viewModel.state.value.suggestions!!.outfits.isEmpty())
        assertTrue(viewModel.state.value.suggestions!!.emptyReason!!.contains("Tủ đồ đã thay đổi"))
        assertTrue(viewModel.state.value.itemsById.isEmpty())
        assertEquals(listOf(Occasion.CASUAL), occasions)
    }

    @Test fun `edit during an outstanding suggestion cannot publish the stale outfit`() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val stale = outfit("delayed", first)
        val viewModel = create { completion.await(); listOf(stale) }
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)
        repository.upsert(first.copy(name = "New revision"), first.revision).getOrThrow()
        completion.complete(Unit)
        runCurrent()
        assertTrue(viewModel.state.value.suggestions!!.outfits.isEmpty())
        assertTrue(viewModel.state.value.itemsById.isEmpty())
    }

    @Test fun `a late response cannot replace the latest selected occasion`() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val late = outfit("late", first)
        val work = outfit("work", second)
        val viewModel = create { occasion ->
            if (occasion == Occasion.CASUAL) {
                withContext(NonCancellable) { completion.await() }
                listOf(late)
            } else listOf(work)
        }
        runCurrent()
        viewModel.onOccasionSelected(Occasion.WORK)
        runCurrent()
        completion.complete(Unit)
        runCurrent()
        assertEquals(Occasion.WORK, viewModel.state.value.occasion)
        assertEquals(listOf(work), viewModel.state.value.suggestions?.outfits)
    }

    private fun create(suggest: suspend (Occasion) -> List<Outfit>): TodayViewModel {
        val service = object : OutfitService {
            override suspend fun suggest(occasion: Occasion, focusItemId: String?): OutfitSuggestions {
                occasions += occasion
                return OutfitSuggestions(suggest(occasion), null)
            }
        }
        val viewModel = TodayViewModel(SavedStateHandle(), service, legacy, repository, wearLog, object : MetricsTracker {
            override fun track(event: com.fitcheckai.core.model.MetricEvent) = Unit
        })
        store.put("today", viewModel)
        return viewModel
    }

    private fun outfit(id: String, item: VersionedItem) = Outfit(id, listOf(item.id), "Test outfit", source = OutfitSource.RULES)

    private class LegacyReader(private val repository: FakeWardrobeRepository) : WardrobeRepository {
        override fun observeItems() = repository.observeItems().map { state ->
            (state as? LoadState.Ready)?.value.orEmpty().map(::asLegacy)
        }
        override suspend fun getItem(id: String) = repository.get(id).getOrThrow()?.let(::asLegacy)
        override suspend fun save(imagePath: String, attributes: ClothingAttributes): Result<ClothingItem> = error("Read only")
        override suspend fun update(item: ClothingItem): Result<Unit> = error("Read only")
        override suspend fun delete(id: String): Result<Unit> = error("Read only")
        override suspend fun markWorn(itemIds: List<String>, at: Instant): Result<Unit> = error("Read only")

        private fun asLegacy(item: VersionedItem) = ClothingItem(
            item.id, "/fixture/${item.id}.jpg",
            ClothingAttributes(Category.valueOf(item.attributes.category.name), item.name, emptyList()),
            Instant.ofEpochMilli(item.createdAt),
        )
    }
}

package com.fitcheckai.feature.today

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.fitcheckai.core.ai.fallback.BasicOutfitFallback
import com.fitcheckai.core.imaging.OutfitShare
import com.fitcheckai.core.model.MetricEvent
import com.fitcheckai.core.model.MetricsTracker
import com.fitcheckai.core.model.Outfit as LegacyOutfit
import com.fitcheckai.core.model.OutfitFeedback
import com.fitcheckai.core.model.WearLogEntry
import com.fitcheckai.core.model.WearLogRepository
import com.fitcheckai.core.model.ai.fakes.FakeOutfitRecommendation
import com.fitcheckai.core.model.ai.fakes.RecommendationScenario
import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.core.model.profile.fakes.FakeProfileStorage
import com.fitcheckai.core.model.profile.fakes.fullProfile
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.OutfitRecommendation
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.tryon.InMemoryTryOnSelectionHandoff
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.FakeMediaStore
import com.fitcheckai.core.model.wardrobe.fakes.FakeWardrobeRepository
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.sixFixtures
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val fixtures = sixFixtures()
    private val top = fixtures[0]
    private val bottom = fixtures[1]
    private val dress = fixtures[2]
    private val repository = FakeWardrobeRepository(fixtures)
    private val media = FakeMediaStore()
    private val profile = fullProfile()
    private val fake = FakeOutfitRecommendation(RecommendationScenario.Single)
    private val worn = mutableListOf<LegacyOutfit>()
    private val handoff = InMemoryTryOnSelectionHandoff()
    private val wearLog = object : WearLogRepository {
        override fun observeLogs() = flowOf(emptyList<WearLogEntry>())
        override suspend fun logWear(outfit: LegacyOutfit, at: Instant): Result<Unit> {
            worn += outfit
            return Result.success(Unit)
        }
        override suspend fun recordFeedback(feedback: OutfitFeedback) = Result.success(Unit)
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun `Today uses CT02 snapshot and projection and resolves both views for focused outfits`() = runTest(dispatcher) {
        fake.scenario = RecommendationScenario.Generate
        val viewModel = create(focus = dress.id)
        val shown = viewModel.settled()
        val request = fake.requests.single()
        assertEquals(repository.getSnapshot().getOrThrow().snapshotId, request.wardrobeSnapshotId)
        assertEquals(profile.toProjection(), request.profile)
        assertEquals(dress.id, request.focusItemId)
        assertTrue(shown.outfits.size in 1..5)
        for (card in shown.outfits) {
            assertEquals(OutfitSource.FAKE, card.source)
            val item = card.items.first { it.itemId == dress.id }
            assertEquals(media.displayPath(dress.images.front!!), item.imagePath)
            assertEquals(media.displayPath(dress.images.back!!), item.backImagePath)
        }
    }

    @Test fun `wardrobe edit and delete hide stale cards and reject share and wear`() = runTest(dispatcher) {
        val viewModel = create()
        val first = viewModel.settled().outfits.single()
        viewModel.onWear(first.outfitId)
        runCurrent()
        assertEquals(first.outfitId, viewModel.state.value.wornOutfitId)

        repository.upsert(top.copy(name = "Edited item"), top.revision).getOrThrow()
        val shareError = viewModel.prepareShare(first.outfitId).exceptionOrNull() as WardrobeException
        assertEquals(ErrorCode.STALE_INPUT, shareError.code)
        val invalidated = viewModel.state.first { it.outfits.isEmpty() && !it.isLoading }
        assertTrue(invalidated.message!!.contains("Tủ đồ đã thay đổi"))
        assertNull(invalidated.wornOutfitId)
        viewModel.onWear(first.outfitId)
        runCurrent()
        assertEquals(listOf(first.outfitId), worn.map { it.id })

        viewModel.suggest()
        val replacement = viewModel.state.first { it.outfits.isNotEmpty() }.outfits.single()
        assertTrue(replacement.items.any { it.name == "Edited item" })
        repository.delete(bottom.id, bottom.revision).getOrThrow()
        assertTrue(viewModel.state.first { it.outfits.isEmpty() && !it.isLoading }.message!!.contains("Tủ đồ đã thay đổi"))
    }

    @Test fun `edit during an outstanding suggestion cannot publish the stale outfit`() = runTest(dispatcher) {
        fake.delayMs = 1_000
        val viewModel = create()
        runCurrent()
        assertEquals(1, fake.requests.size)
        repository.upsert(top.copy(name = "New revision"), top.revision).getOrThrow()
        advanceTimeBy(1_001)
        assertTrue(viewModel.settled().outfits.isEmpty())
    }

    @Test fun `missing proxy falls back to a correctly labelled rules outfit while keeping focus`() = runTest(dispatcher) {
        fake.scenario = RecommendationScenario.Failure(ErrorCode.UNSUPPORTED_CAPABILITY)
        val card = create(focus = dress.id).settled().outfits.single()
        assertEquals(OutfitSource.RULES, card.source)
        assertEquals(listOf(dress.id), card.items.map { it.itemId })
    }

    @Test fun `reroll excludes the visible outfit and duplicate taps launch one request`() = runTest(dispatcher) {
        val viewModel = create()
        val card = viewModel.settled().outfits.single()
        val previous = fake.requests.single()
        viewModel.reroll()
        viewModel.reroll()
        val next = viewModel.state.first { state -> state.outfits.any { it.outfitId != card.outfitId } }.outfits.single()
        assertEquals(2, fake.requests.size)
        val reroll = fake.requests.last()
        assertEquals(previous.requestId, reroll.regenerationOfRequestId)
        assertEquals(setOf(outfitSignature(card.items.map { it.itemId })), reroll.excludeOutfitSignatures)
        assertNotEquals(card.items.map { it.itemId }.toSet(), next.items.map { it.itemId }.toSet())
    }

    @Test fun `a missing focus does not send another outfit request`() = runTest(dispatcher) {
        val state = create(focus = fixtureId("deleted-focus")).settled()
        assertTrue(fake.requests.isEmpty())
        assertTrue(state.outfits.isEmpty())
        assertTrue(state.message!!.contains("Món được chọn"))
    }

    @Test fun `an older recommendation cannot replace the selected occasion`() = runTest(dispatcher) {
        val engine = object : OutfitRecommendation by fake {
            override suspend fun recommend(request: OutfitRequest) =
                if (request.occasion == Occasion.CASUAL) withContext(NonCancellable) {
                    delay(1_000)
                    fake.recommend(request)
                } else fake.recommend(request)
        }
        val viewModel = create(engine = engine)
        runCurrent()
        viewModel.onOccasionSelected(Occasion.WORK)
        val work = viewModel.state.first { it.occasion == Occasion.WORK && !it.isLoading }
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(work, viewModel.state.value)
        assertTrue(work.outfits.single().explanation.contains("work"))
    }

    private suspend fun TodayViewModel.settled() = state.first { !it.isLoading }

    @Test fun `try-on hands the current outfit to Try-On and refuses a stale one`() = runTest(dispatcher) {
        val viewModel = create()
        val outfit = viewModel.settled().outfits.single()

        val route = viewModel.prepareTryOn(outfit.outfitId).getOrThrow()
        val selection = handoff.find(outfit.outfitId)!!
        assertEquals(Routes.tryOn(outfit.outfitId, selection.outfit.wardrobeSnapshotId), route)
        assertEquals(selection.outfit.items.map { it.itemId }.toSet(), selection.items.map { it.id }.toSet())
        assertNull(handoff.find("another-outfit"))

        repository.upsert(top.copy(name = "Edited item"), top.revision).getOrThrow()
        val error = viewModel.prepareTryOn(outfit.outfitId).exceptionOrNull() as WardrobeException
        assertEquals(ErrorCode.STALE_INPUT, error.code)
    }

    @Test fun `style, free text and count are sent only when the user applies them`() = runTest(dispatcher) {
        val viewModel = create()
        viewModel.settled()
        assertEquals(1, fake.requests.size)

        viewModel.onStyleToggled("Minimal")
        viewModel.onFreeTextChanged("Trời mưa, cần đi bộ nhiều")
        viewModel.onCountSelected(3)
        runCurrent()
        assertEquals(1, fake.requests.size)
        val draft = viewModel.state.value
        assertEquals(listOf("Minimal"), draft.styleOverrides)
        assertEquals(3, draft.requestedCount)

        viewModel.applyPreferences()
        runCurrent()
        viewModel.settled()
        val sent = fake.requests.last()
        assertEquals(2, fake.requests.size)
        assertEquals(listOf("Minimal"), sent.styleOverrides)
        assertEquals("Trời mưa, cần đi bộ nhiều", sent.freeText)
        assertEquals(3, sent.requestedCount)
        assertEquals("Minimal", viewModel.state.value.styleOverrides.single())
    }

    @Test fun `free text, style and count stay within the request limits`() = runTest(dispatcher) {
        val viewModel = create()
        viewModel.settled()
        viewModel.onFreeTextChanged("x".repeat(600))
        viewModel.onCountSelected(9)
        listOf("Minimal", "Streetwear", "Công sở", "Năng động", "Vintage", "Nữ tính").forEach(viewModel::onStyleToggled)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(500, state.freeText.length)
        assertEquals(5, state.requestedCount)
        assertEquals(5, state.styleOverrides.size)
        viewModel.onStyleToggled("Minimal")
        runCurrent()
        assertFalse("Minimal" in viewModel.state.value.styleOverrides)

        viewModel.applyPreferences()
        runCurrent()
        viewModel.settled()
        fake.requests.last().validate()
    }

    @Test fun `offline recommendation ends loading with a labelled rules outfit`() = runTest(dispatcher) {
        val offline = object : OutfitRecommendation by fake {
            override suspend fun recommend(request: OutfitRequest) =
                Result.failure<com.fitcheckai.core.model.recommendation.RecommendationResult>(
                    WardrobeException(ErrorCode.NETWORK, "offline"),
                )
        }
        val state = create(engine = offline).settled()
        assertFalse(state.isLoading)
        assertEquals(OutfitSource.RULES, state.outfits.single().source)
    }

    @Test fun `an empty wardrobe shows guidance instead of an endless spinner`() = runTest(dispatcher) {
        fixtures.forEach { repository.delete(it.id, it.revision).getOrThrow() }
        val state = create().settled()
        assertTrue(state.outfits.isEmpty())
        assertTrue(state.message!!.contains("Hãy thêm"))
        assertTrue(fake.requests.isEmpty())
    }

    private fun create(focus: String? = null, engine: OutfitRecommendation = fake): TodayViewModel {
        val handle = SavedStateHandle(mapOf(Routes.ARG_FOCUS_ITEM_ID to focus))
        val viewModel = TodayViewModel(
            handle, repository, FakeProfileStorage(profile), media, engine,
            BasicOutfitFallback { 100 }, OutfitShare(ContextWrapper(null), media), wearLog,
            object : MetricsTracker { override fun track(event: MetricEvent) = Unit },
            handoff,
        )
        store.put("today", viewModel)
        return viewModel
    }
}

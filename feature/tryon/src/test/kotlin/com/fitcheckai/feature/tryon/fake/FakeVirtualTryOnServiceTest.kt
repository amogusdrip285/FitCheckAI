package com.fitcheckai.feature.tryon.fake

import app.cash.turbine.test
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.OutfitSource
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.Sprint0ModelInterop
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.ViewProcessState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVirtualTryOnServiceTest {

    @Test
    fun `deduplicates identical idempotencyKey and rejects mutated payload with CONFLICT`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeVirtualTryOnService(
            initialScenario = FakeTryOnScenario.STAY_RUNNING,
            stepDelayMs = 0L,
            scope = TestScope(dispatcher),
        )
        val req1 = FakeTryOnFixtures.sampleDualRequest(idempotencyKey = "idem-dup")
        val handle1 = service.submit(req1).getOrThrow()
        val handle2 = service.submit(req1).getOrThrow()
        assertEquals(handle1.jobId, handle2.jobId)

        val mutated = req1.copy(profileRevision = 99L)
        val conflict = service.submit(mutated)
        assertTrue(conflict.isFailure)
        assertEquals(
            TryOnErrorCode.CONFLICT,
            (conflict.exceptionOrNull() as TryOnException).tryOnError.code,
        )
    }

    @Test
    fun `simulates partial back failure honestly with source FAKE and unapplied shoes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeVirtualTryOnService(
            initialScenario = FakeTryOnScenario.PARTIAL_BACK_FAILED,
            stepDelayMs = 5L,
            scope = TestScope(dispatcher),
        )
        val handle = service.submit(FakeTryOnFixtures.sampleDualRequest("idem-partial")).getOrThrow()
        advanceUntilIdle()

        service.observe(handle.jobId).test {
            val terminal = awaitItem()
            assertEquals(TryOnJobStatus.PARTIAL, terminal.status)
            assertEquals(TryOnSource.FAKE, terminal.source)
            assertEquals(listOf("item-shoes-loafer"), terminal.unappliedItemIds)
            assertEquals(ViewProcessState.SUCCEEDED, terminal.views[ImageView.FRONT]?.state)
            assertEquals(QualityReviewState.UNREVIEWED, terminal.views[ImageView.FRONT]?.qualityReview)
            assertEquals(ViewProcessState.FAILED, terminal.views[ImageView.BACK]?.state)
            assertNull(terminal.views[ImageView.BACK]?.outputRef)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `suppresses late completion when job is deleted before manual gate releases`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val service = FakeVirtualTryOnService(
            initialScenario = FakeTryOnScenario.WAIT_FOR_MANUAL_GATE,
            mediaStore = mediaStore,
            stepDelayMs = 0L,
            scope = TestScope(dispatcher),
        )
        val handle = service.submit(FakeTryOnFixtures.sampleDualRequest("idem-race")).getOrThrow()
        advanceUntilIdle()

        val deleteReceipt = service.delete(handle.jobId).getOrThrow()
        assertTrue(deleteReceipt.lateCompletionSuppressed)
        service.releaseManualGate(handle.jobId)
        advanceUntilIdle()

        service.observe(handle.jobId).test {
            val finalState = awaitItem()
            assertEquals(TryOnJobStatus.DELETED, finalState.status)
            assertNull(finalState.views[ImageView.FRONT]?.outputRef)
            assertNull(finalState.views[ImageView.BACK]?.outputRef)
            assertEquals(0, mediaStore.savedOutputCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bridges Sprint0 Outfit and ClothingItem without reading imagePath as URL`() {
        val top = ClothingItem(
            id = "t1",
            imagePath = "seed://shirt",
            attributes = ClothingAttributes(
                category = Category.TOP,
                subCategory = "Áo thun trắng",
                colors = listOf("#FFFFFF"),
            ),
            createdAt = Instant.EPOCH,
        )
        val sprint0Outfit = Outfit(
            id = "o1",
            itemIds = listOf("t1"),
            explanation = "Gọn gàng",
            source = OutfitSource.RULES,
        )
        val snapshot = Sprint0ModelInterop.toOutfitSnapshot(
            outfit = sprint0Outfit,
            itemsById = mapOf("t1" to top),
            revisionsById = mapOf("t1" to 4L),
        )
        assertEquals("o1", snapshot.outfitId)
        assertEquals(1, snapshot.items.size)
        assertEquals("Áo thun trắng", snapshot.items.first().label)
        assertEquals(4L, snapshot.items.first().revision)
        assertFalse(snapshot.toString().contains("seed://shirt"))
    }
}

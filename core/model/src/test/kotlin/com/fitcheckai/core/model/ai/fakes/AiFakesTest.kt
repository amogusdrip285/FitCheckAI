package com.fitcheckai.core.model.ai.fakes

import com.fitcheckai.core.model.profile.ProfileProjection
import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.RecommendationResult
import com.fitcheckai.core.model.recommendation.ShortfallReason
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.AttributeSource
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ConsentGrant
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.RecognitionCapability
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.ResultSource
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.fixtureMedia
import com.fitcheckai.core.model.wardrobe.fakes.sixFixtures
import com.fitcheckai.core.model.wardrobe.mergeRecognition
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFakesTest {
    private val six = sixFixtures().map { ItemSummary(it.id, it.revision, it.attributes) }
    private val extraTop = ItemSummary(fixtureId("extra-top"), 4, ClothingAttributes(Category.TOP))
    private val unknown = ItemSummary(fixtureId("unknown"), 1, ClothingAttributes(Category.UNKNOWN))
    private fun of(c: Category) = six.first { it.attributes.category == c }

    private fun request(items: List<ItemSummary> = six + extraTop + unknown, count: Int = 5, focus: String? = null,
        exclude: Set<String> = emptySet()) = OutfitRequest(UUID.randomUUID().toString(), fixtureId("snapshot"), items,
        ProfileProjection(), Occasion.WORK, weather = FakeWeather.current(), requestedCount = count,
        focusItemId = focus, excludeOutfitSignatures = exclude)

    private fun Result<*>.code() = (exceptionOrNull() as WardrobeException).code

    /** Kiểm độc lập với fake (cùng luật CT-02 như eval/ai/lib/invariants.mjs). */
    private fun assertInvariants(request: OutfitRequest, result: RecommendationResult) {
        val catalog = request.items.associateBy { it.id }
        assertEquals(request.requestId, result.requestId)
        assertTrue(result.outfits.size in 1..request.requestedCount)
        assertEquals(result.outfits.size < request.requestedCount, result.shortfallReason != null)
        assertEquals(result.outfits.size, result.outfits.map { it.signature }.toSet().size)
        assertEquals(result.outfits.size, result.outfits.map { it.id }.toSet().size)
        result.outfits.forEach { o ->
            assertEquals(OutfitSource.FAKE, o.source)
            assertEquals(request.wardrobeSnapshotId, o.wardrobeSnapshotId)
            assertTrue(o.explanation.isNotBlank() && o.explanation.length <= 500)
            o.items.forEach { ref -> assertEquals(catalog.getValue(ref.itemId).revision, ref.revision) }
            val ids = o.items.map { it.itemId }
            assertEquals(ids.size, ids.toSet().size)
            assertEquals(outfitSignature(ids), o.signature)
            val count = ids.map { catalog.getValue(it).attributes.category }.groupingBy { it }.eachCount()
            assertTrue(Category.UNKNOWN !in count && count.values.all { it == 1 })
            assertTrue(if (Category.DRESS in count) Category.TOP !in count && Category.BOTTOM !in count
                else Category.TOP in count && Category.BOTTOM in count)
            request.focusItemId?.let { assertTrue(it in ids) }
            assertTrue(o.signature !in request.excludeOutfitSignatures)
            UUID.fromString(o.id)
        }
    }

    @Test fun `five valid outfits without shortfall`() = runTest {
        val req = request()
        val result = FakeOutfitRecommendation().recommend(req).getOrThrow()
        assertEquals(5, result.outfits.size)
        assertNull(result.shortfallReason)
        assertInvariants(req, result)
    }

    @Test fun `fewer combinations return what exists with a reason`() = runTest {
        val req = request(items = listOf(of(Category.TOP), of(Category.BOTTOM)))
        val result = FakeOutfitRecommendation().recommend(req).getOrThrow()
        assertEquals(1, result.outfits.size)
        assertEquals(ShortfallReason.NOT_ENOUGH_DISTINCT_COMBINATIONS, result.shortfallReason)
        assertInvariants(req, result)
    }

    @Test fun `single scenario returns exactly one outfit`() = runTest {
        val req = request()
        val result = FakeOutfitRecommendation(RecommendationScenario.Single).recommend(req).getOrThrow()
        assertEquals(1, result.outfits.size)
        assertInvariants(req, result)
    }

    @Test fun `wardrobe without a valid base is INSUFFICIENT_WARDROBE, never empty success`() = runTest {
        val fake = FakeOutfitRecommendation()
        assertEquals(ErrorCode.INSUFFICIENT_WARDROBE, fake.recommend(request(items = listOf(of(Category.TOP), unknown))).code())
        assertEquals(ErrorCode.INSUFFICIENT_WARDROBE, fake.recommend(request(items = listOf(of(Category.SHOES)))).code())
        assertEquals(ErrorCode.INVALID_REQUEST, fake.recommend(request(items = emptyList())).code())
    }

    @Test fun `focus and exclusions are respected`() = runTest {
        val shoes = of(Category.SHOES).id
        val focused = request(focus = shoes)
        val result = FakeOutfitRecommendation().recommend(focused).getOrThrow()
        assertInvariants(focused, result)

        val first = FakeOutfitRecommendation().recommend(request(count = 1)).getOrThrow().outfits.single().signature
        // Exclusion ghi theo thứ tự ID bất kỳ vẫn bị loại.
        val reversed = first.split('|').reversed().joinToString("|")
        val next = request(exclude = setOf(reversed))
        val again = FakeOutfitRecommendation().recommend(next).getOrThrow()
        assertTrue(again.outfits.none { it.signature == first })
    }

    @Test fun `outfit ids are stable for the same request`() = runTest {
        val req = request()
        val fake = FakeOutfitRecommendation()
        assertEquals(fake.recommend(req).getOrThrow().outfits.map { it.id }, fake.recommend(req).getOrThrow().outfits.map { it.id })
    }

    @Test fun `failure scenarios and invalid requests are typed`() = runTest {
        listOf(ErrorCode.INVALID_AI_OUTPUT, ErrorCode.UPSTREAM_TIMEOUT, ErrorCode.RATE_LIMITED).forEach { code ->
            assertEquals(code, FakeOutfitRecommendation(RecommendationScenario.Failure(code)).recommend(request()).code())
        }
        assertEquals(ErrorCode.INVALID_REQUEST, FakeOutfitRecommendation().recommend(request(count = 0)).code())
        assertEquals(ErrorCode.INVALID_REQUEST, FakeOutfitRecommendation().recommend(request().copy(requestId = "r1")).code())
    }

    @Test fun `hang is cancellable`() = runTest {
        val fake = FakeOutfitRecommendation(RecommendationScenario.Hang)
        val call = async { fake.recommend(request()) }
        yield()
        assertEquals(1, fake.requests.size)
        call.cancel()
        assertTrue(runCatching { call.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun `weather fixtures cover current, stale and missing`() {
        assertTrue(!FakeWeather.current().isStale && FakeWeather.stale().isStale)
        assertTrue(FakeWeather.stale().validAt < FakeWeather.current().validAt)
        assertNull(FakeWeather.none)
        request().copy(weather = FakeWeather.none).validate()
    }

    // ---- Recognition ----

    private val itemId = fixtureId("garment")
    private val front = fixtureMedia(itemId, ImageView.FRONT)
    private val back = fixtureMedia(itemId, ImageView.BACK)

    private fun recognition(capability: RecognitionCapability, images: ImagePair = ImagePair(front, back), confirmed: Boolean = true): RecognitionRequest {
        val id = UUID.randomUUID().toString()
        return RecognitionRequest(id, itemId, 2, images, ConsentGrant(requestId = id, provider = capability.provider,
            providerConfigVersion = capability.configVersion, mediaIds = images.refs().map { it.id }.toSet(),
            mediaViews = images.refs().associate { it.id to it.view }, requestGeneration = 2, confirmedByUser = confirmed))
    }

    @Test fun `consent not confirmed is rejected before upload`() = runTest {
        val fake = FakeClothingRecognition()
        assertEquals(ErrorCode.CONSENT_REQUIRED, fake.recognize(recognition(fake.capability, confirmed = false)).code())
        assertTrue(fake.uploaded.isEmpty())
    }

    @Test fun `dual success echoes request and cites both views`() = runTest {
        val fake = FakeClothingRecognition()
        val req = recognition(fake.capability)
        val result = fake.recognize(req).getOrThrow()
        assertEquals(Triple(req.requestId, req.itemId, req.inputRevision), Triple(result.requestId, result.itemId, result.inputRevision))
        assertEquals(ResultSource.FAKE, result.source)
        assertEquals(setOf(ImageView.FRONT, ImageView.BACK), result.viewEvidence[AttributeField.CATEGORY])
        result.suggestedAttributes.validate()
    }

    @Test fun `front-only capability blocks back image, front-only request works`() = runTest {
        val fake = FakeClothingRecognition(supportsBack = false)
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, fake.recognize(recognition(fake.capability)).code())
        val result = fake.recognize(recognition(fake.capability, ImagePair(front))).getOrThrow()
        assertTrue(result.viewEvidence.values.none { ImageView.BACK in it })
        assertEquals(1, fake.uploaded.size)
    }

    @Test fun `conflict marks everything for review and manual edits still win`() = runTest {
        val fake = FakeClothingRecognition(RecognitionScenario.Conflict)
        val result = fake.recognize(recognition(fake.capability)).getOrThrow()
        assertEquals(AttributeField.entries.toSet(), result.needsReviewFields)
        assertEquals(listOf(FAKE_CONFLICT_WARNING), result.warnings)
        val manual = ClothingAttributes(Category.BOTTOM, attributeSources = mapOf(AttributeField.CATEGORY to AttributeSource.MANUAL))
        val merged = mergeRecognition(manual, setOf(AttributeField.CATEGORY), result)
        assertEquals(Category.BOTTOM, merged.category)
    }

    @Test fun `unknown category, failures and hang`() = runTest {
        val unknownResult = FakeClothingRecognition(RecognitionScenario.UnknownCategory).let { it.recognize(recognition(it.capability)).getOrThrow() }
        assertEquals(Category.UNKNOWN, unknownResult.suggestedAttributes.category)
        assertTrue(AttributeField.CATEGORY in unknownResult.needsReviewFields)

        listOf(ErrorCode.UPSTREAM_TIMEOUT, ErrorCode.STALE_INPUT).forEach { code ->
            val fake = FakeClothingRecognition(RecognitionScenario.Failure(code))
            assertEquals(code, fake.recognize(recognition(fake.capability)).code())
        }

        val hanging = FakeClothingRecognition(RecognitionScenario.Hang)
        val started = CompletableDeferred<Unit>()
        val call = async { started.complete(Unit); hanging.recognize(recognition(hanging.capability)) }
        started.await(); yield()
        call.cancel()
        assertTrue(runCatching { call.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
}

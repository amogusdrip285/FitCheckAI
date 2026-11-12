package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.profile.ProfileProjection
import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.ShortfallReason
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.ItemRevision
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyOutfitRecommendationTest {
    private val top = item("top", Category.TOP)
    private val top2 = item("top2", Category.TOP)
    private val bottom = item("bottom", Category.BOTTOM)
    private val dress = item("dress", Category.DRESS)
    private val shoes = item("shoes", Category.SHOES)
    private val unknown = item("unknown", Category.UNKNOWN)
    private val snapshot = fixtureId("snapshot")

    private fun item(label: String, category: Category, revision: Long = 1) =
        ItemSummary(fixtureId(label), revision, ClothingAttributes(category, listOf(GarmentColor.BLACK)))

    private fun request(count: Int = 5, focus: String? = null, exclude: Set<String> = emptySet(), id: String = UUID.randomUUID().toString()) =
        OutfitRequest(id, snapshot, listOf(top, top2, bottom, dress, shoes, unknown),
            ProfileProjection(heightCm = 165.0, stylePreferences = listOf("minimal")), Occasion.WORK,
            freeText = "Tôi thích đơn giản", requestedCount = count, focusItemId = focus, excludeOutfitSignatures = exclude)

    private fun outfitJson(req: OutfitRequest, vararg items: ItemSummary, id: String = UUID.randomUUID().toString(),
        signature: String = items.map { it.id }.sorted().joinToString("|"), source: String = "AI_LIVE",
        revisionOf: (ItemSummary) -> Long = { it.revision }, explanation: String = "Áo đen hợp quần đen cho công sở.") =
        """{"id":"$id","requestId":"${req.requestId}","wardrobeSnapshotId":"${req.wardrobeSnapshotId}",
            "items":[${items.joinToString(",") { """{"itemId":"${it.id}","revision":${revisionOf(it)}}""" }}],
            "explanation":"$explanation","source":"$source","createdAt":1794621600000,"signature":"$signature"}"""

    private fun responseJson(req: OutfitRequest, outfits: List<String>, shortfall: String? = null) =
        """{"requestId":"${req.requestId}","outfits":[${outfits.joinToString(",")}],"shortfallReason":${shortfall?.let { "\"$it\"" } ?: "null"}}"""

    private fun proxyReturning(body: (OutfitRequest) -> String, req: OutfitRequest) = FakeProxy().apply { route = { ok(body(req)) } }

    @Test fun `parses one outfit with shortfall, sends only metadata with auth`() = runTest {
        val req = request()
        val proxy = proxyReturning({ responseJson(it, listOf(outfitJson(it, top, bottom)), "NOT_ENOUGH_DISTINCT_COMBINATIONS") }, req)
        val result = ProxyOutfitRecommendation(proxy.transport).recommend(req).getOrThrow()

        assertEquals(req.requestId, result.requestId)
        assertEquals(ShortfallReason.NOT_ENOUGH_DISTINCT_COMBINATIONS, result.shortfallReason)
        val outfit = result.outfits.single()
        assertEquals(listOf(ItemRevision(top.id, 1), ItemRevision(bottom.id, 1)), outfit.items)
        assertEquals(OutfitSource.AI_LIVE, outfit.source)
        assertEquals(listOf(top.id, bottom.id).sorted().joinToString("|"), outfit.signature)

        val sent = proxy.posts("/v2/outfits").single()
        assertEquals("Bearer $TEST_TOKEN", sent.authorization)
        val json = Json.parseToJsonElement(sent.body).jsonObject
        assertEquals(setOf("heightCm", "stylePreferences", "preferredCategories"), json["profile"]!!.jsonObject.keys)
        assertEquals(6, json["items"]!!.jsonArray.size)
        listOf("weightKg", "imagePath", "localRelativePath", "dataBase64", "bodyImages").forEach { assertFalse(it in sent.body) }
        assertTrue("\"weather\":null" in sent.body) // field bắt buộc có mặt dù null
    }

    @Test fun `parses five distinct outfits without shortfall`() = runTest {
        val req = request()
        val proxy = proxyReturning({ r ->
            responseJson(r, listOf(outfitJson(r, top, bottom), outfitJson(r, top2, bottom), outfitJson(r, dress),
                outfitJson(r, dress, shoes), outfitJson(r, top, bottom, shoes)))
        }, req)
        val result = ProxyOutfitRecommendation(proxy.transport).recommend(req).getOrThrow()
        assertEquals(5, result.outfits.size)
        assertNull(result.shortfallReason)
        assertEquals(5, result.outfits.map { it.signature }.toSet().size)
    }

    @Test fun `session token is reused across requests`() = runTest {
        val proxy = FakeProxy().apply { route = { rec -> ok(responseJson(lastRequest!!, listOf(outfitJson(lastRequest!!, top, bottom)), "CONSTRAINTS_TOO_STRICT")) } }
        val client = ProxyOutfitRecommendation(proxy.transport)
        repeat(2) { lastRequest = request(); client.recommend(lastRequest!!).getOrThrow() }
        assertEquals(1, proxy.posts("/v2/session").size)
        assertEquals(2, proxy.posts("/v2/outfits").size)
    }
    private var lastRequest: OutfitRequest? = null

    @Test fun `any invalid outfit rejects the whole response`() = runTest {
        val stranger = item("stranger", Category.BOTTOM)
        val cases: Map<String, (OutfitRequest) -> String> = mapOf(
            "ID lạ" to { r -> responseJson(r, listOf(outfitJson(r, top, stranger)), "INVALID_OUTFITS_REMOVED") },
            "sai revision" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, revisionOf = { 7 })), "INVALID_OUTFITS_REMOVED") },
            "trùng bộ" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom), outfitJson(r, bottom, top)), "NOT_ENOUGH_DISTINCT_COMBINATIONS") },
            "trùng món trong bộ" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, bottom)), "INVALID_OUTFITS_REMOVED") },
            "DRESS + TOP" to { r -> responseJson(r, listOf(outfitJson(r, dress, top)), "INVALID_OUTFITS_REMOVED") },
            "thiếu BOTTOM" to { r -> responseJson(r, listOf(outfitJson(r, top, shoes)), "INVALID_OUTFITS_REMOVED") },
            "món UNKNOWN" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, unknown)), "INVALID_OUTFITS_REMOVED") },
            "signature sai" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, signature = "x|y")), "INVALID_OUTFITS_REMOVED") },
            "id không phải UUID" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, id = "o1")), "INVALID_OUTFITS_REMOVED") },
            "source RULES" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, source = "RULES")), "INVALID_OUTFITS_REMOVED") },
            "giải thích rỗng" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom, explanation = "  ")), "INVALID_OUTFITS_REMOVED") },
            "ít hơn nhưng thiếu reason" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom))) },
            "reason lạ" to { r -> responseJson(r, listOf(outfitJson(r, top, bottom)), "SOMETHING_NEW") },
            "success rỗng" to { r -> responseJson(r, emptyList(), "CONSTRAINTS_TOO_STRICT") },
            "requestId khác" to { r -> responseJson(r.copy(requestId = UUID.randomUUID().toString()), listOf(outfitJson(r, top, bottom)), "INVALID_OUTFITS_REMOVED") },
            "JSON hỏng" to { _ -> """{"requestId":""" },
            "thiếu field" to { r -> """{"requestId":"${r.requestId}","outfits":[{"id":"x"}]}""" },
        )
        cases.forEach { (name, body) ->
            val req = request()
            val result = ProxyOutfitRecommendation(proxyReturning(body, req).transport).recommend(req)
            assertEquals(name, ErrorCode.INVALID_AI_OUTPUT, (result.exceptionOrNull() as? com.fitcheckai.core.model.wardrobe.WardrobeException)?.code)
        }
    }

    @Test fun `more outfits than requested and full count with reason are invalid`() = runTest {
        val two = request(count = 1)
        ProxyOutfitRecommendation(proxyReturning({ r -> responseJson(r, listOf(outfitJson(r, top, bottom), outfitJson(r, dress))) }, two).transport)
            .recommend(two).assertFailure(ErrorCode.INVALID_AI_OUTPUT)
        val one = request(count = 1)
        ProxyOutfitRecommendation(proxyReturning({ r -> responseJson(r, listOf(outfitJson(r, dress)), "CONSTRAINTS_TOO_STRICT") }, one).transport)
            .recommend(one).assertFailure(ErrorCode.INVALID_AI_OUTPUT)
    }

    @Test fun `focus and exclusions are enforced on the response`() = runTest {
        val focused = request(focus = shoes.id)
        ProxyOutfitRecommendation(proxyReturning({ r -> responseJson(r, listOf(outfitJson(r, top, bottom)), "CONSTRAINTS_TOO_STRICT") }, focused).transport)
            .recommend(focused).assertFailure(ErrorCode.INVALID_AI_OUTPUT)

        // Exclusion gửi theo thứ tự bất kỳ vẫn được chuẩn hoá trước khi so.
        val excluded = request(exclude = setOf(listOf(top.id, bottom.id).sortedDescending().joinToString("|")))
        val proxy = proxyReturning({ r -> responseJson(r, listOf(outfitJson(r, top, bottom)), "CONSTRAINTS_TOO_STRICT") }, excluded)
        ProxyOutfitRecommendation(proxy.transport).recommend(excluded).assertFailure(ErrorCode.INVALID_AI_OUTPUT)
        val wire = Json.parseToJsonElement(proxy.posts("/v2/outfits").single().body).jsonObject
        assertEquals(listOf(top.id, bottom.id).sorted().joinToString("|"),
            wire["excludeOutfitSignatures"]!!.jsonArray.single().toString().trim('"'))
    }

    @Test fun `server errors map to typed codes`() = runTest {
        val cases = listOf(
            Triple(409, "INSUFFICIENT_WARDROBE", ErrorCode.INSUFFICIENT_WARDROBE),
            Triple(429, "RATE_LIMITED", ErrorCode.RATE_LIMITED),
            Triple(503, "BUDGET_EXCEEDED", ErrorCode.BUDGET_EXCEEDED),
            Triple(502, "INVALID_AI_OUTPUT", ErrorCode.INVALID_AI_OUTPUT),
            Triple(504, "UPSTREAM_TIMEOUT", ErrorCode.UPSTREAM_TIMEOUT),
            Triple(503, "SOMETHING_NEW", ErrorCode.UNSUPPORTED_CAPABILITY),
            Triple(500, "NETWORK", ErrorCode.UPSTREAM_ERROR), // mã local mà server phát ⇒ không tin
        )
        cases.forEach { (status, code, expected) ->
            val proxy = FakeProxy().apply { route = { errorJson(status, code) } }
            val result = ProxyOutfitRecommendation(proxy.transport).recommend(request())
            assertEquals(code, expected, (result.exceptionOrNull() as com.fitcheckai.core.model.wardrobe.WardrobeException).code)
        }
        val garbage = FakeProxy().apply { route = { ok("<html>bad gateway</html>", HttpStatusCode.BadGateway) } }
        ProxyOutfitRecommendation(garbage.transport).recommend(request()).assertFailure(ErrorCode.UPSTREAM_ERROR)
    }

    @Test fun `timeout becomes typed failure without auto retry`() = runTest {
        val proxy = FakeProxy(deadlineMs = 200).apply { route = { delay(5_000); ok("{}") } }
        ProxyOutfitRecommendation(proxy.transport).recommend(request()).assertFailure(ErrorCode.UPSTREAM_TIMEOUT)
        assertEquals(1, proxy.posts("/v2/outfits").size)
    }

    @Test fun `network failure becomes NETWORK`() = runTest {
        val proxy = FakeProxy().apply { route = { throw IOException("connection reset") } }
        ProxyOutfitRecommendation(proxy.transport).recommend(request()).assertFailure(ErrorCode.NETWORK)
    }

    @Test fun `caller cancellation propagates and never delivers a result`() = runTest {
        val started = CompletableDeferred<Unit>()
        val proxy = FakeProxy(deadlineMs = 10_000).apply { route = { started.complete(Unit); delay(5_000); ok("{}") } }
        val call = async { ProxyOutfitRecommendation(proxy.transport).recommend(request()) }
        started.await()
        call.cancel()
        assertTrue(runCatching { call.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertTrue(call.isCancelled)
    }

    @Test fun `invalid or unconfigured request never reaches the network`() = runTest {
        val unconfigured = FakeProxy(accessCode = "")
        ProxyOutfitRecommendation(unconfigured.transport).recommend(request()).assertFailure(ErrorCode.UNSUPPORTED_CAPABILITY)
        assertTrue(unconfigured.calls.isEmpty())

        val proxy = FakeProxy()
        val client = ProxyOutfitRecommendation(proxy.transport)
        client.recommend(request(count = 0)).assertFailure(ErrorCode.INVALID_REQUEST)
        client.recommend(request().copy(requestId = "not-a-uuid")).assertFailure(ErrorCode.INVALID_REQUEST)
        client.recommend(request(focus = unknown.id)).assertFailure(ErrorCode.UNSUPPORTED_CAPABILITY)
        client.recommend(request().copy(freeText = "x".repeat(501))).assertFailure(ErrorCode.INVALID_REQUEST)
        assertTrue(proxy.calls.isEmpty())
    }

    @Test fun `same requestId is sent at most once`() = runTest {
        val req = request()
        val proxy = proxyReturning({ r -> responseJson(r, listOf(outfitJson(r, top, bottom)), "CONSTRAINTS_TOO_STRICT") }, req)
        val client = ProxyOutfitRecommendation(proxy.transport)
        client.recommend(req).getOrThrow()
        client.recommend(req).assertFailure(ErrorCode.CONFLICT)
        assertEquals(1, proxy.posts("/v2/outfits").size)
    }

    @Test fun `401 drops the session so the next user retry re-authenticates`() = runTest {
        var reject = true
        val proxy = FakeProxy().apply { route = { if (reject) errorJson(401, "UNAUTHORIZED") else ok(responseJson(lastRequest!!, listOf(outfitJson(lastRequest!!, dress)), "CONSTRAINTS_TOO_STRICT")) } }
        val client = ProxyOutfitRecommendation(proxy.transport)
        client.recommend(request()).assertFailure(ErrorCode.UNAUTHORIZED)
        reject = false
        lastRequest = request()
        client.recommend(lastRequest!!).getOrThrow()
        assertEquals(2, proxy.posts("/v2/session").size)
    }

    @Test fun `capability advertises reroll and focus`() {
        val capability = ProxyOutfitRecommendation(FakeProxy().transport).capability
        assertTrue(capability.supportsReroll && capability.supportsFocus)
        assertEquals(OutfitSource.AI_LIVE, capability.source)
    }

    @Test fun `rerollAfter keeps constraints, links the previous request and excludes what was shown`() {
        val first = request(focus = top.id, exclude = setOf("${bottom.id}|${top2.id}"))
        val shown = com.fitcheckai.core.model.recommendation.RecommendationResult(first.requestId, listOf(
            outfitOf(first, top, bottom), outfitOf(first, top, bottom, shoes)), ShortfallReason.CONSTRAINTS_TOO_STRICT)
        val next = first.rerollAfter(shown, newRequestId = UUID.randomUUID().toString())

        assertTrue(next.requestId != first.requestId)
        assertEquals(first.requestId, next.regenerationOfRequestId)
        assertEquals(listOf(
            listOf(top.id, bottom.id).sorted().joinToString("|"),
            listOf(top.id, bottom.id, shoes.id).sorted().joinToString("|"),
            listOf(top2.id, bottom.id).sorted().joinToString("|"),
        ), next.excludeOutfitSignatures.toList())
        assertEquals(first.copy(requestId = next.requestId, regenerationOfRequestId = first.requestId,
            excludeOutfitSignatures = next.excludeOutfitSignatures), next)
        next.validate()

        // Tối đa 5 loại trừ, ưu tiên các bộ vừa hiển thị.
        val many = request(exclude = (1..5).map { "old$it|x$it" }.toSet())
        val five = com.fitcheckai.core.model.recommendation.RecommendationResult(many.requestId, listOf(outfitOf(many, dress)), null)
        val capped = many.rerollAfter(five)
        assertEquals(5, capped.excludeOutfitSignatures.size)
        assertEquals(dress.id, capped.excludeOutfitSignatures.first())
    }

    @Test fun `reroll goes out once with exclusions and no-alternative is a typed failure`() = runTest {
        val first = request()
        val shown = com.fitcheckai.core.model.recommendation.RecommendationResult(first.requestId, listOf(outfitOf(first, top, bottom)),
            ShortfallReason.NOT_ENOUGH_DISTINCT_COMBINATIONS)
        val proxy = FakeProxy().apply { route = { errorJson(409, "INSUFFICIENT_WARDROBE") } }
        val reroll = first.rerollAfter(shown)
        ProxyOutfitRecommendation(proxy.transport).recommend(reroll).assertFailure(ErrorCode.INSUFFICIENT_WARDROBE)
        val wire = Json.parseToJsonElement(proxy.posts("/v2/outfits").single().body).jsonObject
        assertEquals("\"${first.requestId}\"", wire["regenerationOfRequestId"].toString())
        assertEquals(1, wire["excludeOutfitSignatures"]!!.jsonArray.size)
        assertEquals(1, proxy.posts("/v2/outfits").size) // không tự gọi lại
    }

    private fun outfitOf(req: OutfitRequest, vararg items: ItemSummary) = com.fitcheckai.core.model.recommendation.Outfit(
        UUID.randomUUID().toString(), req.requestId, req.wardrobeSnapshotId, items.map { ItemRevision(it.id, it.revision) },
        "x", OutfitSource.AI_LIVE, 1)

    @Test fun `long material is dropped and invalid weather is sent as null`() = runTest {
        val longMaterial = top.copy(attributes = top.attributes.copy(material = "m".repeat(60)))
        val req = request().copy(items = listOf(longMaterial, bottom),
            weather = com.fitcheckai.core.model.recommendation.WeatherSnapshot("Hà Nội", 99.0, "nắng", 1, 1, "x", false))
        val body = encodeOutfitRequest(req)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("null", json["items"]!!.jsonArray[0].jsonObject["attributes"]!!.jsonObject["material"].toString())
        assertEquals("null", json["weather"].toString())
    }
}

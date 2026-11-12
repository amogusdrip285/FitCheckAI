package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ConsentGrant
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.RecognitionCapability
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.ResultSource
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.fixtureMedia
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyClothingRecognitionTest {
    private val itemId = fixtureId("garment")
    private val front = fixtureMedia(itemId, ImageView.FRONT)
    private val back = fixtureMedia(itemId, ImageView.BACK)
    private val media = BytesMediaStore().apply { bytes[front.id] = jpeg(1); bytes[back.id] = jpeg(2) }

    private fun health(views: String = "DUAL", enabled: Boolean = true) =
        """{"status":"ok","version":"dev","schemaVersion":2,"services":{"recognition":$enabled,"outfits":true,"tryon":false},
            "recognitionViews":"$views","provider":"gemini","providerConfigured":true}"""

    private fun request(capability: RecognitionCapability, images: ImagePair = ImagePair(front, back), revision: Long = 3,
        confirmed: Boolean = true, id: String = UUID.randomUUID().toString()) = RecognitionRequest(id, itemId, revision, images,
        ConsentGrant(requestId = id, provider = capability.provider, providerConfigVersion = capability.configVersion,
            mediaIds = images.refs().map { it.id }.toSet(), mediaViews = images.refs().associate { it.id to it.view },
            requestGeneration = revision, confirmedByUser = confirmed))

    private fun resultJson(req: RecognitionRequest, attributes: String = """{"category":"TOP","colors":["WHITE"],"pattern":"SOLID",
            "styleTags":["tối giản"],"material":null,"formality":2,"seasonTags":["SUMMER"]}""",
        review: String = """["material"]""", evidence: String = """{"category":["FRONT","BACK"],"colors":["FRONT"]}""",
        warnings: String = "[]", revision: Long = req.inputRevision, source: String = "AI_LIVE") =
        """{"requestId":"${req.requestId}","itemId":"${req.itemId}","inputRevision":$revision,"suggestedAttributes":$attributes,
            "needsReviewFields":$review,"viewEvidence":$evidence,"warnings":$warnings,"source":"$source"}"""

    /** Request đang gửi, để proxy giả dựng response khớp requestId/revision. */
    private var inFlight: RecognitionRequest? = null
    private suspend fun ProxyClothingRecognition.send(req: RecognitionRequest) = run { inFlight = req; recognize(req) }

    /** Proxy DUAL: `/health` + `/v2/recognition` trả [body]. */
    private suspend fun dualClient(proxy: FakeProxy = FakeProxy(), body: (RecognitionRequest) -> String = { resultJson(it) }):
        Pair<ProxyClothingRecognition, FakeProxy> {
        proxy.route = { rec -> if (rec.path == "/health") ok(health()) else ok(body(inFlight!!)) }
        val client = ProxyClothingRecognition(proxy.transport, media)
        client.refreshCapability().getOrThrow()
        return client to proxy
    }

    @Test fun `uploads front and back derivatives in the right slots with consent`() = runTest {
        val (client, proxy) = dualClient()
        assertTrue(client.capability.supportsBack)
        assertEquals("gemini", client.capability.provider)
        val req = request(client.capability)
        val result = client.send(req).getOrThrow()

        val body = Json.parseToJsonElement(proxy.posts("/v2/recognition").single().body).jsonObject
        val wireFront = body["front"]!!.jsonObject
        val wireBack = body["back"]!!.jsonObject
        assertEquals("FRONT", wireFront["view"]!!.jsonPrimitive.content)
        assertEquals(front.id, wireFront["mediaId"]!!.jsonPrimitive.content)
        assertEquals("BACK", wireBack["view"]!!.jsonPrimitive.content)
        assertEquals(back.id, wireBack["mediaId"]!!.jsonPrimitive.content)
        assertTrue(Base64.getDecoder().decode(wireFront["dataBase64"]!!.jsonPrimitive.content).contentEquals(jpeg(1)))
        assertEquals(sha256Hex(jpeg(2)), wireBack["sha256"]!!.jsonPrimitive.content)
        val consent = body["consentGrant"]!!.jsonObject
        assertEquals("RECOGNITION", consent["purpose"]!!.jsonPrimitive.content)
        assertEquals("true", consent["userConfirmed"]!!.jsonPrimitive.content)
        assertEquals(setOf(front.id, back.id), consent["mediaIds"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals(req.consentGrant.id, consent["grantId"]!!.jsonPrimitive.content)
        assertTrue("Bearer" in proxy.posts("/v2/recognition").single().authorization!!)
        // Derivative tạm đã dọn; ảnh gốc không bị đụng.
        assertEquals(media.derived.map { it.id }.toSet(), media.removed.toSet())
        assertFalse(front.id in media.removed || back.id in media.removed)

        assertEquals(Category.TOP, result.suggestedAttributes.category)
        assertEquals(listOf(GarmentColor.WHITE), result.suggestedAttributes.colors)
        assertEquals(setOf(AttributeField.MATERIAL), result.needsReviewFields)
        assertEquals(setOf(ImageView.FRONT, ImageView.BACK), result.viewEvidence[AttributeField.CATEGORY])
        assertEquals(ResultSource.AI_LIVE, result.source)
    }

    @Test fun `consent not confirmed never touches media or network`() = runTest {
        val (client, proxy) = dualClient()
        val before = proxy.calls.size
        client.send(request(client.capability, confirmed = false)).assertFailure(ErrorCode.CONSENT_REQUIRED)
        val stale = request(client.capability).let { it.copy(consentGrant = it.consentGrant.copy(mediaIds = setOf(front.id))) }
        client.send(stale).assertFailure(ErrorCode.CONSENT_REQUIRED)
        assertEquals(before, proxy.calls.size)
        assertTrue(media.derived.isEmpty())
    }

    @Test fun `before health check back image is unsupported and front-only still works`() = runTest {
        val proxy = FakeProxy()
        proxy.route = { ok(resultJson(inFlight!!, evidence = """{"category":["FRONT"]}""")) }
        val client = ProxyClothingRecognition(proxy.transport, media)
        assertFalse(client.capability.supportsBack)
        client.recognize(request(client.capability)).assertFailure(ErrorCode.UNSUPPORTED_CAPABILITY)
        assertTrue(proxy.calls.isEmpty())

        client.send(request(client.capability, ImagePair(front))).getOrThrow()
        val body = Json.parseToJsonElement(proxy.posts("/v2/recognition").single().body).jsonObject
        assertEquals("null", body["back"].toString())
    }

    @Test fun `oversized or mislabeled derivative is blocked before upload`() = runTest {
        val (client, proxy) = dualClient()
        media.bytes[front.id] = jpeg(1, RECOGNITION_IMAGE_LIMIT_BYTES + 1)
        client.send(request(client.capability, ImagePair(front))).assertFailure(ErrorCode.PAYLOAD_TOO_LARGE)
        media.bytes[front.id] = jpeg(1)
        media.mimeOverride = "image/png"
        client.send(request(client.capability, ImagePair(front))).assertFailure(ErrorCode.INVALID_REQUEST)
        assertTrue(proxy.posts("/v2/recognition").isEmpty())
        assertEquals(media.derived.map { it.id }.toSet(), media.removed.toSet())
    }

    @Test fun `identical front and back bytes are rejected locally`() = runTest {
        val (client, proxy) = dualClient()
        media.bytes[back.id] = jpeg(1)
        client.send(request(client.capability)).assertFailure(ErrorCode.INVALID_REQUEST)
        assertTrue(proxy.posts("/v2/recognition").isEmpty())
    }

    @Test fun `missing image file is IO, not a crash`() = runTest {
        val (client, _) = dualClient()
        media.bytes.remove(front.id)
        client.send(request(client.capability, ImagePair(front))).assertFailure(ErrorCode.IO)
    }

    @Test fun `malformed, stale or unknown-source responses are typed failures`() = runTest {
        listOf<Pair<(RecognitionRequest) -> String, ErrorCode>>(
            { _: RecognitionRequest -> "not json" } to ErrorCode.INVALID_AI_OUTPUT,
            { r: RecognitionRequest -> """{"requestId":"${r.requestId}"}""" } to ErrorCode.INVALID_AI_OUTPUT,
            { r: RecognitionRequest -> resultJson(r, revision = r.inputRevision - 1) } to ErrorCode.STALE_INPUT,
            { r: RecognitionRequest -> resultJson(r, source = "MAGIC") } to ErrorCode.INVALID_AI_OUTPUT,
        ).forEach { (body, code) ->
            val (client, _) = dualClient(body = body)
            client.send(request(client.capability)).assertFailure(code)
        }
    }

    @Test fun `odd values are normalised into review instead of failing`() = runTest {
        val (client, _) = dualClient(body = { r ->
            resultJson(r, attributes = """{"category":"ROBE","colors":["WHITE","TEAL","WHITE"],"pattern":"PAISLEY",
                "styleTags":["  ", "x"],"material":"${"m".repeat(120)}","formality":9,"seasonTags":["MONSOON","SUMMER"]}""",
                review = """["nonsense"]""", evidence = "{}",
                warnings = """["VIEWS_LOOK_LIKE_DIFFERENT_ITEMS","NEW_CODE"]""")
        })
        val result = client.send(request(client.capability)).getOrThrow()
        val a = result.suggestedAttributes
        assertEquals(Category.UNKNOWN, a.category)
        assertEquals(listOf(GarmentColor.WHITE), a.colors)
        assertNull(a.material); assertNull(a.formality)
        assertEquals(listOf("SUMMER"), a.seasonTags)
        assertEquals(listOf("x"), a.styleTags)
        assertEquals(AttributeField.entries.toSet(), result.needsReviewFields)
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.none { "VIEWS_LOOK" in it || "NEW_CODE" in it }) // không lộ mã thô
    }

    @Test fun `BACK evidence without a back image is stripped and flagged`() = runTest {
        val (client, _) = dualClient(body = { r -> resultJson(r, review = "[]", evidence = """{"colors":["BACK"],"category":["FRONT"]}""") })
        val result = client.send(request(client.capability, ImagePair(front))).getOrThrow()
        assertNull(result.viewEvidence[AttributeField.COLORS])
        assertTrue(AttributeField.COLORS in result.needsReviewFields)
    }

    @Test fun `server FRONT_ONLY rejection downgrades capability`() = runTest {
        val proxy = FakeProxy()
        proxy.route = { rec -> if (rec.path == "/health") ok(health()) else errorJson(400, "UNSUPPORTED_CAPABILITY") }
        val client = ProxyClothingRecognition(proxy.transport, media)
        client.refreshCapability().getOrThrow()
        client.recognize(request(client.capability)).assertFailure(ErrorCode.UNSUPPORTED_CAPABILITY)
        assertFalse(client.capability.supportsBack)
        assertTrue(client.capability.available)
    }

    @Test fun `per-service provider in health drives the recognition consent label`() = runTest {
        val proxy = FakeProxy().apply { route = { ok("""{"status":"ok","version":"dev","schemaVersion":2,
            "services":{"recognition":true,"outfits":true,"tryon":false},"recognitionViews":"DUAL",
            "provider":"openai@codex.fosta.app","providerConfigured":true,
            "providers":{"recognition":{"provider":"gemini","model":"gemini-3.5-flash-lite","configured":true},
                         "outfits":{"provider":"openai@codex.fosta.app","model":"gpt-6.1-sol","configured":true}}}""") } }
        val client = ProxyClothingRecognition(proxy.transport, media)
        val capability = client.refreshCapability().getOrThrow()
        assertEquals("gemini", capability.provider)
        assertTrue(capability.available && capability.supportsBack)
        assertTrue("gemini-3.5-flash-lite" in capability.configVersion)

        val unconfigured = FakeProxy().apply { route = { ok("""{"version":"dev","schemaVersion":2,"services":{"recognition":true},
            "recognitionViews":"DUAL","provider":"openai@codex.fosta.app","providerConfigured":true,
            "providers":{"recognition":{"provider":"gemini","model":"","configured":false}}}""") } }
        assertFalse(ProxyClothingRecognition(unconfigured.transport, media).refreshCapability().getOrThrow().available)
    }

    @Test fun `disabled service in health is unavailable`() = runTest {
        val proxy = FakeProxy().apply { route = { ok(health(enabled = false)) } }
        val client = ProxyClothingRecognition(proxy.transport, media)
        assertFalse(client.refreshCapability().getOrThrow().available)
        client.recognize(request(client.capability, ImagePair(front))).assertFailure(ErrorCode.UNSUPPORTED_CAPABILITY)
        assertTrue(proxy.posts("/v2/recognition").isEmpty())
    }

    @Test fun `timeout is typed and temporary derivatives are cleaned`() = runTest {
        val proxy = FakeProxy(deadlineMs = 200)
        proxy.route = { rec -> if (rec.path == "/health") ok(health()) else { delay(5_000); ok("{}") } }
        val client = ProxyClothingRecognition(proxy.transport, media)
        client.refreshCapability().getOrThrow()
        client.recognize(request(client.capability)).assertFailure(ErrorCode.UPSTREAM_TIMEOUT)
        assertEquals(1, proxy.posts("/v2/recognition").size)
        assertEquals(media.derived.map { it.id }.toSet(), media.removed.toSet())
    }

    @Test fun `same request is uploaded at most once`() = runTest {
        val (client, proxy) = dualClient()
        val req = request(client.capability)
        client.send(req).getOrThrow()
        client.send(req).assertFailure(ErrorCode.CONFLICT)
        assertEquals(1, proxy.posts("/v2/recognition").size)
    }

    @Test fun `unconfigured client is unavailable and offline`() = runTest {
        val proxy = FakeProxy(accessCode = "")
        val client = ProxyClothingRecognition(proxy.transport, media)
        assertFalse(client.capability.available)
        client.recognize(request(client.capability, ImagePair(front))).assertFailure(ErrorCode.UNSUPPORTED_CAPABILITY)
        assertTrue(proxy.calls.isEmpty())
    }
}

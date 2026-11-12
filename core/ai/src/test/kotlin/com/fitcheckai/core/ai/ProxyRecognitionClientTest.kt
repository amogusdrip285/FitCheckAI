package com.fitcheckai.core.ai

import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.ConsentGrant
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.ResultSource
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.FakeMediaStore
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.fixtureMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodedPath
import java.io.Closeable
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRecognitionClientTest {
    @Test fun `dual upload uses authorized original IDs and cleans the temporary derivative`() = runBlocking {
        Harness().use { proxy ->
            val request = proxy.connect(back = true)
            val large = request.images.front!!.copy(width = 1200)
            val originalBack = requireNotNull(request.images.back)
            val result = proxy.client.recognize(request.copy(images = request.images.copy(front = large))).getOrThrow()

            assertEquals(ResultSource.AI_LIVE, result.source)
            assertEquals(listOf(GarmentColor.BLACK), result.suggestedAttributes.colors)
            assertEquals(setOf(AttributeField.MATERIAL), result.needsReviewFields)
            assertEquals(setOf(ImageView.FRONT, ImageView.BACK), result.viewEvidence[AttributeField.COLORS])
            val body = proxy.uploads.single()
            assertEquals(request.requestId, body.requestId)
            assertEquals(request.consentGrant.mediaIds, body.consentGrant.mediaIds.toSet())
            assertTrue(body.consentGrant.userConfirmed)
            assertEquals(large.id, body.front.mediaId)
            val sentBack = requireNotNull(body.back)
            assertEquals(originalBack.id, sentBack.mediaId)
            val derivativeId = proxy.media.removed.single()
            assertArrayEquals(photoBytes(ImageView.FRONT), Base64.getDecoder().decode(body.front.dataBase64))
            assertArrayEquals(photoBytes(ImageView.BACK), Base64.getDecoder().decode(sentBack.dataBase64))
            val hash = MessageDigest.getInstance("SHA-256").digest(photoBytes(ImageView.FRONT))
                .joinToString("") { "%02x".format(it) }
            assertEquals(hash, body.front.sha256)
            assertFalse(derivativeId in request.consentGrant.mediaIds)
            assertEquals(listOf(derivativeId, originalBack.id), proxy.media.reads)
            assertEquals(listOf("/health", "/v2/session", "/health", "/v2/recognition"), proxy.paths)
        }
    }

    @Test fun `missing confirmation reads no photos and makes no network request`() = runBlocking {
        Harness().use { proxy ->
            val request = proxy.connect(back = false)
            proxy.paths.clear()
            val unconfirmed = request.copy(consentGrant = request.consentGrant.copy(confirmedByUser = false))
            val failure = proxy.client.recognize(unconfirmed).exceptionOrNull() as WardrobeException
            assertEquals(ErrorCode.CONSENT_REQUIRED, failure.code)
            assertTrue(proxy.paths.isEmpty())
            assertTrue(proxy.media.reads.isEmpty())
        }
    }

    @Test fun `a capability change after consent prevents both reads and upload`() = runBlocking {
        Harness().use { proxy ->
            val request = proxy.connect(back = true)
            proxy.views = "FRONT_ONLY"
            val failure = proxy.client.recognize(request).exceptionOrNull() as WardrobeException
            assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, failure.code)
            assertTrue(proxy.uploads.isEmpty())
            assertTrue(proxy.media.reads.isEmpty())
            assertFalse(proxy.client.capability.supportsBack)
        }
    }

    @Test fun `front-only request never includes back and rejects back evidence`() = runBlocking {
        Harness().use { proxy ->
            proxy.views = "FRONT_ONLY"
            val request = proxy.connect(back = false)
            proxy.transform = { it.copy(viewEvidence = mapOf("colors" to listOf("BACK"))) }
            val failure = proxy.client.recognize(request).exceptionOrNull() as WardrobeException
            assertEquals(ErrorCode.INVALID_AI_OUTPUT, failure.code)
            assertNull(proxy.uploads.single().back)
            assertEquals(listOf(request.images.front!!.id), proxy.uploads.single().consentGrant.mediaIds)
        }
    }

    @Test fun `actual image bytes over the limit are rejected before upload`() = runBlocking {
        Harness().use { proxy ->
            val request = proxy.connect(back = false)
            proxy.media.oversized = true
            val failure = proxy.client.recognize(request).exceptionOrNull() as WardrobeException
            assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, failure.code)
            assertTrue(proxy.uploads.isEmpty())
        }
    }

    @Test fun `stale results are rejected and server authorization failure discards the token`() = runBlocking {
        Harness().use { proxy ->
            val request = proxy.connect(back = false)
            proxy.transform = { it.copy(inputRevision = it.inputRevision - 1) }
            val stale = proxy.client.recognize(request).exceptionOrNull() as WardrobeException
            assertEquals(ErrorCode.STALE_INPUT, stale.code)
            proxy.rejectToken = true
            val expired = proxy.client.recognize(request).exceptionOrNull() as WardrobeException
            assertEquals(ErrorCode.UNAUTHORIZED, expired.code)
            assertTrue(proxy.client.needsAccessCode)
        }
    }

    private class Harness : Closeable {
        val media = PhotoStore(FakeMediaStore())
        val paths = mutableListOf<String>()
        val uploads = mutableListOf<RecognitionPayload>()
        var views = "DUAL"
        var rejectToken = false
        var transform: (RecognitionResponse) -> RecognitionResponse = { it }

        private val http = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            when (path) {
                "/health" -> respond("""{"status":"ok","version":"test","schemaVersion":2,"services":{"recognition":true,"outfits":false,"tryon":false},"recognitionViews":"$views","provider":"gemini","providerConfigured":true}""")
                "/v2/session" -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    assertEquals("demo-code", Json.decodeFromString<DemoSessionRequest>(body).accessCode)
                    respond("""{"token":"v1.session.exp.sig","expiresAt":5000}""")
                }
                "/v2/recognition" -> {
                    assertEquals("Bearer v1.session.exp.sig", request.headers[HttpHeaders.Authorization])
                    val raw = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val body = Json.decodeFromString<RecognitionPayload>(raw)
                    assertFalse(raw.contains("media/GARMENT") || raw.contains("profile"))
                    uploads += body
                    if (rejectToken) respond("""{"error":{"code":"UNAUTHORIZED"}}""", HttpStatusCode.Unauthorized)
                    else {
                        val result = RecognitionResponse(
                            body.requestId, body.itemId, body.inputRevision,
                            RecognitionAttributesResponse("DRESS", listOf("BLACK"), "SOLID", null, 3, emptyList(), emptyList()),
                            listOf("material"), mapOf("colors" to listOfNotNull(body.front.view, body.back?.view)),
                            emptyList(), "AI_LIVE",
                        )
                        respond(Json.encodeToString(transform(result)))
                    }
                }
                else -> error("Unexpected test route: $path")
            }
        })
        val client = ProxyRecognitionClient(media, "https://proxy.test", http) { 1000L }

        suspend fun connect(back: Boolean): RecognitionRequest {
            val capability = client.refreshCapability()
            assertTrue(capability.available)
            client.authorize("demo-code").getOrThrow()
            val owner = fixtureId("recognition-client-item")
            val request = fixtureId("recognition-client-request")
            val images = ImagePair(fixtureMedia(owner, ImageView.FRONT), if (back) fixtureMedia(owner, ImageView.BACK) else null)
            val mediaViews = images.refs().associate { it.id to it.view }
            return RecognitionRequest(request, owner, 2, images, ConsentGrant(
                requestId = request, provider = capability.provider, providerConfigVersion = capability.configVersion,
                mediaIds = mediaViews.keys, mediaViews = mediaViews, issuedAt = 1000, requestGeneration = 2, confirmedByUser = true,
            ))
        }

        override fun close() = http.close()
    }

    private class PhotoStore(private val delegate: FakeMediaStore) : MediaStore by delegate {
        val removed get() = delegate.removed
        val reads = mutableListOf<String>()
        var oversized = false
        override suspend fun read(ref: MediaRef): Result<ByteArray> {
            reads += ref.id
            val bytes = photoBytes(ref.view)
            return Result.success(if (oversized) bytes.copyOf(1024 * 1024 + 1) else bytes)
        }
    }

    private companion object {
        fun photoBytes(view: ImageView) = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), view.ordinal.toByte())
    }
}

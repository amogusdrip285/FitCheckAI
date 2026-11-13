package com.fitcheckai.core.ai

import com.fitcheckai.core.model.wardrobe.ClothingRecognition
import com.fitcheckai.core.model.wardrobe.DerivativePreset
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.core.model.wardrobe.RecognitionCapability
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.RecognitionResult
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.wardrobeResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Optional setup for the proxy-backed implementation. Test fakes only implement ClothingRecognition. */
interface RecognitionSetup {
    val configured: Boolean
    val needsAccessCode: Boolean
    suspend fun refreshCapability(): RecognitionCapability
    suspend fun authorize(accessCode: String): Result<Unit>
}

@Singleton
class ProxyRecognitionClient internal constructor(
    private val media: MediaStore,
    proxyUrl: String,
    private val http: HttpClient,
    private val now: () -> Long,
) : ClothingRecognition, RecognitionSetup {
    @Inject constructor(media: MediaStore) : this(media, BuildConfig.PROXY_URL, proxyHttpClient(), System::currentTimeMillis)

    private val origin = parseOrigin(proxyUrl)
    private val sessionMutex = Mutex()
    private val capabilityMutex = Mutex()
    @Volatile private var session: DemoSessionResponse? = null
    @Volatile override var capability = Unavailable
        private set

    override val configured get() = origin != null
    override val needsAccessCode get() = activeSession() == null

    override suspend fun refreshCapability(): RecognitionCapability = capabilityMutex.withLock {
        capability = if (configured) {
            networkResult { fetchCapability() }.getOrDefault(Unavailable)
        } else Unavailable
        capability
    }

    private suspend fun fetchCapability(): RecognitionCapability {
        val health = exchange<ProxyHealth>("/health")
        // Ảnh đi tới provider của riêng recognition (có thể khác provider chung) ⇒ consent phải nêu đúng nó.
        val own = health.providers["recognition"]
        val provider = own?.provider ?: health.provider
        val providerReady = own?.configured ?: health.providerConfigured
        if (health.status != "ok" || health.schemaVersion != 2 || health.services["recognition"] != true ||
            !providerReady || provider.substringBefore('@') !in SupportedProviders ||
            health.recognitionViews !in setOf("FRONT_ONLY", "DUAL")) return Unavailable
        val version = sha256("$origin/${health.version}/$provider/${own?.model.orEmpty()}/${health.recognitionViews}".encodeToByteArray())
        return RecognitionCapability(
            "$provider qua ${origin?.authority}", version, true, health.recognitionViews == "DUAL",
            supportsGarmentType = health.recognitionGarmentTypeVersion == GarmentTypeVersion,
        )
    }

    override suspend fun authorize(accessCode: String): Result<Unit> = networkResult {
        sessionMutex.withLock {
            if (activeSession() != null) return@withLock
            val code = accessCode.trim()
            if (code.isEmpty() || code.length > 128) throw unauthorized()
            val opened = exchange<DemoSessionResponse>("/v2/session", Json.encodeToString(DemoSessionRequest(code)))
            if (!opened.token.matches(Regex("[A-Za-z0-9._-]{1,2048}")) || opened.expiresAt <= now()) throw unauthorized()
            session = opened
        }
    }

    override suspend fun recognize(request: RecognitionRequest): Result<RecognitionResult> = networkResult {
        // Validate the original consent before any network or media I/O.
        request.validate(capability)
        val authorized = activeSession() ?: throw unauthorized()
        val result = withTimeoutOrNull(AiTimeouts.requestMs) {
            val refreshed = refreshCapability()
            request.validate(refreshed)
            val temporary = mutableListOf<MediaRef>()
            try {
                val front = encodeImage(requireNotNull(request.images.front), temporary)
                val back = request.images.back?.let { encodeImage(it, temporary) }
                if (front.sha256 == back?.sha256) {
                    throw WardrobeException(ErrorCode.INVALID_REQUEST, "Cần hai ảnh khác nhau cho mặt trước và mặt sau.")
                }
                val grant = request.consentGrant
                val payload = RecognitionPayload(request.schemaVersion, request.requestId, request.itemId,
                    request.inputRevision, front, back, RecognitionConsentPayload(grant.id, grant.purpose.name,
                        grant.providerConfigVersion, grant.mediaIds.sorted(), grant.issuedAt, grant.revokedAt, grant.confirmedByUser),
                    garmentTypeVersion = GarmentTypeVersion.takeIf { refreshed.supportsGarmentType })
                val body = Json.encodeToString(payload)
                if (body.encodeToByteArray().size > 4 * MiB) throw tooLarge()
                request.validate(capability)
                try {
                    exchange<RecognitionResponse>("/v2/recognition", body, authorized.token).toDomain(request)
                } catch (error: WardrobeException) {
                    if (error.code == ErrorCode.UNAUTHORIZED) {
                        sessionMutex.withLock { if (session == authorized) session = null }
                    }
                    throw error
                }
            } finally {
                withContext(NonCancellable) { temporary.forEach { media.remove(it) } }
            }
        }
        result ?: throw timedOut()
    }

    private suspend fun encodeImage(original: MediaRef, temporary: MutableList<MediaRef>): RecognitionImagePayload {
        val preset = DerivativePreset.RECOGNITION
        val ref = if (original.byteCount > preset.maxBytes || maxOf(original.width, original.height) > preset.maxEdge) {
            val derived = media.derive(original, preset).getOrThrow()
            require(derived.id != original.id && derived.derivedFromMediaId == original.id &&
                derived.ownerEntityId == original.ownerEntityId && derived.scope == original.scope && derived.view == original.view)
            temporary += derived
            derived
        } else original
        if (ref.byteCount > preset.maxBytes || maxOf(ref.width, ref.height) > preset.maxEdge) throw tooLarge()
        val bytes = media.read(ref).getOrThrow()
        if (bytes.size > preset.maxBytes) throw tooLarge()
        require(matchesMime(bytes, ref.mimeType)) { "Ảnh không đúng định dạng JPEG hoặc PNG." }
        // Consent identifies the selected original. The hash describes the actual encoded bytes.
        return RecognitionImagePayload(original.view.name, original.id, ref.mimeType, sha256(bytes), Base64.getEncoder().encodeToString(bytes))
    }

    private fun activeSession() = session?.takeIf { it.expiresAt > now() }

    private suspend inline fun <reified T> exchange(path: String, body: String? = null, token: String? = null): T {
        val base = origin ?: throw WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Chưa cấu hình proxy HTTPS cho nhận diện.")
        return http.prepareRequest("${base.toString().trimEnd('/')}$path") {
            method = if (body == null) HttpMethod.Get else HttpMethod.Post
            if (token != null) bearerAuth(token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.execute { response ->
            val bytes = response.bodyAsChannel().readRemaining(MaxResponseBytes + 1L).readByteArray()
            if (bytes.size > MaxResponseBytes) throw invalidResponse()
            val text = bytes.decodeToString()
            if (response.status.value != 200) throw proxyFailure(response.status.value, text)
            try {
                ResponseJson.decodeFromString<T>(text)
            } catch (_: IllegalArgumentException) {
                throw invalidResponse()
            }
        }
    }

    private suspend fun <T> networkResult(block: suspend () -> T): Result<T> = wardrobeResult {
        try {
            block()
        } catch (_: HttpRequestTimeoutException) {
            throw timedOut()
        } catch (_: SocketTimeoutException) {
            throw timedOut()
        } catch (_: IOException) {
            throw WardrobeException(ErrorCode.NETWORK, "Không kết nối được proxy. Bạn vẫn có thể tự điền.")
        }
    }

    private companion object {
        const val MiB = 1024 * 1024
        const val GarmentTypeVersion = 1
        const val MaxResponseBytes = 64 * 1024
        val Unavailable = RecognitionCapability("FitCheckAI", "2", false, false)
        /** "openai" gồm cả gateway tương thích; nhãn dạng "openai@codex.fosta.app" giữ host thật để consent ghi đúng. */
        val SupportedProviders = setOf("gemini", "anthropic", "openai")
        /** Proxy được thêm field mới (vd. `providers`) mà không làm hỏng client cũ. */
        val ResponseJson = Json { ignoreUnknownKeys = true }
        val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

        fun parseOrigin(value: String): URI? = runCatching { URI(value.trim()) }.getOrNull()?.takeIf {
            it.scheme == "https" && !it.host.isNullOrBlank() &&
                !it.host.equals("fitcheck-proxy.example.workers.dev", ignoreCase = true) &&
                it.userInfo == null && it.query == null && it.fragment == null && it.path in setOf("", "/")
        }

        fun proxyHttpClient() = HttpClient(OkHttp) {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = AiTimeouts.requestMs; connectTimeoutMillis = 5_000 }
            engine { config { retryOnConnectionFailure(false); followRedirects(false); followSslRedirects(false) } }
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

        fun matchesMime(bytes: ByteArray, mime: String): Boolean = when (mime) {
            "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
            "image/png" -> bytes.size >= PngSignature.size && PngSignature.indices.all { bytes[it] == PngSignature[it] }
            else -> false
        }

        fun unauthorized() = WardrobeException(ErrorCode.UNAUTHORIZED, "Cần mã truy cập demo hợp lệ để dùng nhận diện.")
        fun tooLarge() = WardrobeException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh gửi nhận diện vượt giới hạn dung lượng.")
        fun timedOut() = WardrobeException(ErrorCode.UPSTREAM_TIMEOUT, "Nhận diện quá thời gian. Bạn vẫn có thể tự điền.")
        fun invalidResponse() = WardrobeException(ErrorCode.INVALID_AI_OUTPUT, "Phản hồi proxy không hợp lệ. Bạn vẫn có thể tự điền.")

        fun proxyFailure(status: Int, body: String): WardrobeException {
            val code = runCatching {
                val value = Json.parseToJsonElement(body).jsonObject.getValue("error").jsonObject.getValue("code")
                ErrorCode.valueOf(value.jsonPrimitive.content)
            }.getOrNull() ?: when (status) {
                400 -> ErrorCode.INVALID_REQUEST
                401 -> ErrorCode.UNAUTHORIZED
                403 -> ErrorCode.CONSENT_REQUIRED
                413 -> ErrorCode.PAYLOAD_TOO_LARGE
                429 -> ErrorCode.RATE_LIMITED
                504 -> ErrorCode.UPSTREAM_TIMEOUT
                else -> ErrorCode.UPSTREAM_ERROR
            }
            return when (code) {
                ErrorCode.UNAUTHORIZED -> unauthorized()
                ErrorCode.PAYLOAD_TOO_LARGE -> tooLarge()
                ErrorCode.UPSTREAM_TIMEOUT -> timedOut()
                ErrorCode.CONSENT_REQUIRED -> WardrobeException(code, "Cần xác nhận đúng ảnh trước khi gửi.")
                ErrorCode.UNSUPPORTED_CAPABILITY -> WardrobeException(code, "Dịch vụ chưa hỗ trợ yêu cầu này. Hãy kết nối lại hoặc tự điền.")
                ErrorCode.RATE_LIMITED -> WardrobeException(code, "Đã gửi quá nhiều yêu cầu. Hãy thử lại sau.")
                ErrorCode.BUDGET_EXCEEDED -> WardrobeException(code, "Dịch vụ đã hết hạn mức nhận diện.")
                else -> WardrobeException(code, "Nhận diện chưa hoàn tất. Bạn vẫn có thể tự điền.")
            }
        }
    }
}

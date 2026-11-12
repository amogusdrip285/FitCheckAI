package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.WardrobeException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// OWNER: Track B (B-003/B-007). Wire format: docs/contracts/proxy-api.md. Lỗi theo CT-07.

/** Cấu hình proxy. [accessCode] trống ⇒ dịch vụ coi như chưa cấu hình, không gọi mạng. */
data class ProxyConfig(
    val baseUrl: String,
    val accessCode: String,
    /** Deadline client CT-07: 20s cho cả session + request. */
    val deadlineMs: Long = 20_000,
) {
    val configured: Boolean get() = accessCode.isNotBlank() && validBaseUrl(baseUrl)

    companion object {
        private val LOCAL_DEV_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

        /** Chỉ HTTPS; HTTP chỉ cho proxy chạy local (emulator → máy dev). Không query/fragment/credential. */
        fun validBaseUrl(raw: String): Boolean = runCatching {
            val url = Url(raw)
            val schemeOk = url.protocol.name == "https" || (url.protocol.name == "http" && url.host in LOCAL_DEV_HOSTS)
            schemeOk && url.host.isNotBlank() && url.user == null && url.parameters.isEmpty() && url.fragment.isEmpty()
        }.getOrDefault(false)
    }
}

/** Kết quả một lần gọi: body thành công, hoặc lỗi có kiểu + HTTP status nếu server đã trả lời. */
internal sealed interface ProxyResponse {
    data class Ok(val body: String) : ProxyResponse
    data class Failed(val error: WardrobeException, val httpStatus: Int? = null) : ProxyResponse
}

internal val wireJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

@Serializable private data class SessionRequest(val accessCode: String)
@Serializable private data class SessionResponse(val token: String, val expiresAt: Long)

/**
 * HTTP tới proxy. Không bao giờ tự retry request có body (CT-07: chỉ người dùng bấm lại).
 * Cancellation của caller được ném tiếp; mọi lỗi khác thành [WardrobeException] có [ErrorCode].
 */
class ProxyTransport internal constructor(
    private val config: ProxyConfig,
    engine: HttpClientEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = config.deadlineMs
            connectTimeoutMillis = config.deadlineMs
            socketTimeoutMillis = config.deadlineMs
        }
    }
    private val base = config.baseUrl.trimEnd('/')
    private val sessionMutex = Mutex()
    private var session: SessionResponse? = null

    internal val configured: Boolean get() = config.configured

    /** POST JSON có auth. [body] đã được caller kiểm size trước khi gọi. */
    internal suspend fun postJson(path: String, body: String): ProxyResponse = withDeadline {
        val token = sessionToken()
        val response = client.post("$base$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(TextContent(body, ContentType.Application.Json))
        }
        if (response.status.value == 401) invalidateSession()
        read(response)
    }

    /** GET không auth (vd. `/health`). */
    internal suspend fun get(path: String): ProxyResponse = withDeadline { read(client.get("$base$path")) }

    private suspend fun read(response: HttpResponse): ProxyResponse {
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) ProxyResponse.Ok(text)
        else ProxyResponse.Failed(errorFromEnvelope(response.status.value, text), response.status.value)
    }

    private suspend fun sessionToken(): String = sessionMutex.withLock {
        if (!config.configured) throw proxyError(ErrorCode.UNSUPPORTED_CAPABILITY)
        session?.takeIf { it.expiresAt - clock() > SESSION_REFRESH_MARGIN_MS }?.let { return it.token }
        val response = client.post("$base/v2/session") {
            setBody(TextContent(wireJson.encodeToString(SessionRequest.serializer(), SessionRequest(config.accessCode)),
                ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw errorFromEnvelope(response.status.value, text)
        val parsed = runCatching { wireJson.decodeFromString(SessionResponse.serializer(), text) }.getOrNull()
            ?.takeIf { it.token.isNotBlank() && it.expiresAt > clock() }
            ?: throw proxyError(ErrorCode.UPSTREAM_ERROR)
        session = parsed
        parsed.token
    }

    private suspend fun invalidateSession() = sessionMutex.withLock { session = null }

    private suspend fun withDeadline(block: suspend () -> ProxyResponse): ProxyResponse = withContext(dispatcher) {
        // withTimeoutOrNull chạy trên dispatcher thật nên deadline là thời gian thật, kể cả trong runTest.
        withTimeoutOrNull(config.deadlineMs) { attempt(block) } ?: ProxyResponse.Failed(proxyError(ErrorCode.UPSTREAM_TIMEOUT))
    }

    private suspend fun attempt(block: suspend () -> ProxyResponse): ProxyResponse = try {
        block()
    } catch (cancelled: CancellationException) {
        // Caller/timeout huỷ ⇒ ném tiếp. Engine tự huỷ khi coroutine vẫn sống ⇒ lỗi có kiểu, không văng vào UI.
        if (!currentCoroutineContext().isActive) throw cancelled
        ProxyResponse.Failed(proxyError(ErrorCode.CANCELLED))
    } catch (known: WardrobeException) {
        ProxyResponse.Failed(known)
    } catch (e: Exception) {
        ProxyResponse.Failed(proxyError(if (e.isTimeout()) ErrorCode.UPSTREAM_TIMEOUT else ErrorCode.NETWORK))
    }

    private fun Throwable.isTimeout(): Boolean =
        this is HttpRequestTimeoutException || this is java.net.SocketTimeoutException ||
            javaClass.simpleName.contains("Timeout") || cause?.isTimeout() == true

    companion object {
        const val SESSION_REFRESH_MARGIN_MS = 60_000L
    }
}

/** Server code ⇒ [ErrorCode]; envelope hỏng/mã lạ ⇒ suy theo HTTP status. Không giữ message server (có thể đổi). */
internal fun errorFromEnvelope(status: Int, body: String): WardrobeException {
    val code = runCatching {
        wireJson.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
    }.getOrNull()
    val mapped = ErrorCode.entries.firstOrNull { it.name == code && it !in LOCAL_ONLY_CODES } ?: when (status) {
        400, 404 -> ErrorCode.INVALID_REQUEST
        401 -> ErrorCode.UNAUTHORIZED
        403 -> ErrorCode.FORBIDDEN
        409 -> ErrorCode.CONFLICT
        413 -> ErrorCode.PAYLOAD_TOO_LARGE
        429 -> ErrorCode.RATE_LIMITED
        503 -> ErrorCode.UNSUPPORTED_CAPABILITY
        504 -> ErrorCode.UPSTREAM_TIMEOUT
        else -> ErrorCode.UPSTREAM_ERROR
    }
    return proxyError(mapped)
}

/** Mã chỉ Android sinh; server mà trả các mã này là sai contract. */
private val LOCAL_ONLY_CODES = setOf(ErrorCode.NETWORK, ErrorCode.IO, ErrorCode.CANCELLED)

internal fun proxyError(code: ErrorCode): WardrobeException = WardrobeException(code, userMessage(code))

/** Người dùng có nên được mời bấm lại không (CT-07). Client không bao giờ tự retry. */
fun ErrorCode.isUserRetryable(): Boolean = this in setOf(
    ErrorCode.NETWORK, ErrorCode.UPSTREAM_TIMEOUT, ErrorCode.UPSTREAM_ERROR, ErrorCode.RATE_LIMITED,
    ErrorCode.INVALID_AI_OUTPUT, ErrorCode.CANCELLED, ErrorCode.UNAUTHORIZED,
)

internal fun userMessage(code: ErrorCode): String = when (code) {
    ErrorCode.INVALID_REQUEST -> "Dữ liệu gửi đi không hợp lệ."
    ErrorCode.CONSENT_REQUIRED -> "Cần xác nhận gửi đúng ảnh cho lần nhận diện này."
    ErrorCode.UNAUTHORIZED -> "Phiên kết nối AI đã hết hạn hoặc mã truy cập sai. Hãy thử lại."
    ErrorCode.FORBIDDEN -> "Không có quyền dùng dịch vụ AI."
    ErrorCode.PAYLOAD_TOO_LARGE -> "Ảnh hoặc dữ liệu quá lớn để gửi."
    ErrorCode.RATE_LIMITED -> "Bạn gửi hơi nhanh. Đợi một chút rồi thử lại."
    ErrorCode.BUDGET_EXCEEDED -> "Dịch vụ AI đã hết lượt dùng hôm nay."
    ErrorCode.UNSUPPORTED_CAPABILITY -> "Dịch vụ AI chưa được kết nối hoặc đang tắt. Bạn vẫn có thể dùng thủ công."
    ErrorCode.INSUFFICIENT_WARDROBE -> "Tủ đồ chưa đủ món để phối một bộ hợp lệ."
    ErrorCode.INVALID_AI_OUTPUT -> "AI trả kết quả không hợp lệ nên đã bị bỏ. Hãy thử lại."
    ErrorCode.UPSTREAM_TIMEOUT -> "AI phản hồi quá lâu. Hãy thử lại."
    ErrorCode.UPSTREAM_ERROR -> "Dịch vụ AI đang lỗi. Hãy thử lại sau."
    ErrorCode.NETWORK -> "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại."
    ErrorCode.CANCELLED -> "Yêu cầu đã bị huỷ."
    ErrorCode.STALE_INPUT -> "Kết quả không còn khớp dữ liệu hiện tại nên đã bị bỏ."
    ErrorCode.CONFLICT -> "Yêu cầu này đã được gửi rồi; hãy tạo yêu cầu mới."
    ErrorCode.IO -> "Không đọc được ảnh trên máy."
    ErrorCode.NEEDS_INPUT -> "Cần bổ sung dữ liệu trước khi gửi."
    ErrorCode.EXPIRED -> "Kết quả đã hết hạn."
}

/** Mỗi requestId chỉ đi ra mạng một lần: chặn upload trùng do recomposition/lifecycle. */
internal class RequestLedger(private val capacity: Int = 256) {
    private val seen = LinkedHashSet<String>()

    @Synchronized
    fun claim(requestId: String): Boolean {
        if (!seen.add(requestId)) return false
        if (seen.size > capacity) seen.remove(seen.first())
        return true
    }
}

/** ID trên wire (proxy `idSchema`): UUID hoặc ID ngắn chữ-số; không chứa `|`. */
internal val WIRE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

/** Factory công khai cho DI; constructor đầy đủ (dispatcher/clock) chỉ dành cho test. */
fun createProxyTransport(config: ProxyConfig, engine: HttpClientEngine): ProxyTransport = ProxyTransport(config, engine)

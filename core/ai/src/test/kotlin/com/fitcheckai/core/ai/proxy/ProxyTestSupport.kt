package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.WardrobeException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals

const val TEST_URL = "https://proxy.test"
const val TEST_TOKEN = "v1.session.9999999999999.sig"

data class Recorded(val method: String, val path: String, val authorization: String?, val body: String)

/** Proxy giả: `/v2/session` tự trả token; route khác do [route] quyết định. Ghi lại mọi request. */
class FakeProxy(
    deadlineMs: Long = 2_000,
    accessCode: String = "demo-code",
    var now: Long = 1_000L,
) {
    val calls = CopyOnWriteArrayList<Recorded>()
    var sessionStatus = HttpStatusCode.OK
    var route: suspend MockRequestHandleScope.(Recorded) -> HttpResponseData = { ok("{}") }

    val engine = MockEngine { request ->
        val recorded = record(request)
        calls += recorded
        if (recorded.path == "/v2/session") {
            if (sessionStatus != HttpStatusCode.OK) return@MockEngine errorJson(sessionStatus.value, "UNAUTHORIZED")
            return@MockEngine ok("""{"token":"$TEST_TOKEN","expiresAt":${now + 12 * 3_600_000}}""")
        }
        route(recorded)
    }
    val transport = ProxyTransport(ProxyConfig(TEST_URL, accessCode, deadlineMs), engine, Dispatchers.Default) { now }

    fun posts(path: String) = calls.filter { it.path == path }

    private fun record(request: HttpRequestData) = Recorded(
        request.method.value, request.url.encodedPath, request.headers[HttpHeaders.Authorization],
        (request.body as? TextContent)?.text ?: "",
    )
}

fun MockRequestHandleScope.ok(json: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun MockRequestHandleScope.errorJson(status: Int, code: String, retryable: Boolean = false) = respond(
    """{"error":{"code":"$code","message":"…","requestId":"x","retryable":$retryable}}""",
    HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"),
)

fun <T> Result<T>.assertFailure(code: ErrorCode) {
    val error = exceptionOrNull() ?: throw AssertionError("Mong đợi lỗi $code nhưng thành công: ${getOrNull()}")
    assertEquals(WardrobeException::class, error::class)
    assertEquals(code, (error as WardrobeException).code)
}

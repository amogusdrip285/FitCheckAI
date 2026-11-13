package com.fitcheckai.core.ai

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Hồi quy INT-002 (13/11/2026): client nhận diện chỉ đặt requestTimeoutMillis nên OkHttp giữ readTimeout mặc định 10s.
 * Gateway trả lời sau 18–24s (không gửi byte nào trong lúc chờ) ⇒ app luôn báo "quá thời gian". Test dùng server
 * socket thật trả lời sau 11s: phải nhận được body khi timeout cấu hình 30s.
 */
class ProxyHttpClientTimeoutTest {
    private lateinit var server: ServerSocket

    @Before fun start() {
        server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit // bỏ qua request line + headers
                    Thread.sleep(11_000)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        flush()
                    }
                }
            }
        }
    }

    @After fun stop() = server.close()

    @Test(timeout = 40_000)
    fun `slow upstream longer than OkHttp default read timeout still succeeds`() = runBlocking {
        val client = proxyHttpClient(timeoutMs = 30_000)
        val body = client.get("http://127.0.0.1:${server.localPort}/slow").bodyAsText()
        assertEquals("ok", body)
    }
}

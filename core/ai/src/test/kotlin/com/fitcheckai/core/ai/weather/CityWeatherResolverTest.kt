package com.fitcheckai.core.ai.weather

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityWeatherResolverTest {
    private var now = 1_794_561_300_000L // trùng current.time mẫu (ms)
    private val calls = CopyOnWriteArrayList<HttpRequestData>()
    private var reply: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { ok(body()) }

    private fun body(time: Long = 1_794_561_300, temp: Double = 23.5, code: Int = 1) =
        """{"latitude":21.05,"longitude":105.89,"current_units":{"time":"unixtime"},
            "current":{"time":$time,"interval":900,"temperature_2m":$temp,"weather_code":$code}}"""

    private fun MockRequestHandleScope.ok(json: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun resolver(timeoutMs: Long = 3_000) = CityWeatherResolver(
        MockEngine { request -> calls += request; reply(request) }, Dispatchers.Default, { now }, timeoutMs = timeoutMs,
    )

    @Test fun `known city maps to a fresh snapshot with source and timestamps`() = runTest {
        now += 60_000
        val snapshot = resolver().weatherFor("hanoi")!!
        assertEquals("Hà Nội", snapshot.cityLabel)
        assertEquals(23.5, snapshot.tempC, 0.0)
        assertEquals("Ít mây", snapshot.condition)
        assertEquals(1_794_561_300_000L, snapshot.validAt)
        assertEquals(now, snapshot.fetchedAt)
        assertEquals(WEATHER_SOURCE, snapshot.source)
        assertFalse(snapshot.isStale)
        val url = calls.single().url
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("21.0285", url.parameters["latitude"])
        assertEquals("temperature_2m,weather_code", url.parameters["current"])
        // Chỉ toạ độ thành phố + tham số kỹ thuật — không có dữ liệu người dùng.
        assertEquals(setOf("latitude", "longitude", "current", "timezone", "timeformat"), url.parameters.names())
    }

    @Test fun `unset or unsupported city gives null without network`() = runTest {
        val r = resolver()
        assertNull(r.weatherFor(null))
        assertNull(r.weatherFor("   "))
        assertNull(r.weatherFor("Paris"))
        assertTrue(calls.isEmpty())
    }

    @Test fun `city names match with or without diacritics and common aliases`() {
        listOf("hanoi", "Hà Nội", "ha noi", "HN", "Thành phố Hà Nội").forEach { assertEquals(it, "hanoi", WeatherCities.find(it)?.key) }
        listOf("TP.HCM", "Hồ Chí Minh", "TP. Hồ Chí Minh", "Sài Gòn", "hochiminh").forEach { assertEquals(it, "hochiminh", WeatherCities.find(it)?.key) }
        assertEquals("danang", WeatherCities.find("Đà Nẵng")?.key)
        assertNull(WeatherCities.find("Bangkok"))
    }

    @Test fun `cached value is reused within 30 minutes`() = runTest {
        val r = resolver()
        r.weatherFor("hanoi")
        now += 10 * 60_000
        r.weatherFor("Hà Nội")
        assertEquals(1, calls.size)
        now += 25 * 60_000
        r.weatherFor("hanoi")
        assertEquals(2, calls.size)
    }

    @Test fun `failed refresh serves recent cache marked stale, too old cache gives null`() = runTest {
        val r = resolver()
        assertFalse(r.weatherFor("hanoi")!!.isStale)
        reply = { ok("""{"error":true}""", HttpStatusCode.InternalServerError) }
        now += 45 * 60_000
        assertTrue(r.weatherFor("hanoi")!!.isStale)
        now += 7 * 60 * 60_000
        assertNull(r.weatherFor("hanoi"))
    }

    @Test fun `old measurement is never presented as current`() = runTest {
        now = 1_794_561_300_000L + 2 * 60 * 60_000
        assertTrue(resolver().weatherFor("hanoi")!!.isStale)
    }

    @Test fun `timeout, network error and malformed data give null instead of throwing`() = runTest {
        reply = { delay(5_000); ok(body()) }
        val started = System.currentTimeMillis()
        assertNull(resolver(timeoutMs = 200).weatherFor("hanoi"))
        assertTrue(System.currentTimeMillis() - started < 3_000)

        reply = { throw IOException("offline") }
        assertNull(resolver().weatherFor("hanoi"))
        reply = { ok("not json") }
        assertNull(resolver().weatherFor("hanoi"))
        reply = { ok(body(temp = 99.0)) }
        assertNull(resolver().weatherFor("hanoi"))
        reply = { ok(body(time = 0)) }
        assertNull(resolver().weatherFor("hanoi"))
    }

    @Test fun `unknown weather code gets a neutral label`() = runTest {
        reply = { ok(body(code = 42)) }
        assertEquals("Thời tiết khác", resolver().weatherFor("hanoi")!!.condition)
        assertEquals("Mưa rào", conditionLabel(81))
        assertEquals("Dông", conditionLabel(95))
    }

    @Test fun `caller cancellation propagates`() = runTest {
        val started = CompletableDeferred<Unit>()
        reply = { started.complete(Unit); delay(5_000); ok(body()) }
        val call = async { resolver(timeoutMs = 10_000).weatherFor("hanoi") }
        started.await()
        call.cancel()
        assertTrue(runCatching { call.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
}

package com.fitcheckai.core.ai.weather

import com.fitcheckai.core.model.recommendation.WeatherSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.text.Normalizer
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

// OWNER: Track B (C-101). Thời tiết theo thành phố người dùng CHỌN (CT-04 `cityKey`), không GPS.
// Nguồn: Open-Meteo free API (phi thương mại, không key, CC BY 4.0) — xem docs/track-b/C-101-weather.md.

/** Phải hiển thị cạnh mọi chỗ dùng dữ liệu thời tiết (giấy phép CC BY 4.0 của Open-Meteo). */
const val WEATHER_ATTRIBUTION_TEXT = "Weather data by Open-Meteo.com"
const val WEATHER_ATTRIBUTION_URL = "https://open-meteo.com/"
const val WEATHER_SOURCE = "Open-Meteo"

data class WeatherCity(val key: String, val label: String, val latitude: Double, val longitude: Double, val aliases: Set<String> = emptySet())

/** Danh sách thành phố hỗ trợ. Không có trong danh sách ⇒ không có thời tiết (không đoán vị trí). */
object WeatherCities {
    val ALL: List<WeatherCity> = listOf(
        WeatherCity("hanoi", "Hà Nội", 21.0285, 105.8542, setOf("hn", "thudohanoi")),
        WeatherCity("hochiminh", "TP. Hồ Chí Minh", 10.7769, 106.7009, setOf("hcm", "tphcm", "tphochiminh", "saigon", "thanhphohochiminh")),
        WeatherCity("danang", "Đà Nẵng", 16.0544, 108.2022, setOf("dn")),
        WeatherCity("haiphong", "Hải Phòng", 20.8449, 106.6881, setOf("hp")),
        WeatherCity("cantho", "Cần Thơ", 10.0452, 105.7469),
        WeatherCity("hue", "Huế", 16.4637, 107.5909, setOf("thuathienhue")),
        WeatherCity("nhatrang", "Nha Trang", 12.2388, 109.1967),
        WeatherCity("dalat", "Đà Lạt", 11.9404, 108.4583),
        WeatherCity("vungtau", "Vũng Tàu", 10.3460, 107.0843),
        WeatherCity("halong", "Hạ Long", 20.9517, 107.0800, setOf("quangninh")),
        WeatherCity("vinh", "Vinh", 18.6796, 105.6813, setOf("nghean")),
        WeatherCity("quynhon", "Quy Nhơn", 13.7820, 109.2196, setOf("binhdinh")),
    )

    /** Khớp key/tên/alias sau khi bỏ dấu, khoảng trắng, dấu câu và chữ "thành phố"/"tp". */
    fun find(cityKey: String?): WeatherCity? {
        val wanted = normalize(cityKey ?: return null).ifEmpty { return null }
        return ALL.firstOrNull { city -> wanted == city.key || wanted == normalize(city.label) || wanted in city.aliases }
            ?: ALL.firstOrNull { city -> wanted.removePrefix("thanhpho").removePrefix("tp") == city.key }
    }

    internal fun normalize(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase().replace('đ', 'd'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]"), "")
}

/** Mã WMO (Open-Meteo `weather_code`) ⇒ mô tả tiếng Việt ngắn. */
internal fun conditionLabel(code: Int): String = when (code) {
    0 -> "Trời quang"
    1 -> "Ít mây"
    2 -> "Có mây"
    3 -> "Nhiều mây"
    45, 48 -> "Sương mù"
    51, 53, 55 -> "Mưa phùn"
    56, 57 -> "Mưa phùn lạnh"
    61 -> "Mưa nhỏ"
    63 -> "Mưa vừa"
    65 -> "Mưa to"
    66, 67 -> "Mưa lạnh"
    71, 73, 75, 77 -> "Tuyết"
    80, 81, 82 -> "Mưa rào"
    85, 86 -> "Mưa tuyết"
    95 -> "Dông"
    96, 99 -> "Dông kèm mưa đá"
    else -> "Thời tiết khác"
}

@Serializable private data class OpenMeteoCurrent(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
)
@Serializable private data class OpenMeteoResponse(val current: OpenMeteoCurrent)

/**
 * Lấy thời tiết hiện tại cho thành phố đã chọn. **Không bao giờ ném lỗi** (trừ khi caller huỷ):
 * - thành phố chưa chọn/không hỗ trợ ⇒ `null`, không gọi mạng;
 * - timeout riêng 3s, lỗi mạng/dữ liệu hỏng ⇒ dùng bản cache gần đây với `isStale = true`, không có thì `null`;
 * - không bao giờ trình bày dữ liệu cũ như hiện tại: `isStale` khi số đo cũ hơn [staleAfterMs] hoặc lấy từ cache sau lỗi.
 * Recommendation vẫn chạy với `weather = null` (C-101: thời tiết không chặn phối đồ).
 */
class CityWeatherResolver internal constructor(
    engine: HttpClientEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 3_000,
    private val cacheTtlMs: Long = 30 * 60_000L,
    private val staleAfterMs: Long = 60 * 60_000L,
    private val maxCacheAgeMs: Long = 6 * 60 * 60_000L,
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = timeoutMs; connectTimeoutMillis = timeoutMs; socketTimeoutMillis = timeoutMs }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = HashMap<String, WeatherSnapshot>()

    suspend fun weatherFor(cityKey: String?): WeatherSnapshot? {
        val city = WeatherCities.find(cityKey) ?: return null
        return mutex.withLock {
            val cached = cache[city.key]
            if (cached != null && clock() - cached.fetchedAt < cacheTtlMs) return@withLock cached.withFreshness()
            val fresh = fetch(city)
            if (fresh != null) {
                cache[city.key] = fresh
                fresh
            } else {
                cached?.takeIf { clock() - it.fetchedAt < maxCacheAgeMs }?.copy(isStale = true)
            }
        }
    }

    private fun WeatherSnapshot.withFreshness(): WeatherSnapshot = copy(isStale = isStale || clock() - validAt > staleAfterMs)

    private suspend fun fetch(city: WeatherCity): WeatherSnapshot? = withContext(dispatcher) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val response = client.get(OPEN_METEO_FORECAST) {
                    parameter("latitude", city.latitude)
                    parameter("longitude", city.longitude)
                    parameter("current", "temperature_2m,weather_code")
                    parameter("timezone", "Asia/Bangkok")
                    parameter("timeformat", "unixtime")
                }
                if (!response.status.isSuccess()) return@withTimeoutOrNull null
                val current = json.decodeFromString(OpenMeteoResponse.serializer(), response.bodyAsText()).current
                val tempC = current.temperature
                if (!tempC.isFinite() || tempC !in -60.0..60.0 || current.time <= 0) return@withTimeoutOrNull null
                val now = clock()
                val validAt = current.time * 1_000
                WeatherSnapshot(
                    cityLabel = city.label,
                    tempC = tempC,
                    condition = conditionLabel(current.weatherCode),
                    validAt = validAt,
                    fetchedAt = now,
                    source = WEATHER_SOURCE,
                    isStale = now - validAt > staleAfterMs,
                )
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        const val OPEN_METEO_FORECAST = "https://api.open-meteo.com/v1/forecast"
    }
}

/** Factory công khai cho DI; constructor đầy đủ (clock/dispatcher/TTL) chỉ dành cho test. */
fun createCityWeatherResolver(engine: HttpClientEngine): CityWeatherResolver = CityWeatherResolver(engine)

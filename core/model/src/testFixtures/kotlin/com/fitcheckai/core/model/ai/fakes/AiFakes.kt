// OWNER: Track B (B-005). Fake AI cho consumer A/D phát triển không cần proxy/account/API.
// Test-only: KHÔNG bind vào Hilt production. Mọi kết quả có source FAKE; không bao giờ giả AI_LIVE.
package com.fitcheckai.core.model.ai.fakes

import com.fitcheckai.core.model.recommendation.Outfit
import com.fitcheckai.core.model.recommendation.OutfitRecommendation
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.RecommendationCapability
import com.fitcheckai.core.model.recommendation.RecommendationResult
import com.fitcheckai.core.model.recommendation.ShortfallReason
import com.fitcheckai.core.model.recommendation.WeatherSnapshot
import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ClothingRecognition
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.ItemRevision
import com.fitcheckai.core.model.wardrobe.Pattern
import com.fitcheckai.core.model.wardrobe.RecognitionCapability
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.RecognitionResult
import com.fitcheckai.core.model.wardrobe.ResultSource
import com.fitcheckai.core.model.wardrobe.WardrobeException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

const val FAKE_CREATED_AT = 1_794_621_600_000L

/** Kịch bản recommendation. Failure mô phỏng lỗi có kiểu mà client thật (B-003) trả cho UI. */
sealed interface RecommendationScenario {
    /** Ghép mọi tổ hợp hợp lệ từ request, lấy ≤ requestedCount; ít hơn ⇒ shortfall; 0 bộ ⇒ INSUFFICIENT_WARDROBE. */
    data object Generate : RecommendationScenario
    /** Chỉ một bộ hợp lệ đầu tiên (shortfall nếu requestedCount > 1). */
    data object Single : RecommendationScenario
    /** Vd. INVALID_AI_OUTPUT (AI trả ID lạ, client chặn cả response), UPSTREAM_TIMEOUT, RATE_LIMITED, BUDGET_EXCEEDED. */
    data class Failure(val code: ErrorCode) : RecommendationScenario
    /** Treo tới khi caller huỷ — để test cancel/timeout ở ViewModel. */
    data object Hang : RecommendationScenario
}

class FakeOutfitRecommendation(
    var scenario: RecommendationScenario = RecommendationScenario.Generate,
    var delayMs: Long = 0,
    override val capability: RecommendationCapability =
        RecommendationCapability(supportsReroll = true, supportsFocus = true, source = OutfitSource.FAKE),
) : OutfitRecommendation {
    val requests: MutableList<OutfitRequest> = CopyOnWriteArrayList()

    override suspend fun recommend(request: OutfitRequest): Result<RecommendationResult> {
        requests += request
        try { request.validate() } catch (e: IllegalArgumentException) {
            return Result.failure(WardrobeException(ErrorCode.INVALID_REQUEST, e.message ?: "Dữ liệu không hợp lệ."))
        }
        if (delayMs > 0) delay(delayMs)
        val combos = when (val s = scenario) {
            RecommendationScenario.Hang -> awaitCancellation()
            is RecommendationScenario.Failure -> return Result.failure(WardrobeException(s.code, "DEV fake ${s.code}"))
            RecommendationScenario.Generate -> validCombinations(request).take(request.requestedCount)
            RecommendationScenario.Single -> validCombinations(request).take(1)
        }
        if (combos.isEmpty()) {
            return Result.failure(WardrobeException(ErrorCode.INSUFFICIENT_WARDROBE, "Tủ đồ chưa đủ món để phối một bộ hợp lệ."))
        }
        val outfits = combos.map { items ->
            val signature = outfitSignature(items.map { it.id })
            Outfit(
                id = UUID.nameUUIDFromBytes("fake-outfit:${request.requestId}:$signature".toByteArray()).toString(),
                requestId = request.requestId,
                wardrobeSnapshotId = request.wardrobeSnapshotId,
                items = items.map { ItemRevision(it.id, it.revision) },
                explanation = "(FAKE) Gợi ý thử: ${items.joinToString(" + ") { categoryLabel(it.attributes.category) }} cho dịp ${request.occasion.name.lowercase()}.",
                source = OutfitSource.FAKE,
                createdAt = FAKE_CREATED_AT,
                signature = signature,
            )
        }
        val shortfall = if (outfits.size < request.requestedCount) ShortfallReason.NOT_ENOUGH_DISTINCT_COMBINATIONS else null
        return Result.success(RecommendationResult(request.requestId, outfits, shortfall))
    }
}

private val ADDONS = listOf(Category.SHOES, Category.OUTERWEAR, Category.ACCESSORY)

/**
 * Tổ hợp hợp lệ CT-02, thứ tự ổn định: base (TOP×BOTTOM rồi DRESS), mỗi base thêm 0 hoặc 1 add-on.
 * Tôn trọng focus và excludeOutfitSignatures; không bao giờ dùng món UNKNOWN.
 */
fun validCombinations(request: OutfitRequest): List<List<ItemSummary>> {
    val by = request.items.sortedBy { it.id }.groupBy { it.attributes.category }
    fun of(c: Category) = by[c].orEmpty()
    val bases = of(Category.TOP).flatMap { t -> of(Category.BOTTOM).map { b -> listOf(t, b) } } + of(Category.DRESS).map { listOf(it) }
    val addons = ADDONS.flatMap(::of)
    val excluded = request.excludeOutfitSignatures.map { outfitSignature(it.split('|').filter(String::isNotEmpty)) }.toSet()
    return bases.flatMap { base -> listOf(base) + addons.map { base + it } }
        .filter { combo -> request.focusItemId == null || combo.any { it.id == request.focusItemId } }
        .filter { combo -> outfitSignature(combo.map { it.id }) !in excluded }
        .distinctBy { combo -> outfitSignature(combo.map { it.id }) }
}

private fun categoryLabel(c: Category) = when (c) {
    Category.TOP -> "áo"; Category.BOTTOM -> "quần/váy ngắn"; Category.DRESS -> "đầm"; Category.OUTERWEAR -> "áo khoác"
    Category.SHOES -> "giày"; Category.ACCESSORY -> "phụ kiện"; Category.UNKNOWN -> "món chưa rõ"
}

/** Kịch bản recognition. Failure: UPSTREAM_TIMEOUT, STALE_INPUT, PAYLOAD_TOO_LARGE, RATE_LIMITED… */
sealed interface RecognitionScenario {
    /** Gợi ý TOP trắng trơn; viewEvidence theo ảnh thực sự gửi (front-only hoặc dual). */
    data object Success : RecognitionScenario
    /** Hai ảnh có vẻ khác món ⇒ cảnh báo + mọi field cần review. */
    data object Conflict : RecognitionScenario
    /** AI không nhận ra loại ⇒ UNKNOWN + review CATEGORY. */
    data object UnknownCategory : RecognitionScenario
    data class Failure(val code: ErrorCode) : RecognitionScenario
    data object Hang : RecognitionScenario
}

const val FAKE_CONFLICT_WARNING = "Ảnh trước và sau có vẻ là hai món khác nhau. Hãy kiểm tra lại mọi thông tin."
const val FAKE_UNKNOWN_WARNING = "AI chưa nhận ra loại đồ. Hãy chọn loại thủ công."

class FakeClothingRecognition(
    var scenario: RecognitionScenario = RecognitionScenario.Success,
    supportsBack: Boolean = true,
    var delayMs: Long = 0,
) : ClothingRecognition {
    override val capability = RecognitionCapability("DEV fake", "fake-v2", available = true, supportsBack = supportsBack)
    /** Chỉ request đã qua kiểm consent/capability (tức là "đã upload"). */
    val uploaded: MutableList<RecognitionRequest> = CopyOnWriteArrayList()

    override suspend fun recognize(request: RecognitionRequest): Result<RecognitionResult> {
        try { request.validate(capability) } catch (e: WardrobeException) { return Result.failure(e) }
        catch (e: IllegalArgumentException) { return Result.failure(WardrobeException(ErrorCode.INVALID_REQUEST, e.message ?: "Dữ liệu không hợp lệ.")) }
        uploaded += request
        if (delayMs > 0) delay(delayMs)
        val views = request.images.refs().map { it.view }.toSet()
        fun result(attributes: ClothingAttributes, review: Set<AttributeField>, warnings: List<String>, evidence: Map<AttributeField, Set<ImageView>>) =
            Result.success(RecognitionResult(request.requestId, request.itemId, request.inputRevision, attributes, review, evidence, warnings, ResultSource.FAKE))
        return when (val s = scenario) {
            RecognitionScenario.Hang -> awaitCancellation()
            is RecognitionScenario.Failure -> Result.failure(WardrobeException(s.code, "DEV fake ${s.code}"))
            RecognitionScenario.Success -> result(
                ClothingAttributes(Category.TOP, listOf(GarmentColor.WHITE), Pattern.SOLID, formality = 2, seasonTags = listOf("SUMMER"), styleTags = listOf("tối giản")),
                review = setOf(AttributeField.MATERIAL), warnings = emptyList(),
                evidence = mapOf(AttributeField.CATEGORY to views, AttributeField.COLORS to setOf(ImageView.FRONT)),
            )
            RecognitionScenario.Conflict -> result(
                ClothingAttributes(Category.TOP, listOf(GarmentColor.WHITE, GarmentColor.BLUE), Pattern.UNKNOWN),
                review = AttributeField.entries.toSet(), warnings = listOf(FAKE_CONFLICT_WARNING),
                evidence = mapOf(AttributeField.COLORS to views),
            )
            RecognitionScenario.UnknownCategory -> result(
                ClothingAttributes(Category.UNKNOWN, listOf(GarmentColor.OTHER)),
                review = setOf(AttributeField.CATEGORY), warnings = listOf(FAKE_UNKNOWN_WARNING), evidence = emptyMap(),
            )
        }
    }
}

/**
 * Weather context cho OutfitRequest (CT-02). Chưa có interface WeatherProvider v2 trong core/model —
 * đây chỉ là giá trị mẫu; `null` = chưa có dữ liệu thời tiết (request vẫn hợp lệ).
 */
object FakeWeather {
    const val SOURCE = "FAKE"
    fun current(now: Long = FAKE_CREATED_AT) =
        WeatherSnapshot("Hà Nội", 24.0, "Nhiều mây", validAt = now, fetchedAt = now, source = SOURCE, isStale = false)
    fun stale(now: Long = FAKE_CREATED_AT) =
        WeatherSnapshot("Hà Nội", 31.0, "Nắng", validAt = now - 6 * 3_600_000, fetchedAt = now - 6 * 3_600_000, source = SOURCE, isStale = true)
    val none: WeatherSnapshot? = null
}

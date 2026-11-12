package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.recommendation.MAX_EXPLANATION
import com.fitcheckai.core.model.recommendation.Outfit
import com.fitcheckai.core.model.recommendation.OutfitRecommendation
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.RecommendationCapability
import com.fitcheckai.core.model.recommendation.RecommendationResult
import com.fitcheckai.core.model.recommendation.ShortfallReason
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ItemRevision
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.requireUuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

// OWNER: Track B (B-003). Client `POST /v2/outfits` — CT-02/07. D chỉ thấy interface OutfitRecommendation.

internal const val OUTFITS_BODY_LIMIT_BYTES = 64 * 1024
private const val WIRE_MAX_MATERIAL = 40

@Serializable internal data class WireAttributes(
    val category: String,
    val colors: List<String>,
    val pattern: String?,
    val styleTags: List<String>,
    val material: String?,
    val formality: Int?,
    val seasonTags: List<String>,
)
@Serializable internal data class WireItem(val id: String, val revision: Long, val attributes: WireAttributes)
@Serializable internal data class WireProfile(val heightCm: Double?, val stylePreferences: List<String>, val preferredCategories: List<String>)
@Serializable internal data class WireWeather(
    val cityLabel: String, val tempC: Double, val condition: String,
    val validAt: Long, val fetchedAt: Long, val source: String, val isStale: Boolean,
)
@Serializable internal data class WireOutfitRequest(
    val schemaVersion: Int,
    val requestId: String,
    val wardrobeSnapshotId: String,
    val items: List<WireItem>,
    val profile: WireProfile?,
    val occasion: String,
    val styleOverrides: List<String>,
    val freeText: String,
    val weather: WireWeather?,
    val requestedCount: Int,
    val focusItemId: String?,
    val excludeOutfitSignatures: List<String>,
    val regenerationOfRequestId: String?,
)

@Serializable internal data class WireItemRef(val itemId: String, val revision: Long)
@Serializable internal data class WireOutfit(
    val id: String,
    val requestId: String,
    val wardrobeSnapshotId: String,
    val items: List<WireItemRef>,
    val explanation: String,
    val source: String,
    val createdAt: Long,
    val signature: String,
)
@Serializable internal data class WireOutfitResponse(
    val requestId: String,
    val outfits: List<WireOutfit>,
    val shortfallReason: String? = null,
)

/**
 * Chỉ gửi metadata + profile projection (không ảnh, không cân nặng, không local path).
 * Response được kiểm lại toàn bộ invariant CT-02; một bộ sai ⇒ bỏ cả response ([ErrorCode.INVALID_AI_OUTPUT]),
 * không sửa và không đưa phần còn lại vào UI.
 */
class ProxyOutfitRecommendation(
    private val transport: ProxyTransport,
) : OutfitRecommendation {
    private val ledger = RequestLedger()

    /** Focus/reroll: server đã kiểm, nhưng C-102/LATER-006 chưa có evidence ⇒ chưa bật CTA. */
    override val capability = RecommendationCapability(supportsReroll = false, supportsFocus = false, source = OutfitSource.AI_LIVE)

    override suspend fun recommend(request: OutfitRequest): Result<RecommendationResult> = try {
        val body = encodeOutfitRequest(request)
        if (!transport.configured) throw proxyError(ErrorCode.UNSUPPORTED_CAPABILITY)
        if (!ledger.claim(request.requestId)) throw proxyError(ErrorCode.CONFLICT)
        when (val response = transport.postJson("/v2/outfits", body)) {
            is ProxyResponse.Failed -> Result.failure(response.error)
            is ProxyResponse.Ok -> Result.success(parseOutfitResponse(request, response.body))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (known: WardrobeException) {
        Result.failure(known)
    } catch (_: Exception) {
        Result.failure(proxyError(ErrorCode.INVALID_AI_OUTPUT))
    }
}

/** Validate local rồi encode; vi phạm ⇒ INVALID_REQUEST/PAYLOAD_TOO_LARGE, không gọi mạng. */
internal fun encodeOutfitRequest(request: OutfitRequest): String {
    try { request.validate() } catch (e: IllegalArgumentException) {
        throw WardrobeException(ErrorCode.INVALID_REQUEST, e.message ?: userMessage(ErrorCode.INVALID_REQUEST))
    }
    val ids = listOfNotNull(request.requestId, request.wardrobeSnapshotId, request.focusItemId, request.regenerationOfRequestId) +
        request.items.map { it.id }
    if (ids.any { !WIRE_ID.matches(it) }) throw proxyError(ErrorCode.INVALID_REQUEST)
    if (request.focusItemId != null && request.items.first { it.id == request.focusItemId }.attributes.category == Category.UNKNOWN) {
        throw WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Món chưa rõ loại nên chưa phối quanh món này được.")
    }
    val wire = WireOutfitRequest(
        schemaVersion = request.schemaVersion,
        requestId = request.requestId,
        wardrobeSnapshotId = request.wardrobeSnapshotId,
        items = request.items.map { item ->
            val a = item.attributes
            WireItem(item.id, item.revision, WireAttributes(
                category = a.category.name,
                colors = a.colors.map { it.name },
                pattern = a.pattern.name,
                styleTags = a.styleTags.map { it.trim() },
                // Wire chỉ nhận ≤40 ký tự; chất liệu dài chỉ là gợi ý phụ nên bỏ thay vì cắt giữa chữ.
                material = a.material?.trim()?.takeIf { it.isNotEmpty() && it.length <= WIRE_MAX_MATERIAL },
                formality = a.formality,
                seasonTags = a.seasonTags,
            ))
        },
        profile = request.profile?.let { p ->
            WireProfile(
                heightCm = p.heightCm?.takeIf { it.isFinite() && it > 0 && it <= 300 },
                stylePreferences = p.stylePreferences.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 32 }.take(5),
                preferredCategories = p.preferredCategories.map { it.name }.sorted(),
            )
        },
        occasion = request.occasion.name,
        styleOverrides = request.styleOverrides.map { it.trim() },
        freeText = request.freeText,
        // Thời tiết là context phụ (C-101): ngoài biên wire thì gửi null, không chặn phối đồ.
        weather = request.weather?.takeIf { w ->
            w.cityLabel.trim().length in 1..64 && w.condition.trim().length in 1..64 && w.source.trim().length in 1..64 &&
                w.tempC.isFinite() && w.tempC in -60.0..60.0 && w.validAt >= 0 && w.fetchedAt >= 0
        }?.let { WireWeather(it.cityLabel, it.tempC, it.condition, it.validAt, it.fetchedAt, it.source, it.isStale) },
        requestedCount = request.requestedCount,
        focusItemId = request.focusItemId,
        excludeOutfitSignatures = request.excludeOutfitSignatures.map(::canonicalSignature).distinct().sorted(),
        regenerationOfRequestId = request.regenerationOfRequestId,
    )
    val body = wireJson.encodeToString(WireOutfitRequest.serializer(), wire)
    if (body.toByteArray(Charsets.UTF_8).size > OUTFITS_BODY_LIMIT_BYTES) throw proxyError(ErrorCode.PAYLOAD_TOO_LARGE)
    return body
}

internal fun canonicalSignature(signature: String): String = outfitSignature(signature.split('|').filter { it.isNotEmpty() })

/** Kiểm response 200 theo CT-02 (cùng luật eval/ai/lib/invariants.mjs). */
internal fun parseOutfitResponse(request: OutfitRequest, body: String): RecommendationResult {
    val wire = runCatching { wireJson.decodeFromString(WireOutfitResponse.serializer(), body) }
        .getOrElse { throw proxyError(ErrorCode.INVALID_AI_OUTPUT) }
    fun invalid(): Nothing = throw proxyError(ErrorCode.INVALID_AI_OUTPUT)

    if (wire.requestId != request.requestId) invalid()
    // Server hứa không bao giờ success rỗng (khi đó là 409); rỗng ở đây là sai contract.
    if (wire.outfits.isEmpty() || wire.outfits.size > request.requestedCount) invalid()
    val shortfall = wire.shortfallReason?.let { reason -> ShortfallReason.entries.firstOrNull { it.name == reason } ?: invalid() }
    if ((wire.outfits.size < request.requestedCount) != (shortfall != null)) invalid()

    val catalog = request.items.associateBy { it.id }
    val excluded = request.excludeOutfitSignatures.map(::canonicalSignature).toSet()
    val seenSignatures = HashSet<String>()
    val seenIds = HashSet<String>()

    val outfits = wire.outfits.map { o ->
        runCatching { requireUuid(o.id) }.getOrElse { invalid() }
        if (!seenIds.add(o.id)) invalid()
        if (o.requestId != request.requestId || o.wardrobeSnapshotId != request.wardrobeSnapshotId) invalid()
        val source = when (o.source) {
            "AI_LIVE" -> OutfitSource.AI_LIVE
            "FAKE" -> OutfitSource.FAKE
            else -> invalid() // RULES là fallback local (C-002), proxy không được tự xưng.
        }
        val explanation = o.explanation.trim()
        if (explanation.isEmpty() || o.explanation.length > MAX_EXPLANATION) invalid()
        if (o.createdAt < 0) invalid()
        val ids = o.items.map { it.itemId }
        if (ids.isEmpty() || ids.toSet().size != ids.size) invalid()
        val categories = o.items.map { ref ->
            val item = catalog[ref.itemId] ?: invalid()
            if (item.revision != ref.revision) invalid()
            item.attributes.category
        }
        if (!slotsValid(categories)) invalid()
        if (request.focusItemId != null && request.focusItemId !in ids) invalid()
        val signature = outfitSignature(ids)
        if (o.signature != signature || signature in excluded || !seenSignatures.add(signature)) invalid()
        Outfit(
            id = o.id,
            requestId = o.requestId,
            wardrobeSnapshotId = o.wardrobeSnapshotId,
            items = o.items.map { ItemRevision(it.itemId, it.revision) },
            explanation = explanation,
            source = source,
            createdAt = o.createdAt,
            signature = signature,
        )
    }
    return RecommendationResult(request.requestId, outfits, shortfall)
}

/** (1 TOP + 1 BOTTOM) hoặc 1 DRESS; thêm ≤1 mỗi OUTERWEAR/SHOES/ACCESSORY; không UNKNOWN. */
internal fun slotsValid(categories: List<Category>): Boolean {
    val count = categories.groupingBy { it }.eachCount()
    if (Category.UNKNOWN in count || count.values.any { it > 1 }) return false
    return if (Category.DRESS in count) Category.TOP !in count && Category.BOTTOM !in count
    else Category.TOP in count && Category.BOTTOM in count
}

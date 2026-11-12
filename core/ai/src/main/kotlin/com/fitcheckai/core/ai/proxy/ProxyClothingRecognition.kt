package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ClothingRecognition
import com.fitcheckai.core.model.wardrobe.DerivativePreset
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaScope
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.core.model.wardrobe.Pattern
import com.fitcheckai.core.model.wardrobe.RecognitionCapability
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.RecognitionResult
import com.fitcheckai.core.model.wardrobe.ResultSource
import com.fitcheckai.core.model.wardrobe.WardrobeException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// OWNER: Track B (B-007). Client `POST /v2/recognition` — CT-05/07. A chỉ thấy interface ClothingRecognition.

internal const val RECOGNITION_BODY_LIMIT_BYTES = 4 * 1024 * 1024
internal const val RECOGNITION_IMAGE_LIMIT_BYTES = 1024 * 1024
internal const val UNVERIFIED_PROVIDER = "fitcheck-proxy"
internal const val UNVERIFIED_CONFIG = "unverified"

@Serializable internal data class WireImage(
    val view: String, val mediaId: String, val mimeType: String, val sha256: String, val dataBase64: String,
)
@Serializable internal data class WireConsent(
    val grantId: String,
    val purpose: String,
    val providerConfigVersion: String,
    val mediaIds: List<String>,
    val issuedAt: Long,
    val revokedAt: Long?,
    val userConfirmed: Boolean,
)
@Serializable internal data class WireRecognitionRequest(
    val schemaVersion: Int,
    val requestId: String,
    val itemId: String,
    val inputRevision: Long,
    val front: WireImage,
    val back: WireImage?,
    val consentGrant: WireConsent,
)
@Serializable internal data class WireSuggested(
    val category: String,
    val colors: List<String> = emptyList(),
    val pattern: String? = null,
    val styleTags: List<String> = emptyList(),
    val material: String? = null,
    val formality: Int? = null,
    val seasonTags: List<String> = emptyList(),
)
@Serializable internal data class WireRecognitionResponse(
    val requestId: String,
    val itemId: String,
    val inputRevision: Long,
    val suggestedAttributes: WireSuggested,
    val needsReviewFields: List<String> = emptyList(),
    val viewEvidence: Map<String, List<String>> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val source: String,
)
@Serializable internal data class WireServices(val recognition: Boolean = false)
/** Provider riêng của một service (proxy có thể dùng provider khác nhau cho recognition và outfits). */
@Serializable internal data class WireServiceProvider(val provider: String, val model: String = "", val configured: Boolean)
@Serializable internal data class WireHealth(
    val version: String,
    val schemaVersion: Int,
    val services: WireServices,
    val recognitionViews: String,
    val provider: String,
    val providerConfigured: Boolean,
    val providers: Map<String, WireServiceProvider> = emptyMap(),
)

/**
 * Gửi derivative RECOGNITION của đúng ảnh garment trong [RecognitionRequest] (không bao giờ ảnh profile/person).
 * Consent sai/thiếu ⇒ lỗi trước khi đọc ảnh hay gọi mạng. Kết quả chỉ là gợi ý; không lưu gì.
 * Capability ban đầu = FRONT_ONLY chưa xác minh; [refreshCapability] đọc `/health` để biết provider/DUAL.
 */
class ProxyClothingRecognition(
    private val transport: ProxyTransport,
    private val media: MediaStore,
) : ClothingRecognition {
    private val ledger = RequestLedger()

    @Volatile private var current = RecognitionCapability(UNVERIFIED_PROVIDER, UNVERIFIED_CONFIG, transport.configured, supportsBack = false)
    override val capability: RecognitionCapability get() = current

    /** Gọi khi mở màn hình nhập đồ (không có dữ liệu người dùng). Capability đổi ⇒ consent cũ không còn khớp. */
    suspend fun refreshCapability(): Result<RecognitionCapability> {
        if (!transport.configured) return Result.success(current.copy(available = false).also { current = it })
        return when (val response = transport.get("/health")) {
            is ProxyResponse.Failed -> Result.failure(response.error)
            is ProxyResponse.Ok -> runCatching { wireJson.decodeFromString(WireHealth.serializer(), response.body) }.fold(
                { health ->
                    // Ưu tiên provider riêng của recognition: consent phải nêu đúng nơi ẢNH được gửi.
                    val own = health.providers["recognition"]
                    val provider = own?.provider ?: health.provider
                    val usable = health.schemaVersion == 2 && health.services.recognition && (own?.configured ?: health.providerConfigured)
                    val capability = RecognitionCapability(
                        provider = provider.ifBlank { UNVERIFIED_PROVIDER }.take(32),
                        // Đổi provider/model ⇒ configVersion đổi ⇒ consent cũ không còn khớp.
                        configVersion = listOf(health.version, health.recognitionViews, provider, own?.model.orEmpty())
                            .joinToString("/").take(64),
                        available = usable,
                        supportsBack = usable && health.recognitionViews == "DUAL",
                    )
                    current = capability
                    Result.success(capability)
                },
                { Result.failure(proxyError(ErrorCode.UPSTREAM_ERROR)) },
            )
        }
    }

    override suspend fun recognize(request: RecognitionRequest): Result<RecognitionResult> = try {
        val capability = current
        request.validate(capability) // consent/capability/ảnh — trước mọi I/O
        if (!transport.configured) throw proxyError(ErrorCode.UNSUPPORTED_CAPABILITY)
        // Claim trước khi đọc ảnh: gọi lại cùng requestId (recomposition/lifecycle) không đọc/upload lần hai.
        if (!ledger.claim(request.requestId)) throw proxyError(ErrorCode.CONFLICT)
        val front = encodeImage(request.images.front!!, request.itemId, ImageView.FRONT)
        val back = request.images.back?.let { encodeImage(it, request.itemId, ImageView.BACK) }
        if (back != null && back.sha256 == front.sha256) {
            throw WardrobeException(ErrorCode.INVALID_REQUEST, "Ảnh sau trùng ảnh trước; hãy chọn ảnh mặt sau khác.")
        }
        val grant = request.consentGrant
        val body = wireJson.encodeToString(WireRecognitionRequest.serializer(), WireRecognitionRequest(
            schemaVersion = request.schemaVersion,
            requestId = request.requestId,
            itemId = request.itemId,
            inputRevision = request.inputRevision,
            front = front,
            back = back,
            consentGrant = WireConsent(grant.id, grant.purpose.name, grant.providerConfigVersion,
                request.images.refs().map { it.id }, grant.issuedAt, grant.revokedAt, grant.confirmedByUser),
        ))
        if (body.toByteArray(Charsets.UTF_8).size > RECOGNITION_BODY_LIMIT_BYTES) throw proxyError(ErrorCode.PAYLOAD_TOO_LARGE)
        when (val response = transport.postJson("/v2/recognition", body)) {
            is ProxyResponse.Failed -> {
                learnFrom(response, sentBack = back != null)
                Result.failure(response.error)
            }
            is ProxyResponse.Ok -> Result.success(parseRecognitionResponse(request, response.body))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (known: WardrobeException) {
        Result.failure(known)
    } catch (invalid: IllegalArgumentException) {
        Result.failure(WardrobeException(ErrorCode.INVALID_REQUEST, invalid.message ?: userMessage(ErrorCode.INVALID_REQUEST)))
    } catch (_: Exception) {
        Result.failure(proxyError(ErrorCode.INVALID_AI_OUTPUT))
    }

    /** Server từ chối ảnh sau (FRONT_ONLY) hoặc tắt dịch vụ ⇒ hạ capability để UI báo và chỉ gửi ảnh trước lần sau. */
    private fun learnFrom(response: ProxyResponse.Failed, sentBack: Boolean) {
        if (response.error.code != ErrorCode.UNSUPPORTED_CAPABILITY) return
        current = when {
            response.httpStatus == 503 -> current.copy(available = false, supportsBack = false)
            sentBack && response.httpStatus == 400 -> current.copy(supportsBack = false)
            else -> current
        }
    }

    /** Derivative ≤1024px/≤1MiB của đúng ảnh; file tạm luôn được dọn kể cả khi bị huỷ. */
    private suspend fun encodeImage(ref: MediaRef, itemId: String, view: ImageView): WireImage {
        if (ref.scope != MediaScope.GARMENT || ref.ownerEntityId != itemId || ref.view != view) {
            throw WardrobeException(ErrorCode.INVALID_REQUEST, "Ảnh không thuộc món đồ đang nhận diện.")
        }
        val derived = media.derive(ref, DerivativePreset.RECOGNITION).getOrElse { throw ioError(it) }
        try {
            if (derived.scope != MediaScope.GARMENT || derived.ownerEntityId != itemId || derived.view != view) {
                throw WardrobeException(ErrorCode.INVALID_REQUEST, "Ảnh không thuộc món đồ đang nhận diện.")
            }
            val bytes = media.read(derived).getOrElse { throw ioError(it) }
            if (bytes.isEmpty()) throw proxyError(ErrorCode.IO)
            if (bytes.size > RECOGNITION_IMAGE_LIMIT_BYTES) throw proxyError(ErrorCode.PAYLOAD_TOO_LARGE)
            val mime = sniffMime(bytes)
            if (mime == null || mime != derived.mimeType) {
                throw WardrobeException(ErrorCode.INVALID_REQUEST, "Ảnh không đúng định dạng JPEG/PNG.")
            }
            return WireImage(view.name, ref.id, mime, sha256Hex(bytes), Base64.getEncoder().encodeToString(bytes))
        } finally {
            withContext(NonCancellable) { media.remove(derived) }
        }
    }

    private fun ioError(cause: Throwable): WardrobeException =
        cause as? WardrobeException ?: proxyError(ErrorCode.IO)
}

internal fun sniffMime(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
    else -> null
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private val WIRE_FIELDS = mapOf(
    "category" to AttributeField.CATEGORY, "colors" to AttributeField.COLORS, "pattern" to AttributeField.PATTERN,
    "material" to AttributeField.MATERIAL, "formality" to AttributeField.FORMALITY, "seasonTags" to AttributeField.SEASONS,
    "styleTags" to AttributeField.STYLES,
)
private val SEASONS = setOf("SPRING", "SUMMER", "AUTUMN", "WINTER")

/** Thông điệp người dùng đọc được (A hiển thị warnings trực tiếp). Mã lạ ⇒ câu chung, không lộ mã thô. */
internal fun warningMessage(code: String): String = when (code) {
    "VIEWS_LOOK_LIKE_DIFFERENT_ITEMS" -> "Ảnh trước và sau có vẻ là hai món khác nhau. Hãy kiểm tra lại mọi thông tin."
    "CATEGORY_UNKNOWN" -> "AI chưa nhận ra loại đồ. Hãy chọn loại thủ công."
    else -> "AI có lưu ý về ảnh này. Hãy kiểm tra lại thông tin gợi ý."
}

/**
 * Map response sang [RecognitionResult]. Sai request/item/revision ⇒ [ErrorCode.STALE_INPUT] (không áp lên draft).
 * Giá trị lạ/vượt giới hạn được chuẩn hóa và đánh dấu cần review thay vì làm hỏng cả kết quả.
 */
internal fun parseRecognitionResponse(request: RecognitionRequest, body: String): RecognitionResult {
    val wire = runCatching { wireJson.decodeFromString(WireRecognitionResponse.serializer(), body) }
        .getOrElse { throw proxyError(ErrorCode.INVALID_AI_OUTPUT) }
    if (wire.requestId != request.requestId || wire.itemId != request.itemId || wire.inputRevision != request.inputRevision) {
        throw proxyError(ErrorCode.STALE_INPUT)
    }
    val source = when (wire.source) {
        "AI_LIVE" -> ResultSource.AI_LIVE
        "FAKE" -> ResultSource.FAKE
        else -> throw proxyError(ErrorCode.INVALID_AI_OUTPUT)
    }
    val review = wire.needsReviewFields.mapNotNull(WIRE_FIELDS::get).toMutableSet()
    val s = wire.suggestedAttributes

    val category = Category.entries.firstOrNull { it.name == s.category } ?: Category.UNKNOWN
    if (category == Category.UNKNOWN) review += AttributeField.CATEGORY
    val parsedColors = s.colors.map { name -> GarmentColor.entries.firstOrNull { it.name == name } }
    val colors = parsedColors.filterNotNull().distinct()
    if (parsedColors.any { it == null } || colors.size > 3) review += AttributeField.COLORS
    val pattern = s.pattern?.let { name -> Pattern.entries.firstOrNull { it.name == name } }
        ?: Pattern.UNKNOWN.also { if (s.pattern != null) review += AttributeField.PATTERN }
    val material = s.material?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 }
    if (s.material != null && material == null) review += AttributeField.MATERIAL
    val formality = s.formality?.takeIf { it in 1..5 }
    if (s.formality != null && formality == null) review += AttributeField.FORMALITY
    val seasons = s.seasonTags.filter { it in SEASONS }.distinct()
    if (seasons.size != s.seasonTags.size || seasons.size > 4) review += AttributeField.SEASONS
    val styles = s.styleTags.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 32 }.distinct()
    if (styles.size != s.styleTags.size || styles.size > 5) review += AttributeField.STYLES

    val sentViews = request.images.refs().map { it.view }.toSet()
    val evidence = wire.viewEvidence.mapNotNull { (field, views) ->
        val mapped = WIRE_FIELDS[field] ?: return@mapNotNull null
        val parsed = views.mapNotNull { v -> ImageView.entries.firstOrNull { it.name == v } }.toSet()
        // Server không được nói "thấy ở ảnh sau" khi không gửi ảnh sau.
        if (!sentViews.containsAll(parsed)) review += mapped
        (parsed intersect sentViews).takeIf { it.isNotEmpty() }?.let { mapped to it }
    }.toMap()

    val suggestion = ClothingAttributes(
        category = category,
        colors = colors.take(3),
        pattern = pattern,
        material = material,
        formality = formality,
        seasonTags = seasons.take(4),
        styleTags = styles.take(5),
    )
    suggestion.validate()
    return RecognitionResult(
        requestId = wire.requestId,
        itemId = wire.itemId,
        inputRevision = wire.inputRevision,
        suggestedAttributes = suggestion,
        needsReviewFields = review,
        viewEvidence = evidence,
        warnings = wire.warnings.distinct().map(::warningMessage).distinct(),
        source = source,
    )
}

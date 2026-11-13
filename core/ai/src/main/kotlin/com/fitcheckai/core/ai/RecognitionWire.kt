package com.fitcheckai.core.ai

import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.GarmentType
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.Pattern
import com.fitcheckai.core.model.wardrobe.RecognitionRequest
import com.fitcheckai.core.model.wardrobe.RecognitionResult
import com.fitcheckai.core.model.wardrobe.ResultSource
import com.fitcheckai.core.model.wardrobe.WardrobeException
import kotlinx.serialization.Serializable

@Serializable
internal data class ProxyHealth(
    val status: String,
    val version: String? = null,
    val schemaVersion: Int,
    val services: Map<String, Boolean>,
    val recognitionViews: String,
    val provider: String,
    val providerConfigured: Boolean,
    /** Provider thật từng service (proxy có thể dùng Gemini cho recognition, GPT cho outfits). */
    val providers: Map<String, ServiceProvider> = emptyMap(),
    val recognitionGarmentTypeVersion: Int? = null,
)

@Serializable
internal data class ServiceProvider(val provider: String, val model: String = "", val configured: Boolean)

@Serializable
internal data class DemoSessionRequest(val accessCode: String)

@Serializable
internal data class DemoSessionResponse(val token: String, val expiresAt: Long)

@Serializable
internal data class RecognitionImagePayload(
    val view: String,
    val mediaId: String,
    val mimeType: String,
    val sha256: String,
    val dataBase64: String,
)

@Serializable
internal data class RecognitionConsentPayload(
    val grantId: String,
    val purpose: String,
    val providerConfigVersion: String,
    val mediaIds: List<String>,
    val issuedAt: Long,
    val revokedAt: Long?,
    val userConfirmed: Boolean,
)

@Serializable
internal data class RecognitionPayload(
    val schemaVersion: Int,
    val requestId: String,
    val itemId: String,
    val inputRevision: Long,
    val front: RecognitionImagePayload,
    val back: RecognitionImagePayload?,
    val consentGrant: RecognitionConsentPayload,
    val garmentTypeVersion: Int? = null,
)

@Serializable
internal data class RecognitionAttributesResponse(
    val category: String,
    val colors: List<String>,
    val pattern: String,
    val material: String?,
    val formality: Int?,
    val seasonTags: List<String>,
    val styleTags: List<String>,
    val garmentType: String? = null,
)

@Serializable
internal data class RecognitionResponse(
    val requestId: String,
    val itemId: String,
    val inputRevision: Long,
    val suggestedAttributes: RecognitionAttributesResponse,
    val needsReviewFields: List<String>,
    val viewEvidence: Map<String, List<String>>,
    val warnings: List<String>,
    val source: String,
) {
    fun toDomain(request: RecognitionRequest): RecognitionResult {
        if (requestId != request.requestId || itemId != request.itemId || inputRevision != request.inputRevision) {
            throw WardrobeException(ErrorCode.STALE_INPUT, "Kết quả không khớp với ảnh đã gửi. Hãy thử lại.")
        }
        try {
            require(source == ResultSource.AI_LIVE.name)
            val sentViews = request.images.refs().map { it.view }.toSet()
            val evidence = viewEvidence.entries.associate { (key, views) ->
                val parsed = views.map(ImageView::valueOf).toSet()
                require(parsed.all(sentViews::contains))
                field(key) to parsed
            }
            val attributes = with(suggestedAttributes) {
                ClothingAttributes(
                    category = Category.valueOf(category), colors = colors.map(GarmentColor::valueOf),
                    pattern = Pattern.valueOf(pattern), material = material, formality = formality,
                    seasonTags = seasonTags, styleTags = styleTags,
                    garmentType = garmentType?.also { require(GarmentType.find(it) != null) },
                ).also { it.validate() }
            }
            return RecognitionResult(
                requestId, itemId, inputRevision, attributes, needsReviewFields.map(::field).toSet(), evidence,
                warnings.map { warning ->
                    when (warning) {
                        "VIEWS_LOOK_LIKE_DIFFERENT_ITEMS" -> "Hai ảnh có thể là hai món khác nhau. Hãy kiểm tra lại."
                        "CATEGORY_UNKNOWN" -> "Chưa xác định được loại đồ. Bạn có thể tự chọn."
                        else -> "Hãy kiểm tra lại các thuộc tính được gợi ý."
                    }
                }.distinct(),
                ResultSource.AI_LIVE,
            )
        } catch (_: IllegalArgumentException) {
            throw WardrobeException(ErrorCode.INVALID_AI_OUTPUT, "Kết quả nhận diện không hợp lệ. Bạn có thể tự điền.")
        }
    }

    private fun field(wire: String): AttributeField = when (wire) {
        "category" -> AttributeField.CATEGORY
        "colors" -> AttributeField.COLORS
        "pattern" -> AttributeField.PATTERN
        "material" -> AttributeField.MATERIAL
        "formality" -> AttributeField.FORMALITY
        "seasonTags" -> AttributeField.SEASONS
        "styleTags" -> AttributeField.STYLES
        "garmentType" -> AttributeField.GARMENT_TYPE
        else -> throw IllegalArgumentException("Unknown recognition field")
    }
}

package com.fitcheckai.core.model.wardrobe

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

enum class Category { TOP, BOTTOM, DRESS, OUTERWEAR, SHOES, ACCESSORY, UNKNOWN }
enum class GarmentColor { BLACK, WHITE, GRAY, BEIGE, BROWN, BLUE, GREEN, RED, YELLOW, PINK, PURPLE, OTHER }
enum class Pattern { SOLID, STRIPED, CHECKED, PRINTED, OTHER, UNKNOWN }
enum class MediaScope { GARMENT, PERSON, TRYON_RESULT }
enum class ImageView { FRONT, BACK }
enum class AttributeSource { MANUAL, AI }
enum class AttributeField { CATEGORY, COLORS, PATTERN, MATERIAL, FORMALITY, SEASONS, STYLES }
enum class RecognitionState { NOT_REQUESTED, PENDING, REVIEW, CONFIRMED, FAILED }
enum class ResultSource { AI_LIVE, FAKE }
enum class ErrorCode { INVALID_REQUEST, IO, CONFLICT, STALE_INPUT, NEEDS_INPUT, CONSENT_REQUIRED, UNSUPPORTED_CAPABILITY,
    PAYLOAD_TOO_LARGE, UPSTREAM_TIMEOUT, NETWORK, CANCELLED, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, BUDGET_EXCEEDED,
    INSUFFICIENT_WARDROBE, INVALID_AI_OUTPUT, UPSTREAM_ERROR, EXPIRED }
class WardrobeException(val code: ErrorCode, message: String) : Exception(message)

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val error: WardrobeException) : LoadState<Nothing>
}

fun requireUuid(value: String) {
    require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) { "ID phải là UUID chuẩn chữ thường." }
}

data class MediaRef(
    val id: String,
    val ownerEntityId: String,
    val scope: MediaScope,
    val view: ImageView,
    val localRelativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteCount: Long,
    val contentHash: String,
    val revision: Long = 1,
    val derivedFromMediaId: String? = null,
) {
    fun validate() {
        requireUuid(id); requireUuid(ownerEntityId)
        derivedFromMediaId?.let(::requireUuid)
        require(revision >= 1 && width in 1..2048 && height in 1..2048 && byteCount in 1..4L * 1024 * 1024)
        require(mimeType in setOf("image/jpeg", "image/png"))
        require(contentHash.matches(Regex("[a-f0-9]{64}")))
        require(localRelativePath.isNotBlank() && !localRelativePath.startsWith('/') &&
            localRelativePath.split('/').none { it == ".." || it.isEmpty() } &&
            '\\' !in localRelativePath && ':' !in localRelativePath) { "Đường dẫn ảnh không hợp lệ." }
    }
}

data class ImagePair(val front: MediaRef? = null, val back: MediaRef? = null) {
    fun refs(): List<MediaRef> = listOfNotNull(front, back)
    operator fun get(view: ImageView) = if (view == ImageView.FRONT) front else back
    fun with(view: ImageView, ref: MediaRef?) = if (view == ImageView.FRONT) copy(front = ref) else copy(back = ref)
    fun validate(owner: String, requireFront: Boolean = true, scope: MediaScope = MediaScope.GARMENT) {
        require(!requireFront || front != null) { "Cần ảnh mặt trước trước khi lưu." }
        refs().forEach { it.validate(); require(it.ownerEntityId == owner && it.scope == scope) }
        require(front == null || front.view == ImageView.FRONT)
        require(back == null || back.view == ImageView.BACK)
        require(front == null || back == null || (front.id != back.id && front.contentHash != back.contentHash)) {
            "Ảnh trước và sau phải là hai ảnh khác nhau của cùng món đồ."
        }
    }
}

data class ClothingAttributes(
    val category: Category = Category.UNKNOWN,
    val colors: List<GarmentColor> = emptyList(),
    val pattern: Pattern = Pattern.UNKNOWN,
    val material: String? = null,
    val formality: Int? = null,
    val seasonTags: List<String> = emptyList(),
    val styleTags: List<String> = emptyList(),
    val attributeSources: Map<AttributeField, AttributeSource> = emptyMap(),
    val needsReviewFields: Set<AttributeField> = emptySet(),
) {
    fun validate() {
        require(colors.size <= 3 && colors.distinct().size == colors.size) { "Chọn tối đa 3 màu." }
        require(styleTags.size <= 5 && styleTags.all { it.isNotBlank() && it.length <= 32 }) { "Tối đa 5 nhãn, mỗi nhãn 32 ký tự." }
        require(material == null || (material.isNotBlank() && material.length in 1..100))
        require(formality == null || formality in 1..5)
        require(seasonTags.size <= 4 && seasonTags.all { it in setOf("SPRING", "SUMMER", "AUTUMN", "WINTER") })
    }
}

data class ClothingItem(
    val id: String,
    val name: String,
    val images: ImagePair,
    val attributes: ClothingAttributes = ClothingAttributes(),
    val revision: Long = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val recognitionState: RecognitionState = RecognitionState.NOT_REQUESTED,
) {
    fun validate() {
        requireUuid(id)
        require(name.isNotBlank() && name.length <= 100) { "Tên món đồ cần có 1–100 ký tự." }
        require(revision >= 1 && createdAt >= 0 && updatedAt >= createdAt && deletedAt == null)
        images.validate(id); attributes.validate()
    }
}

data class ItemRevision(val itemId: String, val revision: Long)
data class WardrobeSnapshot(val snapshotId: String, val items: List<ClothingItem>) {
    val references get() = items.map { ItemRevision(it.id, it.revision) }
    fun isCurrent(refs: List<ItemRevision>): Boolean = refs.distinctBy { it.itemId }.size == refs.size &&
        refs.all { ref -> items.any { it.id == ref.itemId && it.revision == ref.revision && it.deletedAt == null } }
}
data class DeletionReceipt(val id: String, val revision: Long, val deletedAt: Long, val cleanupPending: Boolean)
data class WardrobeInvalidation(val itemId: String, val revision: Long, val deleted: Boolean)

interface WardrobeRepository {
    fun observeItems(): Flow<LoadState<List<ClothingItem>>>
    val invalidations: Flow<WardrobeInvalidation>
    suspend fun getSnapshot(): Result<WardrobeSnapshot>
    suspend fun get(id: String): Result<ClothingItem?>
    suspend fun upsert(item: ClothingItem, expectedRevision: Long? = null): Result<ClothingItem>
    suspend fun delete(id: String, expectedRevision: Long): Result<DeletionReceipt>
}

enum class DerivativePreset(val maxEdge: Int, val maxBytes: Long) {
    RECOGNITION(1024, 1024 * 1024), TRYON_PERSON(2048, 2 * 1024 * 1024), THUMBNAIL(384, 256 * 1024), SEGMENTATION(768, 1024 * 1024),
}

interface MediaStore {
    suspend fun importImage(uri: String, scope: MediaScope, ownerId: String, view: ImageView): Result<MediaRef>
    suspend fun read(ref: MediaRef): Result<ByteArray>
    suspend fun remove(ref: MediaRef): Result<DeletionReceipt>
    suspend fun derive(ref: MediaRef, preset: DerivativePreset): Result<MediaRef>
    /** Local display only. Implementations must validate ownership/path before returning it. */
    fun displayPath(ref: MediaRef): String?
    suspend fun retryCleanup(): Result<Int>
    /** Recovery for abandoned garment drafts only; PERSON/result retention belongs to their owners. */
    suspend fun pruneGarmentDrafts(retainedIds: Set<String>, olderThan: Long): Result<Int> = Result.success(0)
}

enum class ConsentPurpose { RECOGNITION, TRYON }
data class ConsentGrant(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val provider: String,
    val providerConfigVersion: String,
    val mediaIds: Set<String>,
    val issuedAt: Long = System.currentTimeMillis(),
    val revokedAt: Long? = null,
    val purpose: ConsentPurpose = ConsentPurpose.RECOGNITION,
    val mediaViews: Map<String, ImageView> = emptyMap(),
    val requestGeneration: Long = 1,
    val confirmedByUser: Boolean = false,
)
data class RecognitionCapability(val provider: String, val configVersion: String, val available: Boolean, val supportsBack: Boolean)
data class RecognitionRequest(
    val requestId: String,
    val itemId: String,
    val inputRevision: Long,
    val images: ImagePair,
    val consentGrant: ConsentGrant,
    val schemaVersion: Int = 2,
) {
    fun validate(capability: RecognitionCapability) {
        requireUuid(requestId); requireUuid(itemId); requireUuid(consentGrant.id); images.validate(itemId)
        require(inputRevision >= 1 && schemaVersion == 2)
        if (!capability.available) throw WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Nhận diện AI chưa được kết nối.")
        if (images.back != null && !capability.supportsBack) throw WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Dịch vụ chưa hỗ trợ ảnh mặt sau.")
        if (!consentGrant.confirmedByUser || consentGrant.purpose != ConsentPurpose.RECOGNITION ||
            consentGrant.requestGeneration != inputRevision || consentGrant.mediaViews != images.refs().associate { it.id to it.view } ||
            consentGrant.issuedAt <= 0 || consentGrant.revokedAt != null || consentGrant.requestId != requestId ||
            consentGrant.provider != capability.provider || consentGrant.providerConfigVersion != capability.configVersion ||
            consentGrant.mediaIds != images.refs().map { it.id }.toSet()) {
            throw WardrobeException(ErrorCode.CONSENT_REQUIRED, "Cần xác nhận gửi đúng ảnh cho lần nhận diện này.")
        }
    }
}
data class RecognitionResult(
    val requestId: String,
    val itemId: String,
    val inputRevision: Long,
    val suggestedAttributes: ClothingAttributes,
    val needsReviewFields: Set<AttributeField> = emptySet(),
    val viewEvidence: Map<AttributeField, Set<ImageView>> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val source: ResultSource,
)
interface ClothingRecognition {
    val capability: RecognitionCapability
    suspend fun recognize(request: RecognitionRequest): Result<RecognitionResult>
}

/** Do not turn cancellation into an ordinary IO error. */
suspend inline fun <T> wardrobeResult(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (known: WardrobeException) {
    Result.failure(known)
} catch (invalid: IllegalArgumentException) {
    Result.failure(WardrobeException(ErrorCode.INVALID_REQUEST, invalid.message ?: "Dữ liệu không hợp lệ."))
} catch (_: Exception) {
    Result.failure(WardrobeException(ErrorCode.IO, "Không đọc/ghi được dữ liệu. Hãy thử lại."))
}

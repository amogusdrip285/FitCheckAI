package com.fitcheckai.core.model.recommendation

import com.fitcheckai.core.model.profile.ProfileProjection
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ItemRevision
import com.fitcheckai.core.model.wardrobe.requireUuid

// CT-02 — types OWNER: Track D; OutfitRecommendation implementation OWNER: Track B.

const val OUTFIT_SCHEMA_VERSION = 2
const val MAX_REQUEST_ITEMS = 50
const val MAX_OUTFITS = 5
const val MAX_FREE_TEXT = 500
const val MAX_EXPLANATION = 500
const val MAX_EXCLUDED_SIGNATURES = 5

enum class Occasion { CASUAL, SCHOOL, WORK, DATE, PARTY, SPORT, FORMAL_EVENT }
enum class OutfitSource { AI_LIVE, RULES, FAKE }
/** Mirrors Track B proxy `/v2/outfits` (proxy/src/outfits). Change both sides together. */
enum class ShortfallReason { NOT_ENOUGH_DISTINCT_COMBINATIONS, CONSTRAINTS_TOO_STRICT, INVALID_OUTFITS_REMOVED }

/** Only what the generator needs: no images, no local paths. */
data class ItemSummary(val id: String, val revision: Long, val attributes: ClothingAttributes)

data class WeatherSnapshot(
    val cityLabel: String,
    val tempC: Double,
    val condition: String,
    val validAt: Long,
    val fetchedAt: Long,
    val source: String,
    val isStale: Boolean,
)

data class OutfitRequest(
    val requestId: String,
    val wardrobeSnapshotId: String,
    val items: List<ItemSummary>,
    val profile: ProfileProjection?,
    val occasion: Occasion,
    val styleOverrides: List<String> = emptyList(),
    val freeText: String = "",
    val weather: WeatherSnapshot? = null,
    val requestedCount: Int = MAX_OUTFITS,
    val focusItemId: String? = null,
    val excludeOutfitSignatures: Set<String> = emptySet(),
    val regenerationOfRequestId: String? = null,
    val schemaVersion: Int = OUTFIT_SCHEMA_VERSION,
) {
    fun validate() {
        require(schemaVersion == OUTFIT_SCHEMA_VERSION)
        requireUuid(requestId)
        require(wardrobeSnapshotId.isNotBlank())
        require(items.size in 1..MAX_REQUEST_ITEMS) { "Tủ đồ cần 1–50 món để phối." }
        require(items.distinctBy { it.id }.size == items.size)
        require(requestedCount in 1..MAX_OUTFITS) { "Số outfit cần trong khoảng 1–5." }
        require(freeText.length <= MAX_FREE_TEXT) { "Yêu cầu tối đa 500 ký tự." }
        require(styleOverrides.size <= 5 && styleOverrides.all { it.isNotBlank() && it.length <= 32 })
        require(focusItemId == null || items.any { it.id == focusItemId }) { "Món được chọn để phối không còn trong tủ." }
        require(excludeOutfitSignatures.size <= MAX_EXCLUDED_SIGNATURES)
        regenerationOfRequestId?.let(::requireUuid)
    }
}

data class Outfit(
    val id: String,
    val requestId: String,
    val wardrobeSnapshotId: String,
    val items: List<ItemRevision>,
    val explanation: String,
    val source: OutfitSource,
    val createdAt: Long,
    val signature: String = outfitSignature(items.map { it.itemId }),
)

/** Canonical identity of an item set: sorted IDs joined by '|'. */
fun outfitSignature(itemIds: Collection<String>): String = itemIds.sorted().joinToString("|")

data class RecommendationResult(
    val requestId: String,
    val outfits: List<Outfit>,
    val shortfallReason: ShortfallReason? = null,
)

data class RecommendationCapability(
    val supportsReroll: Boolean,
    val supportsFocus: Boolean,
    val source: OutfitSource,
)

/**
 * OWNER: Track B. Fewer than one valid outfit → failure INSUFFICIENT_WARDROBE (never an empty success).
 * Must not auto-retry; cancellable.
 */
interface OutfitRecommendation {
    val capability: RecommendationCapability
    suspend fun recommend(request: OutfitRequest): Result<RecommendationResult>
}

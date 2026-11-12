package com.fitcheckai.core.ai.fallback

import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.Outfit
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ItemRevision
import com.fitcheckai.core.model.wardrobe.WardrobeException
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

// OWNER: Track B (C-002). Fallback offline tối thiểu — KHÔNG phải AI và không được hiển thị như AI.

/**
 * Ghép đúng **một** bộ cơ bản từ đồ đang có, chạy offline, không gọi mạng.
 *
 * Một quy tắc duy nhất: trong các bộ hợp lệ (1 TOP + 1 BOTTOM, hoặc 1 DRESS), chọn bộ có độ trang trọng
 * gần mức của dịp nhất; hoà thì lấy theo ID tăng dần (ổn định, gọi lại ra cùng kết quả).
 * - Tôn trọng `focusItemId` và `excludeOutfitSignatures`; không thoả được ⇒ lỗi, không âm thầm bỏ ràng buộc.
 * - Không sinh item ID mới; revision lấy từ request. `source = RULES`.
 * - Thiếu đồ ⇒ [ErrorCode.INSUFFICIENT_WARDROBE] với thông điệp nói rõ thiếu gì.
 *
 * D dùng làm phương án dự phòng tuỳ chọn (vd. khi AI timeout) và phải ghi rõ "gợi ý cơ bản, không dùng AI".
 */
class BasicOutfitFallback(private val clock: () -> Long) {
    @Inject constructor() : this(System::currentTimeMillis)

    fun suggest(request: OutfitRequest): Result<Outfit> = try {
        Result.success(build(request))
    } catch (known: WardrobeException) {
        Result.failure(known)
    } catch (invalid: IllegalArgumentException) {
        Result.failure(WardrobeException(ErrorCode.INVALID_REQUEST, invalid.message ?: "Yêu cầu không hợp lệ."))
    }

    private fun build(request: OutfitRequest): Outfit {
        // Tủ rỗng là "thiếu đồ" (thông điệp hữu ích), không phải request sai.
        if (request.items.isEmpty()) throw insufficient(emptyList(), emptyList())
        request.validate()
        val items = request.items.sortedBy { it.id }
        val focus = request.focusItemId?.let { id -> items.first { it.id == id } }
        if (focus?.attributes?.category == Category.UNKNOWN) {
            throw WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Món chưa rõ loại nên chưa phối quanh món này được.")
        }
        val tops = items.filter { it.attributes.category == Category.TOP }
        val bottoms = items.filter { it.attributes.category == Category.BOTTOM }
        val dresses = items.filter { it.attributes.category == Category.DRESS }
        if (dresses.isEmpty() && (tops.isEmpty() || bottoms.isEmpty())) throw insufficient(tops, bottoms)

        // Món focus là phụ kiện/giày/áo khoác ⇒ gắn vào bộ chính; còn lại focus phải nằm trong bộ chính.
        val addon = focus?.takeIf { it.attributes.category in ADDONS }
        val cores = tops.flatMap { top -> bottoms.map { listOf(top, it) } } + dresses.map { listOf(it) }
        val excluded = request.excludeOutfitSignatures.map { s -> outfitSignature(s.split('|').filter(String::isNotEmpty)) }.toSet()
        val target = TARGET_FORMALITY.getValue(request.occasion)

        val chosen = cores
            .filter { core -> focus == null || addon != null || focus in core }
            .map { core -> core + listOfNotNull(addon) }
            .filter { outfitSignature(it.map(ItemSummary::id)) !in excluded }
            .minWithOrNull(compareBy<List<ItemSummary>> { distance(it, target) }.thenBy { outfit -> outfit.joinToString { it.id } })
            ?: throw WardrobeException(ErrorCode.INSUFFICIENT_WARDROBE,
                "Không còn bộ cơ bản nào thoả lựa chọn hiện tại (món được chọn hoặc các bộ đã loại).")

        val signature = outfitSignature(chosen.map { it.id })
        return Outfit(
            id = UUID.nameUUIDFromBytes("rules-fallback:${request.requestId}:$signature".toByteArray()).toString(),
            requestId = request.requestId,
            wardrobeSnapshotId = request.wardrobeSnapshotId,
            items = chosen.map { ItemRevision(it.id, it.revision) },
            explanation = explain(chosen, request.occasion),
            source = OutfitSource.RULES,
            createdAt = clock(),
            signature = signature,
        )
    }

    /** Món chưa có độ trang trọng tính là lệch 1 bậc: không ưu tiên cũng không loại. */
    private fun distance(outfit: List<ItemSummary>, target: Int): Int =
        outfit.sumOf { item -> item.attributes.formality?.let { abs(it - target) } ?: 1 }

    private fun insufficient(tops: List<ItemSummary>, bottoms: List<ItemSummary>): WardrobeException {
        val missing = when {
            tops.isEmpty() && bottoms.isEmpty() -> "áo và quần/váy"
            tops.isEmpty() -> "áo"
            else -> "quần/váy"
        }
        return WardrobeException(ErrorCode.INSUFFICIENT_WARDROBE,
            "Tủ đồ còn thiếu $missing. Cần 1 áo + 1 quần/chân váy, hoặc 1 váy liền để ghép một bộ.")
    }

    private fun explain(outfit: List<ItemSummary>, occasion: Occasion): String {
        val parts = outfit.joinToString(" + ") { CATEGORY_LABEL.getValue(it.attributes.category) }
        return "Gợi ý cơ bản theo quy tắc (không dùng AI): $parts, chọn theo độ trang trọng gần với dịp " +
            "${OCCASION_LABEL.getValue(occasion)} nhất. Hãy xem lại cho hợp thời tiết và sở thích của bạn."
    }

    private companion object {
        val ADDONS = setOf(Category.OUTERWEAR, Category.SHOES, Category.ACCESSORY)
        val TARGET_FORMALITY = mapOf(
            Occasion.SPORT to 1, Occasion.CASUAL to 2, Occasion.SCHOOL to 2, Occasion.DATE to 3,
            Occasion.PARTY to 3, Occasion.WORK to 3, Occasion.FORMAL_EVENT to 5,
        )
        val OCCASION_LABEL = mapOf(
            Occasion.CASUAL to "thường ngày", Occasion.SCHOOL to "đi học", Occasion.WORK to "đi làm",
            Occasion.DATE to "hẹn hò", Occasion.PARTY to "dự tiệc", Occasion.SPORT to "thể thao",
            Occasion.FORMAL_EVENT to "sự kiện trang trọng",
        )
        val CATEGORY_LABEL = mapOf(
            Category.TOP to "áo", Category.BOTTOM to "quần/chân váy", Category.DRESS to "váy liền",
            Category.OUTERWEAR to "áo khoác", Category.SHOES to "giày", Category.ACCESSORY to "phụ kiện",
            Category.UNKNOWN to "món chưa rõ loại",
        )
    }
}

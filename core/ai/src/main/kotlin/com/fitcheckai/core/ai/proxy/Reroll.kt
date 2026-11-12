package com.fitcheckai.core.ai.proxy

import com.fitcheckai.core.model.recommendation.MAX_EXCLUDED_SIGNATURES
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.RecommendationResult
import java.util.UUID

// OWNER: Track B (C-102). Semantics "phối bộ khác" cho D (C-003): một lần bấm = một request mới, không auto retry.

/**
 * Request "phối bộ khác" sau khi người dùng đã xem [shown] (kết quả của chính request này).
 *
 * - `requestId` mới (gửi lại cùng ID bị client chặn CONFLICT), `regenerationOfRequestId` = request trước.
 * - Giữ nguyên mọi ràng buộc/ngữ cảnh: dịp, ghi chú, focus, profile, thời tiết, số bộ.
 * - Loại trừ các bộ vừa hiển thị (ưu tiên) rồi tới các bộ đã loại trước đó, chuẩn hoá signature, tối đa 5.
 * - Kết quả có thể ít bộ hơn (shortfallReason) hoặc lỗi INSUFFICIENT_WARDROBE nghĩa là "không còn lựa chọn khác"
 *   — server kiểm trước khi gọi AI nên trường hợp này không tốn tiền. Không tự gọi lại.
 */
fun OutfitRequest.rerollAfter(shown: RecommendationResult, newRequestId: String = UUID.randomUUID().toString()): OutfitRequest {
    require(shown.requestId == requestId) { "Kết quả không thuộc request này." }
    require(newRequestId != requestId) { "Phối lại cần requestId mới." }
    val excluded = (shown.outfits.map { it.signature } + excludeOutfitSignatures)
        .map(::canonicalSignature)
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_EXCLUDED_SIGNATURES)
    return copy(requestId = newRequestId, regenerationOfRequestId = requestId, excludeOutfitSignatures = LinkedHashSet(excluded))
}

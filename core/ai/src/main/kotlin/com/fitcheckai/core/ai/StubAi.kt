package com.fitcheckai.core.ai

import com.fitcheckai.core.model.AiOutfitGenerator
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingTagger
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.OutfitRequest
import com.fitcheckai.core.model.OutfitSource
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val FAKE_LATENCY_MS = 600L

/**
 * SPRINT 0 STUB — luôn trả về một áo thun trắng.
 * TODO(Track B): ClaudeClothingTagger gọi `POST /v1/tag` qua proxy ([BuildConfig.PROXY_URL]).
 */
@Singleton
class StubClothingTagger @Inject constructor() : ClothingTagger {
    override suspend fun tag(imagePath: String): Result<ClothingAttributes> {
        delay(FAKE_LATENCY_MS)
        return Result.success(
            ClothingAttributes(
                category = Category.TOP,
                subCategory = "Áo thun (stub)",
                colors = listOf("#FFFFFF"),
                formality = 1,
            ),
        )
    }
}

/**
 * SPRINT 0 STUB — ghép áo/quần/giày đầu tiên tìm thấy, giả lập AI.
 * TODO(Track B): ClaudeOutfitGenerator gọi `POST /v1/outfits` qua proxy.
 */
@Singleton
class StubAiOutfitGenerator @Inject constructor() : AiOutfitGenerator {
    override suspend fun generate(request: OutfitRequest): Result<List<Outfit>> {
        delay(FAKE_LATENCY_MS)
        val byCategory = request.items.groupBy { it.attributes.category }
        val picks = listOfNotNull(
            byCategory[Category.TOP]?.firstOrNull(),
            byCategory[Category.BOTTOM]?.firstOrNull(),
            byCategory[Category.SHOES]?.firstOrNull(),
        )
        if (picks.size < 3) return Result.failure(IllegalStateException("Stub: tủ đồ thiếu áo/quần/giày"))
        return Result.success(
            listOf(
                Outfit(
                    id = UUID.randomUUID().toString(),
                    itemIds = picks.map { it.id },
                    explanation = "(stub) Bộ cơ bản cho dịp ${request.occasion.name.lowercase()}.",
                    source = OutfitSource.AI,
                ),
            ),
        )
    }
}

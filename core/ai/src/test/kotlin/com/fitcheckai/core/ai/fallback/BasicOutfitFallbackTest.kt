package com.fitcheckai.core.ai.fallback

import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ItemRevision
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicOutfitFallbackTest {
    private val fallback = BasicOutfitFallback { 42L }
    private val requestId = fixtureId("request")

    private fun item(label: String, category: Category, formality: Int? = null, revision: Long = 1) =
        ItemSummary(fixtureId(label), revision, ClothingAttributes(category, formality = formality))

    private fun request(vararg items: ItemSummary, occasion: Occasion = Occasion.WORK, focus: String? = null,
        exclude: Set<String> = emptySet()) =
        OutfitRequest(requestId, fixtureId("snapshot"), items.toList(), null, occasion, requestedCount = 5,
            focusItemId = focus, excludeOutfitSignatures = exclude)

    private fun Result<*>.code() = (exceptionOrNull() as WardrobeException).code

    @Test fun `empty or top-only wardrobe reports what is missing`() {
        val empty = fallback.suggest(request())
        assertEquals(ErrorCode.INSUFFICIENT_WARDROBE, empty.code())
        val onlyTop = fallback.suggest(request(item("t", Category.TOP), item("s", Category.SHOES)))
        assertEquals(ErrorCode.INSUFFICIENT_WARDROBE, onlyTop.code())
        assertTrue(onlyTop.exceptionOrNull()!!.message!!.contains("quần"))
        val onlyBottom = fallback.suggest(request(item("b", Category.BOTTOM)))
        assertTrue(onlyBottom.exceptionOrNull()!!.message!!.contains("thiếu áo"))
    }

    @Test fun `top plus bottom gives exactly one rules outfit from existing items`() {
        val top = item("t", Category.TOP, revision = 3)
        val bottom = item("b", Category.BOTTOM, revision = 7)
        val outfit = fallback.suggest(request(top, bottom)).getOrThrow()
        assertEquals(setOf(ItemRevision(top.id, 3), ItemRevision(bottom.id, 7)), outfit.items.toSet())
        assertEquals(OutfitSource.RULES, outfit.source)
        assertEquals(outfitSignature(listOf(top.id, bottom.id)), outfit.signature)
        assertEquals(requestId, outfit.requestId)
        assertEquals(42L, outfit.createdAt)
        assertTrue(outfit.explanation.contains("không dùng AI"))
        assertTrue(outfit.explanation.length in 1..500)
    }

    @Test fun `dress alone is a valid outfit`() {
        val dress = item("d", Category.DRESS)
        assertEquals(listOf(dress.id), fallback.suggest(request(dress, item("s", Category.SHOES))).getOrThrow().items.map { it.itemId })
    }

    @Test fun `formality closest to the occasion wins and result is stable`() {
        val tee = item("tee", Category.TOP, formality = 1)
        val shirt = item("shirt", Category.TOP, formality = 4)
        val jeans = item("jeans", Category.BOTTOM, formality = 1)
        val trousers = item("trousers", Category.BOTTOM, formality = 4)
        val all = arrayOf(tee, shirt, jeans, trousers)
        val work = fallback.suggest(request(*all, occasion = Occasion.FORMAL_EVENT)).getOrThrow()
        assertEquals(setOf(shirt.id, trousers.id), work.items.map { it.itemId }.toSet())
        val sport = fallback.suggest(request(*all, occasion = Occasion.SPORT)).getOrThrow()
        assertEquals(setOf(tee.id, jeans.id), sport.items.map { it.itemId }.toSet())
        // Gọi lại (kể cả đảo thứ tự input) ra cùng bộ và cùng outfit ID.
        assertEquals(sport, fallback.suggest(request(*all.reversedArray(), occasion = Occasion.SPORT)).getOrThrow())
    }

    @Test fun `focus item is always included, addon focus is attached`() {
        val t1 = item("t1", Category.TOP); val t2 = item("t2", Category.TOP); val b = item("b", Category.BOTTOM)
        val shoes = item("shoes", Category.SHOES)
        val focused = fallback.suggest(request(t1, t2, b, focus = t2.id)).getOrThrow()
        assertTrue(t2.id in focused.items.map { it.itemId })
        val withShoes = fallback.suggest(request(t1, b, shoes, focus = shoes.id)).getOrThrow()
        assertEquals(setOf(t1.id, b.id, shoes.id), withShoes.items.map { it.itemId }.toSet())
        val unknown = item("u", Category.UNKNOWN)
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, fallback.suggest(request(t1, b, unknown, focus = unknown.id)).code())
    }

    @Test fun `exclusions are respected and never silently dropped`() {
        val t1 = item("t1", Category.TOP); val t2 = item("t2", Category.TOP); val b = item("b", Category.BOTTOM)
        val first = fallback.suggest(request(t1, t2, b)).getOrThrow()
        // Loại bộ vừa nhận (signature đảo thứ tự vẫn khớp) ⇒ ra bộ còn lại.
        val reversed = first.items.map { it.itemId }.sortedDescending().joinToString("|")
        val second = fallback.suggest(request(t1, t2, b, exclude = setOf(reversed))).getOrThrow()
        assertFalse(second.signature == first.signature)
        // Loại hết ⇒ báo không còn lựa chọn, không trả lại bộ đã loại.
        val none = fallback.suggest(request(t1, t2, b, exclude = setOf(first.signature, second.signature)))
        assertEquals(ErrorCode.INSUFFICIENT_WARDROBE, none.code())
        // Focus không ghép được với gì ⇒ lỗi, không bỏ focus.
        val lonely = item("lonely", Category.TOP)
        assertEquals(ErrorCode.INSUFFICIENT_WARDROBE,
            fallback.suggest(request(lonely, b, focus = lonely.id, exclude = setOf(outfitSignature(listOf(lonely.id, b.id))))).code())
    }

    @Test fun `invalid request is a typed failure`() {
        val bad = request(item("t", Category.TOP), item("b", Category.BOTTOM)).copy(requestedCount = 0)
        assertEquals(ErrorCode.INVALID_REQUEST, fallback.suggest(bad).code())
    }
}

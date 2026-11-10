package com.fitcheckai.core.database.seed

import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Season
import java.time.Instant

/**
 * Tủ đồ mẫu cho phát triển & demo (imagePath = `seed://…`, UI hiển thị ô màu).
 * Track A có thể thay bằng ảnh thật trong assets.
 */
object SeedData {
    private val CREATED = Instant.parse("2026-11-01T08:00:00Z")
    private val ALL_SEASONS = Season.entries.toSet()
    private val WARM = setOf(Season.SPRING, Season.SUMMER)
    private val COOL = setOf(Season.AUTUMN, Season.WINTER)

    val items: List<ClothingItem> = listOf(
        seed("t1", Category.TOP, "Áo thun trắng", "#F5F5F2", 1, ALL_SEASONS, "basic"),
        seed("t2", Category.TOP, "Áo thun đen", "#1C1C1C", 1, ALL_SEASONS, "basic", "streetwear"),
        seed("t3", Category.TOP, "Sơ mi xanh nhạt", "#B9CDE5", 3, ALL_SEASONS, "office"),
        seed("t4", Category.TOP, "Sơ mi trắng", "#FFFFFF", 4, ALL_SEASONS, "office", "minimal"),
        seed("t5", Category.TOP, "Áo polo be", "#D8C3A5", 2, WARM, "smart-casual"),
        seed("t6", Category.TOP, "Áo len cổ lọ xám", "#8A8A8A", 3, COOL, "minimal"),
        seed("b1", Category.BOTTOM, "Quần jean xanh", "#3B5B8C", 2, ALL_SEASONS, "basic"),
        seed("b2", Category.BOTTOM, "Quần tây đen", "#222222", 4, ALL_SEASONS, "office"),
        seed("b3", Category.BOTTOM, "Quần kaki be", "#C8B48A", 3, ALL_SEASONS, "smart-casual"),
        seed("b4", Category.BOTTOM, "Quần short jean", "#6E8DB8", 1, WARM, "streetwear"),
        seed("d1", Category.DRESS, "Đầm suông đen", "#151515", 4, ALL_SEASONS, "minimal"),
        seed("o1", Category.OUTERWEAR, "Áo khoác jean", "#4A6A9A", 2, COOL + Season.SPRING, "streetwear"),
        seed("o2", Category.OUTERWEAR, "Blazer xám", "#5E5E62", 4, COOL + Season.SPRING, "office"),
        seed("o3", Category.OUTERWEAR, "Áo khoác gió đỏ", "#B23A30", 1, ALL_SEASONS, "sporty"),
        seed("s1", Category.SHOES, "Sneaker trắng", "#F0F0F0", 2, ALL_SEASONS, "basic"),
        seed("s2", Category.SHOES, "Giày da nâu", "#6B4226", 4, ALL_SEASONS, "office"),
        seed("s3", Category.SHOES, "Giày chạy bộ", "#2F4F6F", 1, ALL_SEASONS, "sporty"),
        seed("a1", Category.ACCESSORY, "Thắt lưng da đen", "#1A1A1A", 3, ALL_SEASONS, "office"),
    )

    private fun seed(
        id: String,
        category: Category,
        name: String,
        color: String,
        formality: Int,
        seasons: Set<Season>,
        vararg tags: String,
    ) = ClothingItem(
        id = "seed-$id",
        imagePath = "seed://$id",
        attributes = ClothingAttributes(
            category = category,
            subCategory = name,
            colors = listOf(color),
            formality = formality,
            seasons = seasons,
            styleTags = tags.toSet(),
        ),
        createdAt = CREATED,
    )
}

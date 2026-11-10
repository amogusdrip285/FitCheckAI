package com.fitcheckai.core.model

import java.time.Instant

enum class Category { TOP, BOTTOM, DRESS, OUTERWEAR, SHOES, ACCESSORY }

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/**
 * Thuộc tính một món đồ — do AI (Track B) điền, người dùng sửa được (Track A).
 *
 * @property colors mã màu hex dạng `#RRGGBB`, màu chủ đạo đứng đầu.
 * @property formality 1 (rất thoải mái) .. 5 (rất trang trọng).
 */
data class ClothingAttributes(
    val category: Category,
    val subCategory: String,
    val colors: List<String>,
    val pattern: String = "solid",
    val material: String? = null,
    val formality: Int = 2,
    val seasons: Set<Season> = Season.entries.toSet(),
    val styleTags: Set<String> = emptySet(),
) {
    init {
        require(formality in FORMALITY_RANGE) { "formality must be in $FORMALITY_RANGE, was $formality" }
        require(colors.all { HEX_COLOR.matches(it) }) { "colors must be #RRGGBB, was $colors" }
    }

    companion object {
        val FORMALITY_RANGE = 1..5
        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}

/**
 * @property imagePath đường dẫn tuyệt đối tới ảnh đã tách nền trong internal storage,
 * hoặc `seed://<tên>` cho dữ liệu mẫu (UI hiển thị ô màu thay ảnh).
 */
data class ClothingItem(
    val id: String,
    val imagePath: String,
    val attributes: ClothingAttributes,
    val createdAt: Instant,
    val wearCount: Int = 0,
    val lastWornAt: Instant? = null,
)

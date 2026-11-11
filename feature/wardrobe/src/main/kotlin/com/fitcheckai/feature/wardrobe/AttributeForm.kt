package com.fitcheckai.feature.wardrobe

import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.Season
import java.util.Locale

data class AttributeForm(
    val category: Category? = null,
    val name: String = "",
    val colors: String = "",
    val pattern: String = "solid",
    val material: String = "",
    val formality: Int = 2,
    val seasons: Set<Season> = Season.entries.toSet(),
    val styles: String = "",
) {
    fun attributes(): Result<ClothingAttributes> = runCatching {
        require(category != null) { "Chọn loại đồ." }
        require(name.isNotBlank()) { "Nhập tên món đồ." }
        require(name.trim().length <= 100) { "Tên món đồ tối đa 100 ký tự." }
        val parsedColors = split(colors).map { it.uppercase(Locale.ROOT) }.distinct()
        require(parsedColors.isNotEmpty() && parsedColors.all { Regex("#[0-9A-F]{6}").matches(it) }) {
            "Chọn màu hoặc nhập mã #RRGGBB, ngăn cách bằng dấu phẩy."
        }
        require(seasons.isNotEmpty()) { "Chọn ít nhất một mùa." }
        ClothingAttributes(category, name.trim(), parsedColors, pattern.trim().ifBlank { "solid" },
            material.trim().ifBlank { null }, formality, seasons, split(styles).toSet())
    }

    fun savedValues(): ArrayList<String> = arrayListOf(category?.name.orEmpty(), name, colors, pattern,
        material, formality.toString(), seasons.joinToString(",") { it.name }, styles)

    companion object {
        fun from(attributes: ClothingAttributes) = AttributeForm(attributes.category, attributes.subCategory,
            attributes.colors.joinToString(", "), attributes.pattern, attributes.material.orEmpty(),
            attributes.formality, attributes.seasons, attributes.styleTags.joinToString(", "))

        fun restore(values: List<String>?): AttributeForm = if (values?.size != 8) AttributeForm() else AttributeForm(
            Category.entries.find { it.name == values[0] }, values[1], values[2], values[3], values[4],
            values[5].toIntOrNull()?.coerceIn(1, 5) ?: 2,
            Season.entries.filter { it.name in split(values[6]) }.toSet(), values[7],
        )

        private fun split(value: String) = value.split(',').map(String::trim).filter(String::isNotEmpty)
    }
}

internal fun correctedFields(original: ClothingAttributes, final: ClothingAttributes): List<String> = buildList {
    if (original.category != final.category) add("category")
    if (original.subCategory != final.subCategory) add("subCategory")
    if (original.colors.map { it.uppercase(Locale.ROOT) } != final.colors.map { it.uppercase(Locale.ROOT) }) add("colors")
    if (original.pattern != final.pattern) add("pattern")
    if (original.material != final.material) add("material")
    if (original.formality != final.formality) add("formality")
    if (original.seasons != final.seasons) add("seasons")
    if (original.styleTags != final.styleTags) add("styleTags")
}

internal val Category.label: String get() = when (this) {
    Category.TOP -> "Áo"
    Category.BOTTOM -> "Quần / chân váy"
    Category.DRESS -> "Đầm / váy liền"
    Category.OUTERWEAR -> "Áo khoác"
    Category.SHOES -> "Giày"
    Category.ACCESSORY -> "Phụ kiện"
}

internal val Season.label: String get() = when (this) {
    Season.SPRING -> "Xuân"
    Season.SUMMER -> "Hè"
    Season.AUTUMN -> "Thu"
    Season.WINTER -> "Đông"
}

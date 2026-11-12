package com.fitcheckai.feature.onboarding.profile

import com.fitcheckai.core.model.profile.HEIGHT_CM_RANGE
import com.fitcheckai.core.model.profile.MAX_STYLE_PREFERENCES
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.profile.WEIGHT_KG_RANGE
import com.fitcheckai.core.model.wardrobe.Category

val STYLE_OPTIONS = listOf("Minimal", "Streetwear", "Công sở", "Năng động", "Vintage", "Nữ tính", "Smart casual")

val CATEGORY_OPTIONS: List<Pair<Category, String>> = listOf(
    Category.TOP to "Áo", Category.BOTTOM to "Quần / chân váy", Category.DRESS to "Đầm",
    Category.OUTERWEAR to "Áo khoác", Category.SHOES to "Giày", Category.ACCESSORY to "Phụ kiện",
)

enum class FormField { HEIGHT, WEIGHT }

/** Raw, user-editable values. Empty text means "not provided" and maps to null — never 0. */
data class ProfileForm(
    val heightText: String = "",
    val weightText: String = "",
    val styles: List<String> = emptyList(),
    val categories: Set<Category> = emptySet(),
) {
    fun toggleStyle(style: String): ProfileForm = when {
        style in styles -> copy(styles = styles - style)
        styles.size >= MAX_STYLE_PREFERENCES -> this
        else -> copy(styles = styles + style)
    }

    fun toggleCategory(category: Category): ProfileForm =
        copy(categories = if (category in categories) categories - category else categories + category)

    fun errors(): Map<FormField, String> = buildMap {
        if (parseNumber(heightText, HEIGHT_CM_RANGE) == Parsed.Invalid) put(FormField.HEIGHT, "Nhập chiều cao 50–250 cm, hoặc để trống.")
        if (parseNumber(weightText, WEIGHT_KG_RANGE) == Parsed.Invalid) put(FormField.WEIGHT, "Nhập cân nặng 20–300 kg, hoặc để trống.")
    }

    /** Applies the form onto [base]; call only when [errors] is empty. Body images are untouched. */
    fun applyTo(base: UserProfile): UserProfile = base.copy(
        heightCm = (parseNumber(heightText, HEIGHT_CM_RANGE) as? Parsed.Value)?.value,
        weightKg = (parseNumber(weightText, WEIGHT_KG_RANGE) as? Parsed.Value)?.value,
        stylePreferences = styles,
        preferredCategories = categories,
    )

    companion object {
        fun from(profile: UserProfile?): ProfileForm = if (profile == null) ProfileForm() else ProfileForm(
            heightText = profile.heightCm?.let(::formatNumber).orEmpty(),
            weightText = profile.weightKg?.let(::formatNumber).orEmpty(),
            styles = profile.stylePreferences,
            categories = profile.preferredCategories,
        )
    }
}

internal sealed interface Parsed {
    data object Empty : Parsed
    data object Invalid : Parsed
    data class Value(val value: Double) : Parsed
}

internal fun parseNumber(text: String, range: ClosedFloatingPointRange<Double>): Parsed {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Parsed.Empty
    val value = trimmed.replace(',', '.').toDoubleOrNull() ?: return Parsed.Invalid
    return if (value.isFinite() && value in range) Parsed.Value(value) else Parsed.Invalid
}

internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

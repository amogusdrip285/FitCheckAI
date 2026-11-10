package com.fitcheckai.core.model.nav

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Tất cả route của app. Feature dùng hàm builder, KHÔNG tự ghép chuỗi. */
object Routes {
    const val ONBOARDING = "onboarding"

    const val ARG_FOCUS_ITEM_ID = "focusItemId"
    const val TODAY_PATTERN = "today?$ARG_FOCUS_ITEM_ID={$ARG_FOCUS_ITEM_ID}"
    const val TODAY = "today"
    fun today(focusItemId: String? = null): String =
        if (focusItemId == null) TODAY else "$TODAY?$ARG_FOCUS_ITEM_ID=${encode(focusItemId)}"

    const val WARDROBE = "wardrobe"
    const val WARDROBE_ADD = "wardrobe/add"
    const val ARG_ITEM_ID = "itemId"
    const val WARDROBE_ITEM_PATTERN = "wardrobe/item/{$ARG_ITEM_ID}"
    fun wardrobeItem(itemId: String): String = "wardrobe/item/${encode(itemId)}"

    const val INSIGHTS = "insights"

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

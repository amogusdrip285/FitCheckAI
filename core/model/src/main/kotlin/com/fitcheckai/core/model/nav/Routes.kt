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

    /** Parked in v2 (TEAM_WORKFLOW §3); route kept so the old module still compiles. */
    const val INSIGHTS = "insights"

    const val PROFILE = "profile"

    /** CT-03: TryOn(outfitId + snapshot reference). Screen owned by Track C, route by D. */
    const val ARG_OUTFIT_ID = "outfitId"
    const val ARG_SNAPSHOT_ID = "snapshotId"
    const val TRYON_PATTERN = "tryon/{$ARG_OUTFIT_ID}?$ARG_SNAPSHOT_ID={$ARG_SNAPSHOT_ID}"
    fun tryOn(outfitId: String, snapshotId: String): String =
        "tryon/${encode(outfitId)}?$ARG_SNAPSHOT_ID=${encode(snapshotId)}"

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

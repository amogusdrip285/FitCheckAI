package com.fitcheckai.feature.insights

import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.WearLogEntry
import java.time.Duration
import java.time.Instant

val FORGOTTEN_AFTER: Duration = Duration.ofDays(30)

data class WardrobeInsights(
    val totalItems: Int,
    val wornInWindow: Int,
    val totalWears: Int,
    val forgotten: List<ClothingItem>,
) {
    /** % tủ đồ được mặc trong cửa sổ [FORGOTTEN_AFTER] — metric chính cho tiêu chí 2. */
    val utilizationPercent: Int
        get() = if (totalItems == 0) 0 else wornInWindow * 100 / totalItems
}

/** Hàm thuần — dễ test. Món "bị bỏ quên" = chưa mặc lần nào hoặc lâu hơn [FORGOTTEN_AFTER]. */
fun computeInsights(items: List<ClothingItem>, logs: List<WearLogEntry>, now: Instant): WardrobeInsights {
    val cutoff = now.minus(FORGOTTEN_AFTER)
    val wornRecently = items.filter { it.lastWornAt?.isAfter(cutoff) == true }
    val forgotten = (items - wornRecently.toSet())
        .sortedBy { it.lastWornAt ?: Instant.EPOCH }
    return WardrobeInsights(
        totalItems = items.size,
        wornInWindow = wornRecently.size,
        totalWears = logs.size,
        forgotten = forgotten,
    )
}

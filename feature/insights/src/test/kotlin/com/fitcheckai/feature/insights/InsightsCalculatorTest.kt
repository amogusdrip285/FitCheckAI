package com.fitcheckai.feature.insights

import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class InsightsCalculatorTest {
    private val now = Instant.parse("2026-11-10T00:00:00Z")

    private fun item(id: String, lastWornDaysAgo: Long?) = ClothingItem(
        id = id,
        imagePath = "seed://$id",
        attributes = ClothingAttributes(Category.TOP, id, listOf("#FFFFFF")),
        createdAt = now.minus(Duration.ofDays(100)),
        lastWornAt = lastWornDaysAgo?.let { now.minus(Duration.ofDays(it)) },
    )

    @Test
    fun `utilization counts items worn within 30 days`() {
        val items = listOf(item("a", 1), item("b", 10), item("c", 45), item("d", null))

        val result = computeInsights(items, emptyList(), now)

        assertEquals(50, result.utilizationPercent)
    }

    @Test
    fun `forgotten lists never worn first then oldest`() {
        val items = listOf(item("old", 45), item("never", null), item("recent", 2))

        val result = computeInsights(items, emptyList(), now)

        assertEquals(listOf("never", "old"), result.forgotten.map { it.id })
    }

    @Test
    fun `empty wardrobe has zero utilization`() {
        assertEquals(0, computeInsights(emptyList(), emptyList(), now).utilizationPercent)
    }
}

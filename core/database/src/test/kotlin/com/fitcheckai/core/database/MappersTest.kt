package com.fitcheckai.core.database

import com.fitcheckai.core.database.seed.SeedData
import com.fitcheckai.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MappersTest {

    @Test
    fun `entity round trip preserves every seed item`() {
        SeedData.items.forEach { item ->
            assertEquals(item, item.toEntity().toModel())
        }
    }

    @Test
    fun `round trip keeps tags containing commas`() {
        val item = SeedData.items.first().let {
            it.copy(
                attributes = it.attributes.copy(styleTags = setOf("a, b", "c")),
                lastWornAt = Instant.parse("2026-11-05T10:00:00Z"),
            )
        }
        assertEquals(item, item.toEntity().toModel())
    }

    @Test
    fun `list codec handles empty list`() {
        assertEquals(emptyList<String>(), ListCodec.decode(ListCodec.encode(emptyList())))
    }

    @Test
    fun `seed wardrobe can build a basic outfit`() {
        val categories = SeedData.items.map { it.attributes.category }.toSet()
        assertTrue(categories.containsAll(listOf(Category.TOP, Category.BOTTOM, Category.SHOES)))
        assertEquals(SeedData.items.size, SeedData.items.map { it.id }.toSet().size)
    }
}

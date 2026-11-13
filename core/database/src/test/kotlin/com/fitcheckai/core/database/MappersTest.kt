package com.fitcheckai.core.database

import com.fitcheckai.core.database.seed.SeedData
import com.fitcheckai.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MappersTest {

    @Test fun `codec handles arbitrary user text and legacy data`() {
        val values = listOf("", "a\u001Fb", "quotes\"", "dấu phẩy,", "\\", "雪")
        assertEquals(values, ListCodec.decode(ListCodec.encode(values)))
        assertEquals(listOf("a", "b"), ListCodec.decode("a\u001Fb"))
        assertEquals(emptyList<String>(), ListCodec.decode(""))
    }

    @Test fun `room converters preserve nullable lists sets and instants`() {
        val codec = DatabaseConverters()
        val time = Instant.parse("2026-11-11T02:03:04.567Z")
        assertEquals(time, codec.decodeInstant(codec.encodeInstant(time)))
        assertEquals(listOf("x", "", "y"), codec.decodeList(codec.encodeList(listOf("x", "", "y"))))
        assertEquals(setOf("x", "y"), codec.decodeSet(codec.encodeSet(setOf("x", "y"))))
        org.junit.Assert.assertNull(codec.encodeInstant(null))
        org.junit.Assert.assertNull(codec.decodeInstant(null))
        org.junit.Assert.assertNull(codec.encodeList(null))
        org.junit.Assert.assertNull(codec.decodeList(null))
        org.junit.Assert.assertNull(codec.encodeSet(null))
        org.junit.Assert.assertNull(codec.decodeSet(null))
    }

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

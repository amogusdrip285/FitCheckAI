package com.fitcheckai.core.model

import com.fitcheckai.core.model.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContractsTest {

    @Test
    fun `today route without focus item is plain`() {
        assertEquals("today", Routes.today())
    }

    @Test
    fun `today route encodes focus item id`() {
        assertEquals("today?focusItemId=a+b%2Fc", Routes.today("a b/c"))
    }

    @Test
    fun `wardrobe item route encodes id`() {
        assertEquals("wardrobe/item/x%3F1", Routes.wardrobeItem("x?1"))
    }

    @Test
    fun `attributes reject formality out of range`() {
        assertThrows(IllegalArgumentException::class.java) {
            ClothingAttributes(Category.TOP, "t-shirt", listOf("#FFFFFF"), formality = 6)
        }
    }

    @Test
    fun `attributes reject non hex colors`() {
        assertThrows(IllegalArgumentException::class.java) {
            ClothingAttributes(Category.TOP, "t-shirt", listOf("white"))
        }
    }

    @Test
    fun `attributes accept valid values`() {
        val attrs = ClothingAttributes(Category.SHOES, "sneaker", listOf("#112233"), formality = 1)
        assertEquals(Category.SHOES, attrs.category)
    }
}

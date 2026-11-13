package com.fitcheckai.feature.wardrobe

import com.fitcheckai.core.model.Category
import org.junit.Assert.*
import org.junit.Test

class AttributeFormTest {
    @Test fun `all user editable fields round trip through saved state`() {
        val form = AttributeForm.from(ATTRS).copy(material = "Cotton", pattern = "striped", styles = "office, minimal")
        assertEquals(form, AttributeForm.restore(form.savedValues()))
        assertEquals(setOf("office", "minimal"), form.attributes().getOrThrow().styleTags)
        assertEquals(AttributeForm(), AttributeForm.restore(listOf("bad")))
    }

    @Test fun `manual form requires a name category valid colors and season`() {
        assertTrue(AttributeForm().attributes().isFailure)
        val valid = AttributeForm.from(ATTRS)
        assertTrue(valid.copy(name = " ").attributes().isFailure)
        assertTrue(valid.copy(name = "a".repeat(101)).attributes().isFailure)
        assertTrue(valid.copy(colors = "red").attributes().isFailure)
        assertTrue(valid.copy(colors = "").attributes().isFailure)
        assertTrue(valid.copy(seasons = emptySet()).attributes().isFailure)
        val normalized = valid.copy(colors = "#aAbBcC, #AABBCC", name = "  Áo  ").attributes().getOrThrow()
        assertEquals(listOf("#AABBCC"), normalized.colors)
        assertEquals("Áo", normalized.subCategory)
    }

    @Test fun `correction metrics describe only changed fields`() {
        assertTrue(correctedFields(ATTRS, ATTRS.copy(colors = listOf("#ffffff"))).isEmpty())
        val changed = ATTRS.copy(category = Category.SHOES, pattern = "striped", formality = 5,
            colors = listOf("#000000"), seasons = emptySet(), styleTags = setOf("minimal"))
        assertEquals(listOf("category", "colors", "pattern", "formality", "seasons", "styleTags"), correctedFields(ATTRS, changed))
    }
}

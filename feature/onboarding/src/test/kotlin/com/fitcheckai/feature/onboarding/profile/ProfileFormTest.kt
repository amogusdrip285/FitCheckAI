package com.fitcheckai.feature.onboarding.profile

import com.fitcheckai.core.model.profile.fakes.fullProfile
import com.fitcheckai.core.model.profile.fakes.minimalProfile
import com.fitcheckai.core.model.wardrobe.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormTest {
    @Test
    fun `styles are capped at five and toggle off`() {
        var form = ProfileForm()
        STYLE_OPTIONS.forEach { form = form.toggleStyle(it) }
        assertEquals(5, form.styles.size)
        assertEquals(4, form.toggleStyle(form.styles.first()).styles.size)
    }

    @Test
    fun `number parsing accepts comma and rejects out of range`() {
        assertEquals(Parsed.Empty, parseNumber("  ", 50.0..250.0))
        assertEquals(Parsed.Value(170.5), parseNumber("170,5", 50.0..250.0))
        assertEquals(Parsed.Invalid, parseNumber("49", 50.0..250.0))
        assertEquals(Parsed.Invalid, parseNumber("1e9", 50.0..250.0))
        assertEquals(Parsed.Invalid, parseNumber("NaN", 50.0..250.0))
    }

    @Test
    fun `round trips through a profile without touching body images`() {
        val full = fullProfile()
        val form = ProfileForm.from(full)
        assertTrue(form.errors().isEmpty())
        assertEquals(full, form.applyTo(full))
        val cleared = form.copy(weightText = "").toggleCategory(Category.TOP).applyTo(full)
        assertNull(cleared.weightKg)
        assertEquals(setOf(Category.DRESS), cleared.preferredCategories)
        assertEquals(full.bodyImages, cleared.bodyImages)
        assertEquals(ProfileForm(), ProfileForm.from(null))
        assertEquals("", ProfileForm.from(minimalProfile()).heightText)
    }
}

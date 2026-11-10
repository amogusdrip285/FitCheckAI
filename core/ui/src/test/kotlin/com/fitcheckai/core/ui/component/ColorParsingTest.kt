package com.fitcheckai.core.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorParsingTest {

    @Test
    fun `parses valid hex color`() {
        assertEquals(Color(0xFFB85A3C), parseHexColor("#B85A3C"))
    }

    @Test
    fun `returns null for malformed hex`() {
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor("#FFF"))
    }
}

package com.fitcheckai.feature.wardrobe.v2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitcheckai.feature.wardrobe.WardrobeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WardrobeV2InputTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `typing stays ordered even when repository derived state has not emitted`() {
        val updates = mutableListOf<Filter>()
        compose.setContent { WardrobeTheme {
            WardrobeListContent(ListState(total = 21, loading = false), updates::add, {}, {}, {})
        } }
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("D"); input.performTextInput("D"); input.performTextInput("C")
        input.assertTextContains("DDC")
        assertEquals("DDC", updates.last().query)
        compose.onNodeWithContentDescription("Xóa tìm kiếm").performClick()
        assertEquals("", updates.last().query)
    }
}

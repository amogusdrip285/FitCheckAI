package com.fitcheckai.feature.wardrobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitcheckai.core.model.wardrobe.Category
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OutfitVisualTest {
    @get:Rule val compose = createComposeRule()
    private fun item() = ItemThumbnailData("dress-id", "Đầm xanh", null, Category.DRESS)
    @Test fun `five outfits select the exact fifth ID with missing images safely`() {
        var selected: String? = null
        val outfits = (1..5).map { OutfitVisualData("outfit-$it", "Bộ $it", listOf(item()), OutfitSource.FAKE) }
        compose.setContent { WardrobeTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { OutfitVisualList(outfits, { selected = it }, {}) }
        } }
        compose.onAllNodesWithText("Chọn bộ này")[4].performScrollTo().performClick()
        assertEquals("outfit-5", selected)
        compose.onAllNodesWithText("Đầm xanh").assertCountEquals(5)
    }
    @Test fun `stale and deleted outfit cannot be selected but can request refresh`() {
        var selected = false; var refreshed = false
        compose.setContent { WardrobeTheme {
            OutfitVisual(OutfitVisualData("stale", "Bộ cũ", listOf(item().copy(stale = true)), OutfitSource.RULES),
                { selected = true }, {}, onRefresh = { refreshed = true })
        } }
        compose.onNodeWithText("Chọn bộ này").assertIsNotEnabled()
        compose.onNodeWithText("Làm mới gợi ý").performClick()
        assertFalse(selected); assertTrue(refreshed)
    }
}

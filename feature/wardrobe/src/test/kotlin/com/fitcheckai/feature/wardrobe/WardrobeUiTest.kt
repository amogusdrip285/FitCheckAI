package com.fitcheckai.feature.wardrobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WardrobeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `empty wardrobe guides user to add a first item`() {
        var opened = false
        compose.setContent { MaterialTheme { WardrobeContent(WardrobeState(), {}, { opened = true }, {}) } }
        compose.onNodeWithText("Tủ đồ trống").assertIsDisplayed()
        compose.onNodeWithText("Thêm món đầu tiên").performClick()
        assertTrue(opened)
    }

    @Test fun `grid opens detail and filter callback updates category`() {
        var opened: String? = null
        var filter = WardrobeFilter()
        val item = ITEM.copy(imagePath = "seed://test")
        compose.setContent { MaterialTheme { WardrobeContent(WardrobeState(listOf(item), 1), { filter = it }, {}, { opened = it }) } }
        compose.onNodeWithText("Áo", useUnmergedTree = true).performClick()
        assertEquals(com.fitcheckai.core.model.Category.TOP, filter.category)
        compose.onAllNodesWithText("Áo thun").onFirst().performClick()
        assertEquals(ITEM.id, opened)
    }

    @Test fun `attribute editor supports manual name and category selection`() {
        var form by mutableStateOf(AttributeForm())
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { AttributeEditor(form, { form = it }) }
        } }
        compose.onNodeWithText("Tên món đồ *").performTextInput("Áo linen")
        compose.onNodeWithText("Áo").performClick()
        assertEquals("Áo linen", form.name)
        assertEquals(com.fitcheckai.core.model.Category.TOP, form.category)
    }

}

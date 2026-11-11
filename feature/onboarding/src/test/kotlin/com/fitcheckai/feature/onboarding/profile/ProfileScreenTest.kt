package com.fitcheckai.feature.onboarding.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.fitcheckai.core.model.wardrobe.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `onboarding offers skip and hides photo section`() {
        var skipped = false
        compose.setContent {
            ProfileScreen(ProfileMode.ONBOARDING, ProfileUiState(load = ProfileLoad.Ready), ProfileActions(onSkip = { skipped = true }))
        }
        compose.onNodeWithTag("skip").performScrollTo().performClick()
        assertTrue(skipped)
        compose.onNodeWithTag("photo-FRONT").assertDoesNotExist()
    }

    @Test
    fun `field error is shown`() {
        compose.setContent {
            ProfileScreen(ProfileMode.EDIT, ProfileUiState(load = ProfileLoad.Ready,
                fieldErrors = mapOf(FormField.HEIGHT to "Nhập chiều cao 50–250 cm, hoặc để trống.")), ProfileActions())
        }
        compose.onNodeWithText("Nhập chiều cao 50–250 cm, hoặc để trống.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `edit mode shows missing back as missing and reset needs confirmation`() {
        var resets = 0
        compose.setContent {
            ProfileScreen(
                ProfileMode.EDIT,
                ProfileUiState(load = ProfileLoad.Ready, hasProfile = true,
                    photos = mapOf(ImageView.FRONT to BodyPhotoUi("m1", null))),
                ProfileActions(onReset = { resets++ }),
            )
        }
        compose.onNodeWithTag("photo-BACK").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chưa có ảnh").assertIsDisplayed()
        compose.onNodeWithText("Xóa hồ sơ và ảnh cá nhân").performScrollTo().performClick()
        assertEquals(0, resets)
        compose.onNodeWithText("Xóa").performClick()
        assertEquals(1, resets)
    }

    @Test
    fun `unreadable profile offers reset`() {
        compose.setContent {
            ProfileScreen(ProfileMode.EDIT, ProfileUiState(load = ProfileLoad.Failed("x")), ProfileActions())
        }
        compose.onNodeWithText("Đặt lại hồ sơ").assertIsDisplayed()
    }
}

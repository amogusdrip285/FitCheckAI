package com.fitcheckai.feature.tryon

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.ui.theme.FitCheckTheme
import com.fitcheckai.feature.tryon.client.LocalDiskTryOnMediaStore
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.demo.DemoExecutionMode
import com.fitcheckai.feature.tryon.demo.TryOnDemoTags
import com.fitcheckai.feature.tryon.demo.TryOnDemoWorkbench
import com.fitcheckai.feature.tryon.fake.FakeTryOnFixtures
import com.fitcheckai.feature.tryon.fake.FakeTryOnProfilePort
import com.fitcheckai.feature.tryon.fake.FakeTryOnScenario
import com.fitcheckai.feature.tryon.fake.FakeVirtualTryOnService
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import com.fitcheckai.feature.tryon.ui.TryOnRoute
import com.fitcheckai.feature.tryon.ui.TryOnScreen
import com.fitcheckai.feature.tryon.ui.TryOnTestTags
import com.fitcheckai.feature.tryon.ui.TryOnUiState
import com.fitcheckai.feature.tryon.ui.TryOnViewMode
import com.fitcheckai.feature.tryon.ui.TryOnViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class TryOnScreenVisualQaTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val args = InstrumentationRegistry.getArguments()
        val additionalDir = args.getString("additionalTestOutputDir")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = if (!additionalDir.isNullOrBlank()) {
            File(additionalDir).apply { mkdirs() }
        } else {
            File(context.externalCacheDir ?: context.cacheDir, "visual_qa").apply { mkdirs() }
        }
        val outFile = File(outDir, "$name.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
    }

    @Test
    fun interactiveConsentGateAndEndToEndPartialFlow() {
        val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
        val privacy = TryOnPrivacyCoordinator(mediaStore)
        val fakeService = FakeVirtualTryOnService(
            initialScenario = FakeTryOnScenario.PARTIAL_BACK_FAILED,
            mediaStore = mediaStore,
            privacyCoordinator = privacy,
            stepDelayMs = 20L,
        )
        val viewModel = TryOnViewModel(
            tryOnService = fakeService,
            profilePort = FakeTryOnProfilePort(FakeTryOnFixtures.personDualSnapshot),
            privacyCoordinator = privacy,
            initialOutfit = FakeTryOnFixtures.editorialWorkOutfit,
            initialGarmentImages = FakeTryOnFixtures.defaultGarmentImages,
        )

        composeRule.setContent {
            FitCheckTheme {
                TryOnRoute(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToProfilePhotos = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TryOnTestTags.FAKE_MODE_BADGE).assertIsDisplayed()
        saveScreenshot("01_ready_unconsented")

        // Scroll to consent & submit button, verify >=48dp touch targets & initial disabled state
        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsNotEnabled()

        composeRule.onNodeWithTag(TryOnTestTags.MODE_DUAL_VIEW)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
        saveScreenshot("02_ready_consented_dual")

        // Submit and wait until PARTIAL terminal state settles
        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            viewModel.uiState.value.activeOrLatestResult?.status == TryOnJobStatus.PARTIAL
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TryOnTestTags.PARTIAL_BADGE).assertIsDisplayed()
        saveScreenshot("03_partial_front_tab")
        composeRule.onNodeWithTag(TryOnTestTags.DISCLAIMER_TEXT)
            .performScrollTo()
            .assertIsDisplayed()

        // Switch to Back tab -> must display failure notice, never fake back image
        composeRule.onNodeWithTag(TryOnTestTags.TAB_BACK)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()
        saveScreenshot("04_partial_back_failed_tab")
    }

    @Test
    fun visualQaSmallPhoneMissingBackAndCoreUnavailable() {
        composeRule.setContent {
            FitCheckTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    StaticTryOnScreen(
                        TryOnUiState(
                            isLoadingInitial = false,
                            capability = FakeTryOnFixtures.dualCapability,
                            outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                            personProfile = FakeTryOnFixtures.personFrontOnlySnapshot,
                            garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                            selectedViewMode = TryOnViewMode.FRONT_ONLY,
                            selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                        ),
                    )
                }
            }
        }
        composeRule.onNodeWithTag(TryOnTestTags.MODE_DUAL_VIEW)
            .performScrollTo()
            .assertIsNotEnabled()
        saveScreenshot("05_small_phone_360x640_missing_back")
    }

    @Test
    fun visualQaCoreUnavailableAndProcessingStates() {
        composeRule.setContent {
            FitCheckTheme {
                StaticTryOnScreen(
                    TryOnUiState(
                        isLoadingInitial = false,
                        capability = FakeTryOnFixtures.coreUnknownCapability,
                        outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                        personProfile = FakeTryOnFixtures.personDualSnapshot,
                        garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                    ),
                )
            }
        }
        composeRule.onNodeWithTag(TryOnTestTags.CAPABILITY_UNAVAILABLE_CARD).assertIsDisplayed()
        saveScreenshot("06_core_unavailable_gate")
    }

    @Test
    fun visualQaProcessingRunningState() {
        composeRule.setContent {
            FitCheckTheme {
                StaticTryOnScreen(
                    TryOnUiState(
                        isLoadingInitial = false,
                        capability = FakeTryOnFixtures.dualCapability,
                        outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                        personProfile = FakeTryOnFixtures.personDualSnapshot,
                        garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                        selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                        activeOrLatestResult = FakeTryOnFixtures.previewResult(TryOnJobStatus.RUNNING),
                    ),
                )
            }
        }
        composeRule.onNodeWithTag(TryOnTestTags.CANCEL_BUTTON)
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
        saveScreenshot("07_processing_running")
    }

    @Test
    fun visualQaTwoPaneDarkAndLargeFontScaleVietnamese() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        composeRule.setContent {
            FitCheckTheme(darkTheme = true) {
                StaticTryOnScreen(
                    TryOnUiState(
                        isLoadingInitial = false,
                        capability = FakeTryOnFixtures.dualCapability,
                        outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                        personProfile = FakeTryOnFixtures.personDualSnapshot,
                        garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                        selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                        activeOrLatestResult = FakeTryOnFixtures.previewResult(TryOnJobStatus.SUCCEEDED),
                    ),
                )
            }
        }
        composeRule.waitForIdle()
        saveScreenshot("08_twopane_dark_landscape")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitForIdle()
    }

    @Test
    fun visualQaFontScale185WithLongVietnameseStrings() {
        val longViOutfit = FakeTryOnFixtures.editorialWorkOutfit.copy(
            explanation = "Bản phối sơ mi vải lanh dệt thưa màu kem ấm kết hợp cùng quần âu ống suông xếp ly đôi tạo cảm giác thanh lịch suốt ngày dài.",
            items = listOf(
                TryOnItemSummary(
                    itemId = "item-top-linen",
                    label = "Áo sơ mi vải lanh cổ Đức tay dài màu kem tự nhiên",
                    category = Category.TOP,
                    accentHex = "#E6DCCF",
                ),
                TryOnItemSummary(
                    itemId = "item-shoes-loafer",
                    label = "Giày lười da bê đế khâu thủ công màu nâu sẫm",
                    category = Category.SHOES,
                    accentHex = "#3A3532",
                ),
            ),
        )

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.85f),
            ) {
                FitCheckTheme {
                    StaticTryOnScreen(
                        TryOnUiState(
                            isLoadingInitial = false,
                            capability = FakeTryOnFixtures.frontOnlyCapability,
                            outfitSnapshot = longViOutfit,
                            personProfile = FakeTryOnFixtures.personDualSnapshot,
                            garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                            selectedItemIds = listOf("item-top-linen"),
                        ),
                    )
                }
            }
        }
        saveScreenshot("09_fontscale_185_long_vietnamese")
    }

    @Test
    fun interactiveDemoWorkbenchAndLocalPngFlow() {
        composeRule.setContent {
            FitCheckTheme {
                TryOnDemoWorkbench(initialSimulatedDelayOverrideMs = 20L)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TryOnDemoTags.DEMO_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(TryOnDemoTags.DEMO_CONTROL_PANEL).assertIsDisplayed()

        // Tap "Nạp ảnh .png cục bộ mẫu" to generate & load real local PNG files on device
        composeRule.onNodeWithTag(TryOnDemoTags.LOAD_SAMPLE_LOCAL_PNGS_BUTTON).performClick()
        composeRule.waitForIdle()
        saveScreenshot("10_demo_workbench_local_png_inputs")

        // Collapse demo controls so full TryOnScreen is visible, tick consent, and run local FAKE VTO
        composeRule.onNodeWithTag(TryOnDemoTags.TOGGLE_CONTROLS_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TryOnTestTags.RESULT_SECTION).performScrollTo()
        composeRule.waitForIdle()
        saveScreenshot("11_demo_workbench_local_png_fake_result")
    }

    @Test
    fun localDiskMediaStoreNormalizesExifRotatedCameraJpegOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testFiles = File(context.cacheDir, "exif_test_files").apply {
            deleteRecursively()
            mkdirs()
        }
        val testCache = File(context.cacheDir, "exif_test_cache").apply {
            deleteRecursively()
            mkdirs()
        }

        // Create a real 1024x768 landscape Bitmap and inject EXIF Orientation=6 (90 CW -> 768x1024 portrait)
        val bmp = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(180, 140, 110))
        val rawJpegBos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, rawJpegBos)
        bmp.recycle()
        val rawJpeg = rawJpegBos.toByteArray()
        val exifRotatedJpeg = injectExifOrientationApp1(rawJpeg, orientation = 6)

        val origFile = File(testFiles, "camera_raw_exif6.jpg").apply {
            writeBytes(exifRotatedJpeg)
        }
        val origRef = MediaRef(
            id = "cam-exif6",
            ownerEntityId = "profile-1",
            scope = MediaScope.PERSON,
            view = ImageView.FRONT,
            localRelativePath = origFile.absolutePath,
            mimeType = "image/jpeg",
            width = 1024,
            height = 768,
            byteCount = exifRotatedJpeg.size.toLong(),
            contentHash = LocalDiskTryOnMediaStore.sha256Hex(exifRotatedJpeg),
            revision = 1L,
        )

        val store = LocalDiskTryOnMediaStore(
            filesRootDir = testFiles,
            cacheRootDir = testCache,
        )
        val derivative = store.prepareUploadDerivative(origRef, maxBytes = 2L * 1024L * 1024L).getOrThrow()

        // Must create a temporary derivative with upright portrait dimensions (768x1024) and leave original untouched
        assertNotEquals(origRef.id, derivative.id)
        assertEquals(768, derivative.width)
        assertEquals(1024, derivative.height)
        assertTrue(File(derivative.localRelativePath).exists())
        assertArrayEquals(exifRotatedJpeg, origFile.readBytes())
    }

    @Test
    fun endToEndBackendOpenAiOfflineMockLoop() {
        val args = InstrumentationRegistry.getArguments()
        val requireBackend = args.getString("requireBackendOpenAiE2e") == "true"
        val backendUrl = args.getString("tryOnBackendUrl") ?: "http://10.0.2.2:8788"

        val preflightJson = runCatching {
            val conn = (URL("$backendUrl/v2/tryon/preflight").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                requestMethod = "GET"
                setRequestProperty("x-session-id", "emulator-e2e-preflight")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        }.getOrNull()

        val isOpenAiBackend = preflightJson != null &&
            (preflightJson.contains("\"provider\": \"openai\"") || preflightJson.contains("\"provider\":\"openai\""))

        if (requireBackend) {
            assertTrue(
                "Expected FitCheck local server at $backendUrl/v2/tryon/preflight to be reachable with provider=openai (got: $preflightJson)",
                isOpenAiBackend,
            )
            assertTrue(
                "LIVE gate MUST remain false during automated E2E tests",
                preflightJson!!.contains("\"liveEnabled\": false") || preflightJson.contains("\"liveEnabled\":false"),
            )
        }

        if (!requireBackend && !isOpenAiBackend) {
            if (preflightJson == null) {
                composeRule.setContent {
                    FitCheckTheme {
                        TryOnDemoWorkbench(
                            initialExecutionMode = DemoExecutionMode.BACKEND_OPENAI_E2E,
                            backendBaseUrl = backendUrl,
                            initialControlsExpanded = false,
                        )
                    }
                }
                composeRule.waitUntil(timeoutMillis = 6_000L) {
                    composeRule.onAllNodesWithTag(TryOnTestTags.CAPABILITY_UNAVAILABLE_CARD)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithTag(TryOnTestTags.CAPABILITY_UNAVAILABLE_CARD).assertIsDisplayed()
            }
            return
        }

        composeRule.setContent {
            FitCheckTheme {
                TryOnDemoWorkbench(
                    initialExecutionMode = DemoExecutionMode.BACKEND_OPENAI_E2E,
                    backendBaseUrl = backendUrl,
                    initialControlsExpanded = false,
                )
            }
        }

        // Wait for /v2/tryon/capabilities to load and show FAKE_MODE_BADGE (OFFLINE_MOCK_OPENAI)
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.FAKE_MODE_BADGE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TryOnTestTags.FAKE_MODE_BADGE).assertIsDisplayed()
        saveScreenshot("12_backend_openai_ready")

        // Tick consent and submit real HTTP POST /v2/tryon/jobs -> OpenAITryOnProvider -> Mock /v1/images/edits
        composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TryOnTestTags.RESULT_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        saveScreenshot("13_backend_openai_mock_result")

        // Toggle Compare Original and capture screenshot
        composeRule.onNodeWithTag(TryOnTestTags.COMPARE_ORIGINAL_TOGGLE).performScrollTo().performClick()
        composeRule.waitForIdle()
        saveScreenshot("14_backend_openai_compare_original")

        // Delete result & temporary artifacts over real DELETE /v2/tryon/jobs/{id}
        composeRule.onNodeWithTag(TryOnTestTags.DELETE_RESULT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun endToEndBackendFostaOfflineMockModesAndGarmentsLoop() {
        val args = InstrumentationRegistry.getArguments()
        val requireFostaBackend = args.getString("requireBackendFostaE2e") == "true"
        val backendUrl = args.getString("tryOnBackendUrl") ?: "http://10.0.2.2:8788"

        val preflightJson = runCatching {
            val conn = (URL("$backendUrl/v2/tryon/preflight").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                requestMethod = "GET"
                setRequestProperty("x-session-id", "emulator-fosta-preflight")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        }.getOrNull()

        val isFostaBackend = preflightJson != null &&
            (preflightJson.contains("\"provider\": \"fosta\"") || preflightJson.contains("\"provider\":\"fosta\""))

        if (requireFostaBackend) {
            assertTrue(
                "Expected FitCheck local server at $backendUrl/v2/tryon/preflight to be reachable with provider=fosta (got: $preflightJson)",
                isFostaBackend,
            )
            assertTrue(
                "LIVE gate MUST remain false during automated FOSTA E2E tests",
                preflightJson!!.contains("\"liveEnabled\": false") || preflightJson.contains("\"liveEnabled\":false"),
            )
        }

        if (!requireFostaBackend && !isFostaBackend) {
            return
        }

        composeRule.setContent {
            FitCheckTheme {
                TryOnDemoWorkbench(
                    initialExecutionMode = DemoExecutionMode.BACKEND_FOSTA_E2E,
                    backendBaseUrl = backendUrl,
                    initialControlsExpanded = false,
                )
            }
        }

        // Wait for /v2/tryon/capabilities (FOSTA) to load and show FAKE_MODE_BADGE (OFFLINE_MOCK_FOSTA)
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.FAKE_MODE_BADGE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TryOnTestTags.FAKE_MODE_BADGE).assertIsDisplayed()
        saveScreenshot("14b_fosta_default_original_photo")

        // Exercise BACK view chip and capture screenshot
        composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()
        saveScreenshot("14c_fosta_back_view_selected")

        // Switch back to FRONT view for the TOP+BOTTOM + STUDIO_WHITE run
        composeRule.onNodeWithTag(TryOnTestTags.MODE_FRONT_ONLY)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()

        // Exercise garment combo chip: select TOP+BOTTOM and verify EXPERIMENTAL_NOTICE appears
        composeRule.onNodeWithTag(TryOnTestTags.GARMENT_COMBO_TOP_BOTTOM)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TryOnTestTags.EXPERIMENTAL_NOTICE)
            .performScrollTo()
            .assertIsDisplayed()

        // Exercise presentation mode chip: select STUDIO_WHITE
        composeRule.onNodeWithTag(TryOnTestTags.PRESENTATION_STUDIO_WHITE)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()

        // Tick FOSTA-bound consent
        composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()
        saveScreenshot("15_backend_fosta_studio_combo_ready")

        // Submit real HTTP POST /v2/tryon/jobs -> FostaTryOnProvider -> Mock /v1/responses
        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TryOnTestTags.RESULT_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        saveScreenshot("16_backend_fosta_studio_combo_mock_result")

        // Toggle Compare Original and capture screenshot
        composeRule.onNodeWithTag(TryOnTestTags.COMPARE_ORIGINAL_TOGGLE)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        saveScreenshot("17_backend_fosta_compare_original")

        // Delete result & temporary artifacts over real DELETE /v2/tryon/jobs/{id}
        composeRule.onNodeWithTag(TryOnTestTags.DELETE_RESULT_BUTTON)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun injectExifOrientationApp1(jpegBytes: ByteArray, orientation: Int): ByteArray {
        val exifPayload = byteArrayOf(
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A,
            0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12,
            0x00, 0x03,
            0x00, 0x00, 0x00, 0x01,
            0x00, orientation.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        val app1Len = exifPayload.size + 2
        val bos = ByteArrayOutputStream()
        bos.write(jpegBytes, 0, 2) // FF D8 SOI
        bos.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (app1Len ushr 8).toByte(), (app1Len and 0xFF).toByte()))
        bos.write(exifPayload)
        bos.write(jpegBytes, 2, jpegBytes.size - 2)
        return bos.toByteArray()
    }

    @androidx.compose.runtime.Composable
    private fun StaticTryOnScreen(state: TryOnUiState) {
        TryOnScreen(
            state = state,
            onNavigateBack = {},
            onNavigateToProfilePhotos = {},
            onSelectDifferentOutfit = {},
            onSelectViewMode = {},
            onToggleItem = {},
            onConsentChanged = {},
            onSubmit = {},
            onCancelJob = {},
            onSelectResultTab = {},
            onToggleCompareOriginal = {},
            onDeleteResult = {},
            onPrepareFreshAttempt = {},
        )
    }
}

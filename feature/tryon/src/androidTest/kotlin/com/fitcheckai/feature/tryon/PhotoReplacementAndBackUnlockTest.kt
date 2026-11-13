package com.fitcheckai.feature.tryon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.ui.theme.FitCheckTheme
import com.fitcheckai.feature.tryon.demo.DemoExecutionMode
import com.fitcheckai.feature.tryon.demo.TryOnDemoTags
import com.fitcheckai.feature.tryon.demo.TryOnDemoWorkbench
import com.fitcheckai.feature.tryon.ui.TryOnTestTags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class PhotoReplacementAndBackUnlockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun replacingPickedPhotoAcrossPersonAndGarmentSlotsUpdatesPreviewResetsConsentAndClearsResult() {
        val args = InstrumentationRegistry.getArguments()
        val backendUrl = args.getString("tryOnBackendUrl") ?: "http://10.0.2.2:8788"

        val preflightJson = runCatching {
            val conn = (URL("$backendUrl/v2/tryon/preflight").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                requestMethod = "GET"
                setRequestProperty("x-session-id", "emulator-g4-replace-test")
            }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        }.getOrNull()

        assertTrue(
            "Expected FOSTA mock backend at $backendUrl/v2/tryon/preflight (got: $preflightJson)",
            preflightJson != null &&
                (preflightJson.contains("\"provider\":\"fosta\"") || preflightJson.contains("\"provider\": \"fosta\"")) &&
                (preflightJson.contains("\"liveEnabled\":false") || preflightJson.contains("\"liveEnabled\": false")),
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inputDir = File(context.cacheDir, "g4_replace_inputs").apply { mkdirs() }
        val fileRedA = File(inputDir, "photo_a_red.png").also {
            writeSolidPng(it, width = 384, height = 512, color = Color.rgb(220, 30, 30))
        }
        val fileBlueB = File(inputDir, "photo_b_blue.png").also {
            writeSolidPng(it, width = 450, height = 600, color = Color.rgb(30, 80, 220))
        }
        val hashA = sha256(fileRedA)
        val hashB = sha256(fileBlueB)

        val pickedDir = File(context.cacheDir, "tryon_demo_picked")
        pickedDir.listFiles()?.forEach { it.delete() }

        val pickerRegistryOwner = TestPickerRegistryOwner()

        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerRegistryOwner) {
                FitCheckTheme {
                    TryOnDemoWorkbench(
                        initialExecutionMode = DemoExecutionMode.BACKEND_FOSTA_E2E,
                        backendBaseUrl = backendUrl,
                        initialControlsExpanded = false,
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.FAKE_MODE_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        // Select TOP+BOTTOM so bottom_front is also active and invalidates prior FRONT result when replaced
        composeRule.onNodeWithTag(TryOnTestTags.GARMENT_COMBO_TOP_BOTTOM)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        data class SlotSpec(
            val name: String,
            val buttonTag: String,
            val afterResultButtonTag: String?,
            val diskFilename: String,
            val previewContentDescription: String,
            val submitMockAfterReplace: Boolean,
        )

        val slots = listOf(
            SlotSpec("person_front", TryOnTestTags.PICK_PERSON_FRONT_INLINE, TryOnDemoTags.PICK_PERSON_FRONT_BUTTON, "local-picked-person-front.png", "Mặt trước", submitMockAfterReplace = true),
            SlotSpec("top_front", TryOnTestTags.PICK_GARMENT_TOP_INLINE, TryOnTestTags.PICK_GARMENT_TOP_INLINE, "local-picked-garment-front.png", "Áo sơ mi linen kem (Trước)", submitMockAfterReplace = true),
            SlotSpec("top_back", TryOnTestTags.PICK_GARMENT_TOP_BACK_INLINE, null, "local-picked-garment-back.png", "Áo sơ mi linen kem (Sau)", submitMockAfterReplace = false),
            SlotSpec("bottom_front", TryOnTestTags.PICK_GARMENT_BOTTOM_INLINE, null, "local-picked-bottom-front.png", "Quần âu ống suông đen (Trước)", submitMockAfterReplace = false),
        )

        for (slot in slots) {
            val targetDiskFile = File(pickedDir, slot.diskFilename)

            // 1. Pick RED Image A
            pickerRegistryOwner.nextPickedUri = Uri.fromFile(fileRedA)
            composeRule.onNodeWithTag(slot.buttonTag).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                targetDiskFile.exists() && sha256(targetDiskFile) == hashA &&
                    composeRule.onAllNodesWithContentDescription(slot.previewContentDescription)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            // Verify preview renders RED before replacement
            composeRule.onNodeWithContentDescription(slot.previewContentDescription)
                .performScrollTo()
                .assertIsDisplayed()
            val rgbA = sampleCenterRgb(
                composeRule.onNodeWithContentDescription(slot.previewContentDescription)
                    .captureToImage()
                    .asAndroidBitmap(),
            )
            assertTrue("Expected RED preview for ${slot.name} A, got $rgbA", rgbA.first > 180 && rgbA.third < 80)

            // Tick consent before replacing A -> B
            val alreadyEnabled = runCatching {
                composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON).assertIsEnabled()
            }.isSuccess
            if (!alreadyEnabled) {
                composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW)
                    .performScrollTo()
                    .performClick()
                composeRule.waitForIdle()
            }
            composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON).assertIsEnabled()

            // 2. Replace A -> BLUE Image B on the SAME inline button
            pickerRegistryOwner.nextPickedUri = Uri.fromFile(fileBlueB)
            composeRule.onNodeWithTag(slot.buttonTag).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                targetDiskFile.exists() && sha256(targetDiskFile) == hashB
            }
            composeRule.waitForIdle()

            // Wait until Coil finishes painting the BLUE bitmap on screen
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                composeRule.onNodeWithContentDescription(slot.previewContentDescription).performScrollTo()
                val rgb = sampleCenterRgb(
                    composeRule.onNodeWithContentDescription(slot.previewContentDescription)
                        .captureToImage()
                        .asAndroidBitmap(),
                )
                rgb.third > 180 && rgb.first < 80
            }

            // Assert consent was reset to false
            composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
                .performScrollTo()
                .assertIsNotEnabled()

            // 3. Also verify stale prior result invalidation when a mock job has already produced RESULT_SECTION
            if (slot.submitMockAfterReplace && slot.afterResultButtonTag != null) {
                composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW)
                    .performScrollTo()
                    .performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
                    .performScrollTo()
                    .assertIsEnabled()
                    .performClick()
                composeRule.waitUntil(timeoutMillis = 15_000L) {
                    composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isNotEmpty()
                }

                val needsControlsDrawer = slot.afterResultButtonTag == TryOnDemoTags.PICK_PERSON_FRONT_BUTTON
                if (needsControlsDrawer) {
                    composeRule.onNodeWithTag(TryOnDemoTags.TOGGLE_CONTROLS_BUTTON).performClick()
                    composeRule.waitUntil(timeoutMillis = 5_000L) {
                        composeRule.onAllNodesWithTag(TryOnDemoTags.DEMO_CONTROL_PANEL).fetchSemanticsNodes().isNotEmpty()
                    }
                }

                pickerRegistryOwner.nextPickedUri = Uri.fromFile(fileRedA)
                composeRule.onNodeWithTag(slot.afterResultButtonTag).performScrollTo().performClick()
                composeRule.waitUntil(timeoutMillis = 8_000L) {
                    targetDiskFile.exists() && sha256(targetDiskFile) == hashA
                }
                composeRule.waitForIdle()

                if (needsControlsDrawer) {
                    composeRule.onNodeWithTag(TryOnDemoTags.TOGGLE_CONTROLS_BUTTON).performClick()
                    composeRule.waitForIdle()
                }

                assertTrue(
                    "Expected prior result section to be invalidated after replacing ${slot.name}",
                    composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isEmpty(),
                )
                composeRule.waitUntil(timeoutMillis = 8_000L) {
                    composeRule.onNodeWithContentDescription(slot.previewContentDescription).performScrollTo()
                    val rgb = sampleCenterRgb(
                        composeRule.onNodeWithContentDescription(slot.previewContentDescription)
                            .captureToImage()
                            .asAndroidBitmap(),
                    )
                    rgb.first > 180 && rgb.third < 80
                }
                composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    @Test
    fun pickingAllRequiredBackPhotosUnlocksBackOnlyViewChipAndFixesProfileCta() {
        val stubServer = LocalFostaCapabilityOnlyServer().also { it.start() }
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val inputDir = File(context.cacheDir, "g4_back_unlock_inputs").apply { mkdirs() }
            val personBackFile = File(inputDir, "person_back_green.png").also {
                writeSolidPng(it, width = 384, height = 512, color = Color.rgb(30, 170, 70))
            }
            val topBackFile = File(inputDir, "top_back_purple.png").also {
                writeSolidPng(it, width = 384, height = 512, color = Color.rgb(150, 40, 190))
            }
            val bottomBackFile = File(inputDir, "bottom_back_amber.png").also {
                writeSolidPng(it, width = 384, height = 512, color = Color.rgb(210, 140, 30))
            }

            val pickedDir = File(context.cacheDir, "tryon_demo_picked")
            pickedDir.listFiles()?.forEach { it.delete() }
            val pickedPersonFront = File(pickedDir, "local-picked-person-front.png")
            val pickedPersonBack = File(pickedDir, "local-picked-person-back.png")
            val pickedTopBack = File(pickedDir, "local-picked-garment-back.png")
            val pickedBottomBack = File(pickedDir, "local-picked-bottom-back.png")

            val pickerRegistryOwner = TestPickerRegistryOwner()

            composeRule.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerRegistryOwner) {
                    FitCheckTheme {
                        TryOnDemoWorkbench(
                            initialExecutionMode = DemoExecutionMode.BACKEND_FOSTA_E2E,
                            backendBaseUrl = "http://127.0.0.1:${stubServer.port}",
                            initialControlsExpanded = false,
                        )
                    }
                }
            }

            // Wait for capabilities from local stub so MODE_BACK_ONLY renders and is locked
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                composeRule.onAllNodesWithTag(TryOnTestTags.MODE_BACK_ONLY).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY)
                .performScrollTo()
                .assertIsNotEnabled()
            // Hint must explicitly name missing slots & "Chọn Sau"
            composeRule.onNodeWithText("Cần chọn thêm cho:", substring = true)
                .performScrollTo()
                .assertIsDisplayed()

            // 1. Tap PROFILE_PHOTO_CTA ("Bổ sung ảnh mặt sau tại Hồ sơ") -> MUST launch BACK picker, NOT front!
            pickerRegistryOwner.nextPickedUri = Uri.fromFile(personBackFile)
            composeRule.onNodeWithTag(TryOnTestTags.PROFILE_PHOTO_CTA)
                .performScrollTo()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                pickedPersonBack.exists() && pickedPersonBack.length() == personBackFile.length()
            }
            composeRule.waitForIdle()
            assertFalse(
                "PROFILE_PHOTO_CTA must not overwrite person front slot",
                pickedPersonFront.exists(),
            )
            // CTA disappears once both person front & back are present
            assertTrue(
                composeRule.onAllNodesWithTag(TryOnTestTags.PROFILE_PHOTO_CTA).fetchSemanticsNodes().isEmpty(),
            )

            // Back chip still locked while selected top lacks back photo
            composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY)
                .performScrollTo()
                .assertIsNotEnabled()

            // 2. Pick top back photo -> in TOP_ONLY combo, MODE_BACK_ONLY unlocks!
            pickerRegistryOwner.nextPickedUri = Uri.fromFile(topBackFile)
            composeRule.onNodeWithTag(TryOnTestTags.PICK_GARMENT_TOP_BACK_INLINE)
                .performScrollTo()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                pickedTopBack.exists() && pickedTopBack.length() == topBackFile.length()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY)
                .performScrollTo()
                .assertIsEnabled()

            // 3. Switch to TOP_AND_BOTTOM -> locks again until bottom_back is picked
            composeRule.onNodeWithTag(TryOnTestTags.GARMENT_COMBO_TOP_BOTTOM)
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY)
                .performScrollTo()
                .assertIsNotEnabled()
            assertTrue(
                composeRule.onAllNodesWithText("Quần âu ống suông đen", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )

            // 4. Pick bottom back photo -> MODE_BACK_ONLY unlocks and can be selected!
            pickerRegistryOwner.nextPickedUri = Uri.fromFile(bottomBackFile)
            composeRule.onNodeWithTag(TryOnTestTags.PICK_GARMENT_BOTTOM_BACK_INLINE)
                .performScrollTo()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 8_000L) {
                pickedBottomBack.exists() && pickedBottomBack.length() == bottomBackFile.length()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY)
                .performScrollTo()
                .assertIsEnabled()
                .performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(TryOnTestTags.MODE_BACK_ONLY).assertIsSelected()
        } finally {
            stubServer.close()
        }
    }

    private class TestPickerRegistryOwner : ActivityResultRegistryOwner {
        @Volatile
        var nextPickedUri: Uri? = null

        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, nextPickedUri)
            }
        }
    }

    private class LocalFostaCapabilityOnlyServer {
        private val running = AtomicBoolean(true)
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort

        fun start() {
            thread(isDaemon = true) {
                while (running.get()) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    runCatching {
                        client.use { s ->
                            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                            val requestLine = reader.readLine() ?: return@use
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val body = when {
                                requestLine.contains("/v2/tryon/preflight") -> {
                                    """{"provider":"fosta","liveEnabled":false,"offlineMockEnabled":false,"readyForLivePocCall":false,"usedLiveRequests":5,"maxRequests":10,"remainingLiveRequests":5,"quotaExhausted":false,"checks":{"modelId":"gpt-6-luna"},"blockers":[]}"""
                                }
                                requestLine.contains("/v2/tryon/capabilities") -> {
                                    """{"decision":"GO_DUAL","supportedViews":["FRONT","BACK"],"supportedCategories":["TOP","BOTTOM"],"supportedPresentationModes":["ORIGINAL_PHOTO","STUDIO_WHITE"],"maxGarmentsPerJob":2,"supportsNativePairedViews":false,"runtimeVerifiedViews":["FRONT","BACK"],"providerId":"fosta","providerConfigVersion":"v2-fosta-poc-1","executionMode":"TEST_NO_SAMPLE_BACKS","modelId":"gpt-6-luna"}"""
                                }
                                else -> """{"error":"not_found"}"""
                            }
                            val status = if (body.contains("not_found")) "404 Not Found" else "200 OK"
                            val payload = body.toByteArray(Charsets.UTF_8)
                            val headers = (
                                "HTTP/1.1 $status\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: ${payload.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(Charsets.UTF_8)
                            s.getOutputStream().use { out ->
                                out.write(headers)
                                out.write(payload)
                                out.flush()
                            }
                        }
                    }
                }
            }
        }

        fun close() {
            running.set(false)
            runCatching { socket.close() }
        }
    }

    private fun writeSolidPng(file: File, width: Int, height: Int, color: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sampleCenterRgb(bitmap: Bitmap): Triple<Int, Int, Int> {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        for (dx in -5..5) {
            for (dy in -5..5) {
                val x = (cx + dx).coerceIn(0, bitmap.width - 1)
                val y = (cy + dy).coerceIn(0, bitmap.height - 1)
                val px = bitmap.getPixel(x, y)
                rSum += Color.red(px)
                gSum += Color.green(px)
                bSum += Color.blue(px)
                count += 1L
            }
        }
        return Triple((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }
}

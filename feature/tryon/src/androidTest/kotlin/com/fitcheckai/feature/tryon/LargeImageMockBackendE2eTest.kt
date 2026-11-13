package com.fitcheckai.feature.tryon

import android.graphics.Bitmap
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.ui.theme.FitCheckTheme
import com.fitcheckai.feature.tryon.demo.DemoExecutionMode
import com.fitcheckai.feature.tryon.demo.TryOnDemoWorkbench
import com.fitcheckai.feature.tryon.ui.TryOnTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class LargeImageMockBackendE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun largeExifPersonAndLargeGarmentUriImportSubmitsWithinCapsWithout403() {
        val args = InstrumentationRegistry.getArguments()
        val requireStep3e = args.getString("requireStep3eLargeImageE2e") == "true"
        val backendUrl = args.getString("tryOnBackendUrl") ?: "http://10.0.2.2:8788"

        val preflightJson = runCatching {
            val conn = (URL("$backendUrl/v2/tryon/preflight").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                requestMethod = "GET"
                setRequestProperty("x-session-id", "emulator-step3e-preflight")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        }.getOrNull()

        val hasStep3eSeed = preflightJson != null && runCatching {
            val probe = (URL("$backendUrl/__step3e_seed/person.jpg").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                requestMethod = "HEAD"
            }
            probe.responseCode == 200
        }.getOrDefault(false)

        if (!requireStep3e && !hasStep3eSeed) {
            return
        }

        assertTrue(
            "Expected FOSTA mock backend at $backendUrl/v2/tryon/preflight (got: $preflightJson)",
            preflightJson != null &&
                (preflightJson.contains("\"provider\": \"fosta\"") || preflightJson.contains("\"provider\":\"fosta\"")),
        )
        assertTrue(
            "TRYON_LIVE_ENABLED MUST remain false during Step 3E mock test",
            preflightJson!!.contains("\"liveEnabled\": false") || preflightJson.contains("\"liveEnabled\":false"),
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val seedDir = File(context.cacheDir, "step3e_gallery_seed").apply { mkdirs() }
        val personSeedFile = File(seedDir, "person_4000x3000_exif6.jpg")
        val topSeedFile = File(seedDir, "top_garment_large.png")

        downloadSeed("$backendUrl/__step3e_seed/person.jpg", personSeedFile)
        downloadSeed("$backendUrl/__step3e_seed/top.png", topSeedFile)

        assertTrue(
            "Expected 4-6 MB person seed JPEG, got ${personSeedFile.length()} bytes",
            personSeedFile.length() in 4_000_000L..6_000_000L,
        )
        assertTrue(
            "Expected 2-3 MB garment seed PNG, got ${topSeedFile.length()} bytes",
            topSeedFile.length() in 2_000_000L..3_200_000L,
        )

        val pickedDir = File(context.cacheDir, "tryon_demo_picked")
        val pickedPersonFile = File(pickedDir, "local-picked-person-front.jpg")
        val pickedTopFile = File(pickedDir, "local-picked-garment-front.png")
        pickedPersonFile.delete()
        pickedTopFile.delete()

        val pickerRegistryOwner = object : ActivityResultRegistryOwner {
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

        // Import ~5MB 4000x3000 EXIF=6 person photo via real inline gallery picker path
        pickerRegistryOwner.nextPickedUri = Uri.fromFile(personSeedFile)
        composeRule.onNodeWithTag(TryOnTestTags.PICK_PERSON_FRONT_INLINE)
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 12_000L) {
            pickedPersonFile.exists() &&
                pickedPersonFile.length() == personSeedFile.length() &&
                composeRule.onAllNodesWithTag(TryOnTestTags.FAKE_MODE_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        // Import ~2.5MB large garment PNG via real inline gallery picker path
        pickerRegistryOwner.nextPickedUri = Uri.fromFile(topSeedFile)
        composeRule.onNodeWithTag(TryOnTestTags.PICK_GARMENT_TOP_INLINE)
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 12_000L) {
            pickedTopFile.exists() &&
                pickedTopFile.length() == topSeedFile.length() &&
                composeRule.onAllNodesWithTag(TryOnTestTags.FAKE_MODE_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        // Ensure FRONT + TOP_ONLY selection before consent & submit
        composeRule.onNodeWithTag(TryOnTestTags.GARMENT_COMBO_TOP)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(TryOnTestTags.MODE_FRONT_ONLY)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TryOnTestTags.CONSENT_CHECKBOX_ROW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TryOnTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 35_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TryOnTestTags.RESULT_SECTION)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.waitForIdle()
        Thread.sleep(400L)

        captureAndUploadScreenshot(
            name = "18_step3e_large_exif_mock_result",
            uploadUrl = "$backendUrl/__step3e_screenshot",
        )

        composeRule.onNodeWithTag(TryOnTestTags.DELETE_RESULT_BUTTON)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(TryOnTestTags.RESULT_SECTION).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun downloadSeed(urlStr: String, target: File) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        assertTrue("Expected HTTP 200 downloading seed $urlStr, got ${conn.responseCode}", conn.responseCode == 200)
        conn.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun captureAndUploadScreenshot(name: String, uploadUrl: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val pngBytes = bos.toByteArray()

        val additionalDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = if (!additionalDir.isNullOrBlank()) {
            File(additionalDir).apply { mkdirs() }
        } else {
            File(context.externalCacheDir ?: context.cacheDir, "visual_qa").apply { mkdirs() }
        }
        FileOutputStream(File(outDir, "$name.png")).use { fos ->
            fos.write(pngBytes)
            fos.flush()
        }

        val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "image/png")
        }
        conn.outputStream.use { it.write(pngBytes) }
        assertTrue("Screenshot upload failed: HTTP ${conn.responseCode}", conn.responseCode in 200..299)
    }
}

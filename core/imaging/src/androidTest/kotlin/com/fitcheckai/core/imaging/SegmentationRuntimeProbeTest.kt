package com.fitcheckai.core.imaging

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.model.wardrobe.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runtime observations on public fixtures; not a segmentation accuracy benchmark. */
@RunWith(AndroidJUnit4::class)
class SegmentationRuntimeProbeTest {
    @Test fun observeSixRealGarmentsOnlyWhenTheOnDeviceModelIsReady() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val images = ImageStore(context); val media = LocalMediaStore(context)
        val segmenter = MlKitSegmenter(context, images)
        val processor = MlKitGarmentCutoutProcessor(segmenter, media, images)
        val folder = File(context.filesDir, "qa-segmentation").apply { mkdirs() }
        var readiness = processor.readiness(requestDownload = true)
        repeat(6) { if (readiness != SegmentationReadiness.READY) { delay(2_000); readiness = processor.readiness(requestDownload = false) } }
        val observations = mutableListOf<JsonObject>()
        fun report() {
            val json = buildJsonObject {
                put("readiness", readiness.name); put("observations", JsonArray(observations))
                put("scope", "Runtime/foreground observations only. No mask ground truth, physical-device or fit-accuracy claim.")
            }.toString()
            File(folder, "report.json").writeText(json)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "TRACK_A_SEGMENTATION_REPORT=$json\n") })
        }
        report()
        assumeTrue("ML Kit model is $readiness on this device; original-only fallback remains available", readiness == SegmentationReadiness.READY)
        val manifest = context.assets.open("modern-clothing/manifest.json").bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        val cases = manifest.getValue("items").jsonArray.map { it.jsonObject }.distinctBy { it.getValue("category").jsonPrimitive.content }.take(6)
        assertEquals(6, cases.size)
        for ((index, item) in cases.withIndex()) {
            val path = item.getValue("front").jsonObject.getValue("file").jsonPrimitive.content
            val source = File(folder, "source-$index.jpg")
            context.assets.open("modern-clothing/$path").use { input -> source.outputStream().use { input.copyTo(it) } }
            val original = media.importImage(source.path, MediaScope.GARMENT, UUID.randomUUID().toString(), ImageView.FRONT).getOrThrow()
            val before = media.read(original).getOrThrow(); val start = SystemClock.elapsedRealtime()
            val output = processor.create(original)
            try {
                observations += buildJsonObject {
                    put("fixtureId", item.getValue("id").jsonPrimitive.content)
                    put("category", item.getValue("category").jsonPrimitive.content)
                    put("elapsedMs", SystemClock.elapsedRealtime() - start)
                    put("result", if (output.isSuccess) "OUTPUT_REQUIRES_VISUAL_REVIEW" else "ORIGINAL_FALLBACK")
                    output.getOrNull()?.let { cutout ->
                        assertEquals(original.id, cutout.derivedFromMediaId)
                        val bytes = media.read(cutout).getOrThrow()
                        File(folder, "cutout-$index.png").writeBytes(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        put("opaqueFraction", pixels.count { (it ushr 24) > 127 }.toDouble() / pixels.size)
                        put("width", bitmap.width); put("height", bitmap.height); bitmap.recycle()
                    }
                }
                assertArrayEquals(before, media.read(original).getOrThrow())
            } finally { media.remove(original); report() }
        }
        assertEquals(6, observations.size)
    }
}

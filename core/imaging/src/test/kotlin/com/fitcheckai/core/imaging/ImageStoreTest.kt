package com.fitcheckai.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var store: ImageStore

    @Before fun setup() { store = ImageStore(context) }

    private fun source(width: Int = 100, height: Int = 200, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return File(context.filesDir, "input.${if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"}").also {
            it.outputStream().use { out -> bitmap.compress(format, 90, out) }
            bitmap.recycle()
        }
    }

    @Test fun `large import is sampled resized and persisted outside cache`() = runBlocking {
        val file = source(2400, 4000)
        val path = store.importSource(file.toURI().toString())
        val bitmap = BitmapFactory.decodeFile(path)
        assertEquals(768, bitmap.height)
        assertEquals(461, bitmap.width)
        assertTrue(path.startsWith(File(context.filesDir, "wardrobe/drafts").path))
        assertTrue(File(path).length() > 0)
        bitmap.recycle()
    }

    @Test fun `EXIF rotation is applied once`() = runBlocking {
        val file = source(100, 200, Bitmap.CompressFormat.JPEG)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val path = store.importSource(file.path)
        val bitmap = BitmapFactory.decodeFile(path)
        assertEquals(200, bitmap.width)
        assertEquals(100, bitmap.height)
        bitmap.recycle()
        val again = store.importSource(path)
        assertEquals(200, BitmapFactory.decodeFile(again).width)
    }

    @Test fun `transparent PNG and white JPEG upload have exact hash`() = runBlocking {
        val transparent = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        transparent.setPixel(25, 25, Color.RED)
        val path = store.saveForeground(transparent)
        assertEquals(0, Color.alpha(BitmapFactory.decodeFile(path).getPixel(0, 0)))
        val upload = store.prepareUpload(path)
        val jpeg = BitmapFactory.decodeFile(upload.path)
        assertEquals("image/jpeg", upload.mimeType)
        assertEquals(ImageStore.sha256(File(upload.path)), upload.sha256)
        assertEquals(64, upload.sha256.length)
        assertTrue(Color.red(jpeg.getPixel(0, 0)) > 245)
        assertTrue(Color.green(jpeg.getPixel(0, 0)) > 245)
        assertEquals(50, jpeg.width)
        store.discard(path, upload.path)
        assertFalse(File(path).exists())
        assertFalse(File(upload.path).exists())
        transparent.recycle(); jpeg.recycle()
    }

    @Test fun `cleanup cannot delete saved wardrobe photos or caller images`() = runBlocking {
        val original = source()
        val draft = store.importSource(original.path)
        val saved = File(context.filesDir, "wardrobe/saved.png").apply { writeBytes(File(draft).readBytes()) }
        store.discard(original.path, saved.path, draft, draft)
        assertTrue(original.exists())
        assertTrue(saved.exists())
        assertFalse(File(draft).exists())
    }

    @Test fun `prune only removes expired draft files`() = runBlocking {
        val old = store.createCaptureFile().apply { writeText("old"); setLastModified(1000) }
        val recent = store.createCaptureFile().apply { writeText("recent") }
        store.pruneOldDrafts()
        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test fun `corrupt and remote inputs fail without creating a photo`() = runBlocking {
        val corrupt = File(context.filesDir, "bad.png").apply { writeText("not an image") }
        assertTrue(runCatching { store.importSource(corrupt.path) }.isFailure)
        assertTrue(runCatching { store.importSource("https://example.com/image.png") }.isFailure)
    }

    @Test fun `sampling preserves small images and limits large allocations`() {
        assertEquals(1, ImageStore.sampleSize(100, 200, 768))
        assertEquals(8, ImageStore.sampleSize(8000, 4000, 768))
    }
}

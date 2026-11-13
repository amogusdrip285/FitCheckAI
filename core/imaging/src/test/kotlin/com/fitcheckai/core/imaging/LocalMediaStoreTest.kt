package com.fitcheckai.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.fitcheckai.core.model.wardrobe.*
import java.io.File
import java.util.UUID
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
class LocalMediaStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var media: LocalMediaStore
    private val owner get() = UUID.randomUUID().toString()
    @Before fun setup() { media = LocalMediaStore(context) }
    private fun source(width: Int = 180, height: Int = 250, jpeg: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val path = File.createTempFile("original-", if (jpeg) ".jpg" else ".png", context.cacheDir)
        path.outputStream().use { bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle(); return path
    }
    @Test fun `import normalizes orientation strips GPS caps bytes and persists beyond source`() = runBlocking {
        val input = source(2400, 3200, true)
        ExifInterface(input).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(10.0, 106.0); setAttribute(ExifInterface.TAG_ARTIST, "private metadata"); saveAttributes()
        }
        val ref = media.importImage(input.path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        assertEquals(2048, ref.width); assertEquals(1536, ref.height)
        assertTrue(ref.byteCount <= 4L * 1024 * 1024)
        val local = File(media.displayPath(ref)!!)
        assertNull(ExifInterface(local).latLong)
        assertNull(ExifInterface(local).getAttribute(ExifInterface.TAG_ARTIST))
        input.delete(); assertTrue(media.read(ref).isSuccess)
        val again = media.importImage(local.path, ref.scope, ref.ownerEntityId, ref.view).getOrThrow()
        assertEquals(ref.width, again.width); assertEquals(ref.height, again.height)
    }
    @Test fun `person and tryon originals derivatives and registries stay outside backup`() = runBlocking {
        for (scope in listOf(MediaScope.PERSON, MediaScope.TRYON_RESULT)) {
            val ref = media.importImage(source().path, scope, owner, ImageView.FRONT).getOrThrow()
            val derived = media.derive(ref, DerivativePreset.RECOGNITION).getOrThrow()
            assertTrue(media.displayPath(ref)!!.startsWith(context.noBackupFilesDir.path))
            assertTrue(media.displayPath(derived)!!.startsWith(context.noBackupFilesDir.path))
            assertTrue(File(context.noBackupFilesDir, "media-index/${ref.id}.json").isFile)
            assertFalse(File(context.filesDir, "media-index/${ref.id}.json").exists())
            assertEquals("image/jpeg", derived.mimeType)
            assertEquals(ref.id, derived.derivedFromMediaId)
            media.remove(ref).getOrThrow()
            assertNull(media.displayPath(derived))
            assertFalse(File(context.noBackupFilesDir, "media-index/${ref.id}.json").exists())
        }
    }
    @Test fun `hash ownership missing bytes and path traversal fail safely`() = runBlocking {
        val ref = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        assertTrue(media.read(ref.copy(ownerEntityId = owner)).isFailure)
        assertTrue(media.remove(ref.copy(localRelativePath = "../private.txt")).isFailure)
        val file = File(media.displayPath(ref)!!)
        file.appendBytes(byteArrayOf(42))
        assertTrue(media.read(ref).isFailure)
        file.delete()
        assertNull(media.displayPath(ref)); assertTrue(media.read(ref).isFailure)
        assertTrue(media.remove(ref).isSuccess); assertTrue(media.remove(ref).isSuccess)
    }
    @Test fun `derivatives do not replace original and removal cascades only owned descendants`() = runBlocking {
        val ref = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val other = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val original = media.read(ref).getOrThrow()
        val derivative = media.derive(ref, DerivativePreset.THUMBNAIL).getOrThrow()
        val grandchild = media.derive(derivative, DerivativePreset.RECOGNITION).getOrThrow()
        assertTrue(derivative.width <= 384 && derivative.height <= 384 && derivative.byteCount <= 256 * 1024)
        assertArrayEquals(original, media.read(ref).getOrThrow())
        media.remove(ref).getOrThrow()
        assertTrue(media.read(derivative).isFailure); assertTrue(media.read(grandchild).isFailure)
        assertTrue(media.read(other).isSuccess)
        assertEquals(0, media.retryCleanup().getOrThrow())
    }
    @Suppress("DEPRECATION") // WEBP_LOSSLESS is unavailable on the API 28 test device.
    @Test fun `profile imports transparent WebP with a png filename`() = runBlocking {
        val input = File.createTempFile("webp-photo-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 80, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            bitmap.setPixel(0, 0, Color.TRANSPARENT)
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, it)) }
        } finally {
            bitmap.recycle()
        }

        val ref = media.importImage(input.path, MediaScope.PERSON, owner, ImageView.FRONT).getOrThrow()
        assertEquals("image/png", ref.mimeType)
        assertTrue(media.displayPath(ref)!!.startsWith(context.noBackupFilesDir.path))
        val bytes = media.read(ref).getOrThrow()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(Color.TRANSPARENT, decoded.getPixel(0, 0))
        } finally {
            decoded.recycle()
        }
    }
    @Test fun `invalid format huge encoded input and missing segmentation original create no registered output`() = runBlocking {
        val broken = File.createTempFile("broken", ".jpg", context.cacheDir).apply { writeText("not an image") }
        val invalid = media.importImage(broken.path, MediaScope.GARMENT, owner, ImageView.FRONT).exceptionOrNull()
        assertEquals(ErrorCode.INVALID_REQUEST, (invalid as WardrobeException).code)
        assertTrue(invalid.message.orEmpty().startsWith("Không đọc được tệp ảnh."))
        val huge = File.createTempFile("huge", ".png", context.cacheDir)
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(33L * 1024 * 1024) }
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, (media.importImage(huge.path, MediaScope.GARMENT, owner, ImageView.FRONT).exceptionOrNull() as WardrobeException).code)
        huge.delete()
        val original = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val segmented = media.importSegmented(source().path, original).getOrThrow()
        assertEquals(original.id, segmented.derivedFromMediaId)
        media.remove(original).getOrThrow()
        val before = File(context.filesDir, "media-index").listFiles().orEmpty().size
        assertTrue(media.importSegmented(source().path, original).isFailure)
        assertEquals(before, File(context.filesDir, "media-index").listFiles().orEmpty().size)
    }
    @Test fun `draft recovery expires only old unreferenced garments including their derivatives`() = runBlocking {
        val orphan = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val child = media.derive(orphan, DerivativePreset.THUMBNAIL).getOrThrow()
        val retained = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val recent = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val person = media.importImage(source().path, MediaScope.PERSON, owner, ImageView.FRONT).getOrThrow()
        for (ref in listOf(orphan, retained)) File(context.filesDir, "media-index/${ref.id}.json").setLastModified(1)
        File(context.noBackupFilesDir, "media-index/${person.id}.json").setLastModified(1)
        assertEquals(1, media.pruneGarmentDrafts(setOf(retained.id), 1000).getOrThrow())
        assertTrue(media.read(orphan).isFailure); assertTrue(media.read(child).isFailure)
        assertTrue(media.read(retained).isSuccess); assertTrue(media.read(recent).isSuccess); assertTrue(media.read(person).isSuccess)
    }
    @Test fun `recovery preserves ancestors of a saved derivative`() = runBlocking {
        val original = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val child = media.derive(original, DerivativePreset.THUMBNAIL).getOrThrow()
        File(context.filesDir, "media-index/${original.id}.json").setLastModified(1)
        assertEquals(0, media.pruneGarmentDrafts(setOf(child.id), 1000).getOrThrow())
        assertTrue(media.read(original).isSuccess); assertTrue(media.read(child).isSuccess)
    }
    @Test fun `crash leftovers expire but saved missing index recent private and unknown files survive`() = runBlocking {
        val stranded = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val saved = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val recent = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val person = media.importImage(source().path, MediaScope.PERSON, owner, ImageView.FRONT).getOrThrow()
        val files = listOf(stranded, saved, recent, person).associateWith { File(media.displayPath(it)!!) }
        for (ref in files.keys) {
            val root = if (ref.scope == MediaScope.GARMENT) context.filesDir else context.noBackupFilesDir
            File(root, "media-index/${ref.id}.json").delete()
            if (ref != recent) files.getValue(ref).setLastModified(1)
        }
        val partial = File(files.getValue(stranded).parentFile, "${UUID.randomUUID()}.jpg.new").apply { writeText("partial"); setLastModified(1) }
        val unknown = File(files.getValue(stranded).parentFile, "do-not-delete.txt").apply { writeText("unmanaged"); setLastModified(1) }
        assertEquals(2, media.pruneGarmentDrafts(setOf(saved.id), 1000).getOrThrow())
        assertFalse(files.getValue(stranded).exists()); assertFalse(partial.exists())
        assertTrue(files.getValue(saved).exists()); assertTrue(files.getValue(recent).exists())
        assertTrue(files.getValue(person).exists()); assertTrue(unknown.exists())
    }
    @Test fun `bad cleanup journal does not prevent unrelated pending deletion recovery`() = runBlocking {
        val ref = media.importImage(source().path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val file = File(media.displayPath(ref)!!)
        val journal = File(context.filesDir, "media-cleanup").apply { mkdirs() }
        File(journal, "broken.json").writeText("interrupted journal")
        File(journal, "${ref.id}.json").writeText(WardrobeCodec.encodeMedia(ref))
        assertTrue(media.retryCleanup().isFailure)
        assertFalse(file.exists()); assertTrue(File(journal, "broken.json").exists())
        assertFalse(File(journal, "${ref.id}.json").exists())
    }
}

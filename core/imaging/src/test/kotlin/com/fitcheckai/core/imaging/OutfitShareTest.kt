package com.fitcheckai.core.imaging

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.fitcheckai.core.model.wardrobe.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutfitShareTest {
    @Test fun `collage URI reads image and cancel cleanup never modifies source`() = runBlocking {
        // AndroidX 1.15 FileProvider checks root + '/' while the host JVM uses '\\' on Windows.
        // The same boundary is exercised on Android by OutfitShareDeviceTest, not mocked here.
        assumeTrue("FileProvider uses Android/POSIX paths; run connectedDebugAndroidTest on Windows", File.separatorChar == '/')
        val context = RuntimeEnvironment.getApplication()
        val source = File.createTempFile("share-test", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val media = LocalMediaStore(context)
        val ref = media.importImage(source.path, MediaScope.GARMENT, UUID.randomUUID().toString(), ImageView.FRONT).getOrThrow()
        val before = media.read(ref).getOrThrow()
        val share = OutfitShare(context, media)
        val intent = share.prepare("Bộ thử", listOf(ref)).getOrThrow()
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertThrows(IllegalArgumentException::class.java) {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.wardrobe.share", File(media.displayPath(ref)!!))
        }
        val exported = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        assertNotNull(exported); assertTrue(exported.width <= 2048 && exported.height <= 2048); exported.recycle()
        assertArrayEquals(before, media.read(ref).getOrThrow())
        assertTrue(share.prepare("Private", listOf(ref.copy(scope = MediaScope.PERSON))).isFailure)
        assertTrue(share.prepare("Missing", listOf(ref.copy(id = UUID.randomUUID().toString()))).isFailure)
        assertTrue(share.cleanup(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000) >= 1)
        assertArrayEquals(before, media.read(ref).getOrThrow())
    }
}

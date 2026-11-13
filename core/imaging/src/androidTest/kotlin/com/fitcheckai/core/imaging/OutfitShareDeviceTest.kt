package com.fitcheckai.core.imaging

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.model.wardrobe.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutfitShareDeviceTest {
    @Test fun collageUsesReadableNarrowUriAndKeepsOriginalOnCleanup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("share-device", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val media = LocalMediaStore(context)
        val ref = media.importImage(source.path, MediaScope.GARMENT, UUID.randomUUID().toString(), ImageView.FRONT).getOrThrow()
        try {
            val before = media.read(ref).getOrThrow()
            val share = OutfitShare(context, media)
            val intent = share.prepare("Bộ thử", listOf(ref)).getOrThrow()
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/jpeg", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, "${context.packageName}.wardrobe.share", File(media.displayPath(ref)!!))
            }
            val exported = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            assertNotNull(exported); assertTrue(exported.width <= 2048 && exported.height <= 2048); exported.recycle()
            assertTrue(share.prepare("Private", listOf(ref.copy(scope = MediaScope.PERSON))).isFailure)
            assertTrue(share.prepare("Missing", listOf(ref.copy(id = UUID.randomUUID().toString()))).isFailure)
            assertTrue(share.cleanup(System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000) >= 1)
            assertArrayEquals(before, media.read(ref).getOrThrow())
        } finally { media.remove(ref); source.delete() }
    }
}

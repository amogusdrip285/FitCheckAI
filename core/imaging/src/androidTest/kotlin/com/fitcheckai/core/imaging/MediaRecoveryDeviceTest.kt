package com.fitcheckai.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import android.system.Os
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
class MediaRecoveryDeviceTest {
    @Test fun unindexedInterruptedWriteRecoveryHonorsSavedRefsAndSymlinkBoundaries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val media = LocalMediaStore(context)
        val source = File.createTempFile("recovery", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val owner = UUID.randomUUID().toString()
        val expired = media.importImage(source.path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val retained = media.importImage(source.path, MediaScope.GARMENT, owner, ImageView.FRONT).getOrThrow()
        val person = media.importImage(source.path, MediaScope.PERSON, owner, ImageView.FRONT).getOrThrow()
        val files = listOf(expired, retained, person).associateWith { File(media.displayPath(it)!!) }
        val outside = File(context.cacheDir, "outside-${UUID.randomUUID()}").apply { mkdirs() }
        val outsideFile = File(outside, "${UUID.randomUUID()}.jpg").apply { writeText("do not delete"); setLastModified(1) }
        val link = File(context.filesDir, "media/GARMENT/${UUID.randomUUID()}")
        try {
            for ((ref, file) in files) {
                File(if (ref.scope == MediaScope.PERSON) context.noBackupFilesDir else context.filesDir, "media-index/${ref.id}.json").delete()
                assertTrue(file.setLastModified(1))
            }
            Os.symlink(outside.path, link.path)
            assertEquals(1, media.pruneGarmentDrafts(setOf(retained.id), 1000).getOrThrow())
            assertFalse(files.getValue(expired).exists())
            assertTrue(files.getValue(retained).exists()); assertTrue(files.getValue(person).exists()); assertTrue(outsideFile.exists())
        } finally {
            link.delete(); outsideFile.delete(); outside.delete(); source.delete(); files.values.forEach { it.delete() }
        }
    }
}

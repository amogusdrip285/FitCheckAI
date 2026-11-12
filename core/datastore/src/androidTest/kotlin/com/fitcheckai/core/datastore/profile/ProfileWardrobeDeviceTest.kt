package com.fitcheckai.core.datastore.profile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.database.AppDatabase
import com.fitcheckai.core.database.wardrobe.RoomWardrobeRepository
import com.fitcheckai.core.imaging.LocalMediaStore
import com.fitcheckai.core.model.profile.ProfileInvalidationReason
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.wardrobe.ClothingItem
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the isolated library test package and actual Android SQLite, files and MediaStore code. */
@RunWith(AndroidJUnit4::class)
class ProfileWardrobeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner = UUID.randomUUID().toString()
    private val dbName = "int001-$owner.db"
    private val profileDir = File(context.noBackupFilesDir, "int001-$owner")
    private var database: AppDatabase? = null
    private lateinit var media: LocalMediaStore
    private lateinit var wardrobe: RoomWardrobeRepository
    private lateinit var profiles: FileProfileStorage
    private val testRefs = mutableListOf<MediaRef>()

    private fun reopen() {
        database?.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        database = db
        media = LocalMediaStore(context)
        wardrobe = RoomWardrobeRepository(db, media)
        profiles = FileProfileStorage(profileDir, media, Dispatchers.IO)
    }

    @After fun cleanup() = runBlocking {
        database?.close()
        if (::media.isInitialized) testRefs.forEach { media.remove(it) }
        context.deleteDatabase(dbName)
        File(profileDir, "profile.json").delete()
        File(profileDir, "profile.json.tmp").delete()
        profileDir.delete()
        Unit
    }

    @Test fun pairedGarmentAndProfileSurviveReopenAndOnlyPersonMediaIsDeleted() = runBlocking {
        reopen()
        // Same owner ID on both sides makes the test sensitive to a scope mix-up.
        val garmentFront = importPhoto(MediaScope.GARMENT, ImageView.FRONT, Color.RED)
        val garmentBack = importPhoto(MediaScope.GARMENT, ImageView.BACK, Color.BLUE)
        val item = wardrobe.upsert(ClothingItem(owner, "INT-001 garment", ImagePair(garmentFront, garmentBack))).getOrThrow()
        val garmentBytes = item.images.refs().associateWith { media.read(it).getOrThrow() }
        var profile = profiles.save(UserProfile(owner, createdAt = 1, heightCm = 170.0, onboardingCompleted = true), null).getOrThrow()
        assertTrue(profiles.replaceBodyPhoto(ImageView.FRONT, garmentFront, profile.revision).isFailure)
        assertNull(profile.bodyImages.front)
        val front = importPhoto(MediaScope.PERSON, ImageView.FRONT, Color.GREEN)
        val back = importPhoto(MediaScope.PERSON, ImageView.BACK, Color.YELLOW)
        val personBytes = listOf(front, back).associateWith { media.read(it).getOrThrow() }
        profile = profiles.replaceBodyPhoto(ImageView.FRONT, front, profile.revision).getOrThrow()
        profile = profiles.replaceBodyPhoto(ImageView.BACK, back, profile.revision).getOrThrow()
        assertTrue(media.displayPath(front)!!.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        assertTrue(media.displayPath(garmentFront)!!.startsWith(context.filesDir.canonicalPath + File.separator))

        repeat(3) {
            reopen()
            assertEquals(profile, readProfile())
            assertEquals(item, wardrobe.get(item.id).getOrThrow())
            (personBytes + garmentBytes).forEach { (ref, bytes) -> assertArrayEquals(bytes, media.read(ref).getOrThrow()) }
        }

        val removal = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { profiles.invalidations.first() } }
        profile = profiles.replaceBodyPhoto(ImageView.BACK, null, profile.revision).getOrThrow()
        assertEquals(ProfileInvalidationReason.BODY_PHOTO_REMOVED, removal.await().reason)
        assertNull(profile.bodyImages.back)
        assertTrue(media.read(back).isFailure)
        assertArrayEquals(personBytes.getValue(front), media.read(front).getOrThrow())
        val clearing = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { profiles.invalidations.first() } }
        assertFalse(profiles.clearProfile().getOrThrow().cleanupPending)
        assertEquals(setOf(front.id), clearing.await().removedMediaIds)

        reopen()
        assertNull(readProfile())
        assertTrue(media.read(front).isFailure)
        assertEquals(item, wardrobe.get(item.id).getOrThrow())
        garmentBytes.forEach { (ref, bytes) -> assertArrayEquals(bytes, media.read(ref).getOrThrow()) }
    }

    private suspend fun readProfile(): UserProfile? = when (val state = profiles.observeProfile().first { it !is LoadState.Loading }) {
        is LoadState.Ready -> state.value
        is LoadState.Failed -> throw state.error
        LoadState.Loading -> error("Profile did not load")
    }

    private suspend fun importPhoto(scope: MediaScope, view: ImageView, color: Int): MediaRef {
        val input = File.createTempFile("int001-photo-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return try {
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            media.importImage(input.path, scope, owner, view).getOrThrow().also { testRefs += it }
        } finally {
            bitmap.recycle()
            input.delete()
        }
    }
}

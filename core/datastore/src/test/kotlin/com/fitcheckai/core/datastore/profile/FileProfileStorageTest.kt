package com.fitcheckai.core.datastore.profile

import com.fitcheckai.core.model.profile.ProfileInvalidation
import com.fitcheckai.core.model.profile.ProfileInvalidationReason
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.profile.fakes.FIXTURE_PROFILE_ID
import com.fitcheckai.core.model.profile.fakes.fullProfile
import com.fitcheckai.core.model.profile.fakes.minimalProfile
import com.fitcheckai.core.model.profile.fakes.personMedia
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.FakeMediaStore
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.fixtureMedia
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileProfileStorageTest {
    @get:Rule val temp = TemporaryFolder()
    private val media = FakeMediaStore()
    private var now = 1_000L

    private fun TestScope.storage(dir: File = File(temp.root, "profile")) =
        FileProfileStorage(dir, media, StandardTestDispatcher(testScheduler)) { now }

    private suspend fun FileProfileStorage.current(): UserProfile? =
        (observeProfile().first { it !is LoadState.Loading } as LoadState.Ready).value

    private fun Result<*>.code() = (exceptionOrNull() as WardrobeException).code

    @Test
    fun `empty storage is ready with null profile`() = runTest {
        assertNull(storage().current())
    }

    @Test
    fun `save then reopen keeps nulls values and revision`() = runTest {
        val first = storage()
        val saved = first.save(fullProfile().copy(weightKg = null, bodyImages = ImagePair()), null).getOrThrow()
        assertEquals(1L, saved.revision)
        assertEquals(now, saved.createdAt)

        val reopened = storage().current()
        assertEquals(saved, reopened)
        assertNull(reopened?.weightKg)
        assertEquals(168.0, reopened?.heightCm)
        assertTrue(reopened?.onboardingCompleted == true)
    }

    @Test
    fun `save ignores body images and enforces revision`() = runTest {
        val s = storage()
        val saved = s.save(fullProfile(), null).getOrThrow()
        assertEquals(ImagePair(), saved.bodyImages)
        assertEquals(ErrorCode.CONFLICT, s.save(saved, expectedRevision = null).code())
        assertEquals(ErrorCode.CONFLICT, s.save(saved, expectedRevision = 9).code())
        now = 2_000
        val updated = s.save(saved.copy(heightCm = 170.0), saved.revision).getOrThrow()
        assertEquals(2L, updated.revision)
        assertEquals(saved.createdAt, updated.createdAt)
        assertEquals(2_000L, updated.updatedAt)
    }

    @Test
    fun `invalid profile is rejected and nothing is written`() = runTest {
        val s = storage()
        assertEquals(ErrorCode.INVALID_REQUEST, s.save(minimalProfile().copy(heightCm = 0.0), null).code())
        assertNull(storage().current())
    }

    @Test
    fun `replace body photo removes old file after commit and emits invalidation`() = runTest(UnconfinedTestDispatcher()) {
        val s = storage()
        val profile = s.save(minimalProfile(), null).getOrThrow()
        val front = personMedia(ImageView.FRONT)
        val withFront = s.replaceBodyPhoto(ImageView.FRONT, front, profile.revision).getOrThrow()
        assertEquals(front, withFront.bodyImages.front)
        assertNull(withFront.bodyImages.back) // never cloned from front
        assertTrue(media.removed.isEmpty())

        val event = async { s.invalidations.first() }
        val replacement = personMedia(ImageView.FRONT).copy(id = fixtureId("new-front"), contentHash = "b".repeat(64))
        val replaced = s.replaceBodyPhoto(ImageView.FRONT, replacement, withFront.revision).getOrThrow()
        assertEquals(listOf(front.id), media.removed)
        assertEquals(ProfileInvalidation(FIXTURE_PROFILE_ID, replaced.revision, setOf(front.id),
            ProfileInvalidationReason.BODY_PHOTO_REPLACED), event.await())

        val removedEvent = async { s.invalidations.first() }
        val cleared = s.replaceBodyPhoto(ImageView.FRONT, null, replaced.revision).getOrThrow()
        assertNull(cleared.bodyImages.front)
        assertEquals(ProfileInvalidationReason.BODY_PHOTO_REMOVED, removedEvent.await().reason)
        assertNull(storage().current()?.bodyImages?.front) // does not resurrect after reopen
    }

    @Test
    fun `body photo must be person scope owned by profile and match view`() = runTest {
        val s = storage()
        val profile = s.save(minimalProfile(), null).getOrThrow()
        val garment = fixtureMedia(FIXTURE_PROFILE_ID, ImageView.FRONT)
        assertEquals(ErrorCode.INVALID_REQUEST, s.replaceBodyPhoto(ImageView.FRONT, garment, profile.revision).code())
        assertEquals(ErrorCode.INVALID_REQUEST, s.replaceBodyPhoto(ImageView.BACK, personMedia(ImageView.FRONT), profile.revision).code())
        val foreign = personMedia(ImageView.FRONT, owner = fixtureId("other"))
        assertEquals(ErrorCode.INVALID_REQUEST, s.replaceBodyPhoto(ImageView.FRONT, foreign, profile.revision).code())
        assertEquals(ErrorCode.CONFLICT, s.replaceBodyPhoto(ImageView.FRONT, personMedia(ImageView.FRONT), 7).code())
    }

    @Test
    fun `photo requires an existing profile`() = runTest {
        assertEquals(ErrorCode.NEEDS_INPUT, storage().replaceBodyPhoto(ImageView.FRONT, personMedia(ImageView.FRONT), 1).code())
    }

    @Test
    fun `same photo is a no-op`() = runTest {
        val s = storage()
        val p = s.save(minimalProfile(), null).getOrThrow()
        val front = personMedia(ImageView.FRONT)
        val withFront = s.replaceBodyPhoto(ImageView.FRONT, front, p.revision).getOrThrow()
        assertEquals(withFront, s.replaceBodyPhoto(ImageView.FRONT, front, withFront.revision).getOrThrow())
        assertTrue(media.removed.isEmpty())
    }

    @Test
    fun `clear profile deletes metadata and photos and emits invalidation`() = runTest(UnconfinedTestDispatcher()) {
        val s = storage()
        var p = s.save(minimalProfile(), null).getOrThrow()
        p = s.replaceBodyPhoto(ImageView.FRONT, personMedia(ImageView.FRONT), p.revision).getOrThrow()
        p = s.replaceBodyPhoto(ImageView.BACK, personMedia(ImageView.BACK), p.revision).getOrThrow()
        val event = async { s.invalidations.first() }

        val receipt = s.clearProfile().getOrThrow()

        assertFalse(receipt.cleanupPending)
        assertEquals(p.bodyImages.refs().map { it.id }.toSet(), media.removed.toSet())
        assertEquals(ProfileInvalidationReason.PROFILE_CLEARED, event.await().reason)
        assertNull(s.current())
        assertNull(storage().current())
        assertEquals(ErrorCode.STALE_INPUT, s.clearProfile().code())
    }

    @Test
    fun `clear reports pending cleanup when media removal fails`() = runTest {
        val s = storage()
        val p = s.save(minimalProfile(), null).getOrThrow()
        s.replaceBodyPhoto(ImageView.FRONT, personMedia(ImageView.FRONT), p.revision).getOrThrow()
        media.failNext = ErrorCode.IO
        assertTrue(s.clearProfile().getOrThrow().cleanupPending)
        assertNull(storage().current())
    }

    @Test
    fun `corrupt file surfaces failure without wiping and clear recovers`() = runTest {
        val dir = File(temp.root, "profile").apply { mkdirs() }
        val file = File(dir, "profile.json").apply { writeText("{not json") }
        val s = storage(dir)

        val state = s.observeProfile().first { it !is LoadState.Loading }
        assertTrue(state is LoadState.Failed)
        assertTrue(file.exists())
        assertEquals(ErrorCode.IO, s.save(minimalProfile(), null).code())
        assertEquals("{not json", file.readText())

        assertTrue(s.clearProfile().getOrThrow().cleanupPending)
        assertFalse(file.exists())
        assertNotNullProfileAfterSave(s)
    }

    private suspend fun assertNotNullProfileAfterSave(s: FileProfileStorage) {
        s.save(minimalProfile(), null).getOrThrow()
        assertEquals(FIXTURE_PROFILE_ID, s.current()?.id)
    }

    @Test
    fun `failed read is retried on next access instead of sticking`() = runTest {
        val dir = File(temp.root, "profile").apply { mkdirs() }
        val file = File(dir, "profile.json").apply { writeText("{broken") }
        val s = storage(dir)
        assertTrue(s.observeProfile().first { it !is LoadState.Loading } is LoadState.Failed)

        file.writeText(com.fitcheckai.core.datastore.profile.ProfileCodec.encode(minimalProfile())) // e.g. transient IO resolved
        assertEquals(FIXTURE_PROFILE_ID, s.current()?.id)
        assertEquals(2L, s.save(minimalProfile(), 1).getOrThrow().revision)
    }

    @Test
    fun `no temp file left after successful write`() = runTest {
        val dir = File(temp.root, "profile")
        storage(dir).save(minimalProfile(), null).getOrThrow()
        assertEquals(listOf("profile.json"), dir.list()?.toList())
    }
}

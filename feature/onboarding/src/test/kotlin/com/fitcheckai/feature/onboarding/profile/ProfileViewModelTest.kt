package com.fitcheckai.feature.onboarding.profile

import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.profile.fakes.FakeProfileStorage
import com.fitcheckai.core.model.profile.fakes.fullProfile
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.fakes.FakeMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val media = FakeMediaStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun FakeProfileStorage.current() = (observeProfile().first() as LoadState.Ready).value

    @Test
    fun `skip on a new user completes onboarding with every field null`() = runTest {
        val storage = FakeProfileStorage()
        val vm = ProfileViewModel(storage, media)
        var done = false

        vm.skip { done = true }

        assertTrue(done)
        val saved = requireNotNull(storage.current())
        assertTrue(saved.onboardingCompleted)
        assertNull(saved.heightCm); assertNull(saved.weightKg)
        assertTrue(saved.stylePreferences.isEmpty())
    }

    @Test
    fun `save maps empty fields to null and keeps entered values`() = runTest {
        val storage = FakeProfileStorage()
        val vm = ProfileViewModel(storage, media)
        vm.setHeight("168,5")
        vm.toggleStyle("Minimal")
        vm.toggleCategory(Category.DRESS)

        vm.save {}

        val saved = requireNotNull(storage.current())
        assertEquals(168.5, saved.heightCm)
        assertNull(saved.weightKg)
        assertEquals(listOf("Minimal"), saved.stylePreferences)
        assertEquals(setOf(Category.DRESS), saved.preferredCategories)
    }

    @Test
    fun `invalid numbers stay on screen and nothing is written`() = runTest {
        val storage = FakeProfileStorage()
        val vm = ProfileViewModel(storage, media)
        vm.setHeight("0")
        vm.setWeight("abc")
        var done = false

        vm.save { done = true }

        assertFalse(done)
        assertEquals(setOf(FormField.HEIGHT, FormField.WEIGHT), vm.state.value.fieldErrors.keys)
        assertEquals(0, storage.writeCount)
        vm.setHeight("170")
        assertTrue(vm.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun `existing profile prefills the form and save failure shows message`() = runTest {
        val storage = FakeProfileStorage(fullProfile())
        val vm = ProfileViewModel(storage, media)
        assertEquals("168", vm.state.value.form.heightText)
        assertEquals("58", vm.state.value.form.weightText)
        assertEquals(2, vm.state.value.photos.size)

        storage.failNext = ErrorCode.IO
        var done = false
        vm.save { done = true }

        assertFalse(done)
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `import photo creates profile if needed and stores only that view`() = runTest {
        val storage = FakeProfileStorage()
        val vm = ProfileViewModel(storage, media)

        vm.importPhoto(ImageView.FRONT, "content://picked/front")

        val saved = requireNotNull(storage.current())
        assertNotNull(saved.bodyImages.front)
        assertNull(saved.bodyImages.back)
        assertEquals(setOf(ImageView.FRONT), vm.state.value.photos.keys)
        assertFalse(saved.onboardingCompleted) // importing a photo is not finishing onboarding
    }

    @Test
    fun `failed import leaves profile unchanged`() = runTest {
        val storage = FakeProfileStorage(fullProfile())
        val vm = ProfileViewModel(storage, media)
        media.failNext = ErrorCode.PAYLOAD_TOO_LARGE

        vm.importPhoto(ImageView.BACK, "content://huge")

        assertEquals(fullProfile().bodyImages, storage.current()?.bodyImages)
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun `storage rejection of an imported photo cleans the new file`() = runTest {
        val storage = FakeProfileStorage(fullProfile())
        val vm = ProfileViewModel(storage, media)
        storage.failNext = ErrorCode.IO

        vm.importPhoto(ImageView.FRONT, "content://new")

        assertEquals(1, media.removed.size)
        assertEquals(fullProfile().bodyImages.front, storage.current()?.bodyImages?.front)
    }

    @Test
    fun `remove photo and reset clear local person data`() = runTest {
        val storage = FakeProfileStorage(fullProfile())
        val vm = ProfileViewModel(storage, media)

        vm.removePhoto(ImageView.BACK)
        assertNull(storage.current()?.bodyImages?.back)
        assertEquals(setOf(ImageView.FRONT), vm.state.value.photos.keys)

        vm.reset()
        assertNull(storage.current())
        assertFalse(vm.state.value.hasProfile)
        assertEquals(ProfileForm(), vm.state.value.form)
    }

    @Test
    fun `reset reports pending photo cleanup`() = runTest {
        val backing = FakeProfileStorage(fullProfile())
        val storage = object : ProfileStorage by backing {
            override suspend fun clearProfile() = backing.clearProfile().map { it.copy(cleanupPending = true) }
        }
        val vm = ProfileViewModel(storage, media)

        vm.reset()

        assertNull(backing.current())
        assertEquals("Đã xóa hồ sơ. Một số ảnh có thể vẫn còn trên máy.", vm.state.value.message)
    }

    @Test
    fun `unreadable storage exposes failure state`() = runTest {
        val storage = FakeProfileStorage()
        storage.emitLoadFailure()
        val vm = ProfileViewModel(storage, media)
        assertTrue(vm.state.value.load is ProfileLoad.Failed)
    }
}

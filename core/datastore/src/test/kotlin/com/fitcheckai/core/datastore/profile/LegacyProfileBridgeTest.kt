package com.fitcheckai.core.datastore.profile

import com.fitcheckai.core.model.StyleProfile
import com.fitcheckai.core.model.profile.fakes.FakeProfileStorage
import com.fitcheckai.core.model.profile.fakes.fullProfile
import com.fitcheckai.core.model.profile.fakes.minimalProfile
import com.fitcheckai.core.model.wardrobe.LoadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyProfileBridgeTest {
    @Test
    fun `no profile or incomplete onboarding maps to null`() = runTest {
        assertNull(LegacyProfileBridge(FakeProfileStorage()).observe().first())
        assertNull(LegacyProfileBridge(FakeProfileStorage(minimalProfile().copy(onboardingCompleted = false))).observe().first())
    }

    @Test
    fun `completed profile maps styles and city`() = runTest {
        val legacy = LegacyProfileBridge(FakeProfileStorage(fullProfile())).observe().first()
        assertEquals(StyleProfile(styles = setOf("Minimal", "Smart casual"), city = "hanoi"), legacy)
    }

    @Test
    fun `v1 save creates or updates the single v2 profile`() = runTest {
        val storage = FakeProfileStorage()
        val bridge = LegacyProfileBridge(storage)
        bridge.save(StyleProfile(styles = setOf("Vintage"), city = " ")).getOrThrow()
        val created = requireNotNull((storage.observeProfile().first() as LoadState.Ready).value)
        assertEquals(listOf("Vintage"), created.stylePreferences)
        assertNull(created.cityKey)
        assertEquals(true, created.onboardingCompleted)

        bridge.save(StyleProfile(styles = setOf("Minimal"))).getOrThrow()
        val updated = requireNotNull((storage.observeProfile().first() as LoadState.Ready).value)
        assertEquals(created.id, updated.id)
        assertEquals(2L, updated.revision)
    }
}

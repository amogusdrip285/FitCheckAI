package com.fitcheckai.core.model

import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.core.model.profile.ProfileInvalidationReason
import com.fitcheckai.core.model.profile.fakes.FIXTURE_PROFILE_ID
import com.fitcheckai.core.model.profile.fakes.FakeProfileStorage
import com.fitcheckai.core.model.profile.fakes.fullProfile
import com.fitcheckai.core.model.profile.fakes.minimalProfile
import com.fitcheckai.core.model.profile.fakes.missingBackProfile
import com.fitcheckai.core.model.profile.fakes.personMedia
import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.outfitSignature
import com.fitcheckai.core.model.tryon.TryOnCapability
import com.fitcheckai.core.model.tryon.TryOnDecision
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaScope
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.fixtureMedia
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractsV2DTest {
    @Test
    fun `profile fixtures validate including missing back`() {
        listOf(minimalProfile(), fullProfile(), missingBackProfile()).forEach { it.validate() }
        assertNull(missingBackProfile().bodyImages.back)
    }

    @Test
    fun `profile keeps null for skipped fields and rejects zero or out of range numbers`() {
        val minimal = minimalProfile()
        assertNull(minimal.heightCm); assertNull(minimal.weightKg)
        assertThrows(IllegalArgumentException::class.java) { minimal.copy(heightCm = 0.0).validate() }
        assertThrows(IllegalArgumentException::class.java) { minimal.copy(weightKg = Double.NaN).validate() }
        assertThrows(IllegalArgumentException::class.java) { minimal.copy(stylePreferences = List(6) { "s$it" }).validate() }
        assertThrows(IllegalArgumentException::class.java) { minimal.copy(preferredCategories = setOf(Category.UNKNOWN)).validate() }
    }

    @Test
    fun `body images must be PERSON scope owned by profile`() {
        val garment = fixtureMedia(FIXTURE_PROFILE_ID, ImageView.FRONT)
        assertEquals(MediaScope.GARMENT, garment.scope)
        assertThrows(IllegalArgumentException::class.java) {
            minimalProfile().copy(bodyImages = ImagePair(front = garment)).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            minimalProfile().copy(bodyImages = ImagePair(front = personMedia(ImageView.FRONT, owner = fixtureId("other")))).validate()
        }
    }

    @Test
    fun `projection never carries weight or body images`() {
        val projection = fullProfile().toProjection()
        assertEquals(168.0, projection.heightCm)
        assertEquals(listOf("Minimal", "Smart casual"), projection.stylePreferences)
        val fields = projection::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("weight", ignoreCase = true) || it.contains("image", ignoreCase = true) })
    }

    @Test
    fun `outfit signature is order independent`() {
        assertEquals(outfitSignature(listOf("b", "a", "c")), outfitSignature(listOf("c", "b", "a")))
    }

    @Test
    fun `outfit request validates count text and focus`() {
        val items = listOf(ItemSummary(fixtureId("top"), 1, ClothingAttributes(Category.TOP)),
            ItemSummary(fixtureId("bottom"), 1, ClothingAttributes(Category.BOTTOM)))
        val ok = OutfitRequest(fixtureId("req"), "s1", items, minimalProfile().toProjection(), Occasion.WORK)
        ok.validate()
        listOf(
            ok.copy(requestedCount = 0), ok.copy(requestedCount = 6), ok.copy(freeText = "x".repeat(501)),
            ok.copy(items = emptyList()), ok.copy(focusItemId = fixtureId("gone")), ok.copy(items = items + items.first()),
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { bad.validate() } }
    }

    @Test
    fun `unknown or feasibility-only capability is never offered`() {
        assertFalse(TryOnCapability.UNAVAILABLE.canOffer(setOf(ImageView.FRONT)))
        val feasibleOnly = TryOnCapability(decision = TryOnDecision.GO_FRONT_ONLY, supportedViews = setOf(ImageView.FRONT))
        assertFalse(feasibleOnly.canOffer(setOf(ImageView.FRONT)))
        val verified = feasibleOnly.copy(runtimeVerifiedViews = setOf(ImageView.FRONT))
        assertTrue(verified.canOffer(setOf(ImageView.FRONT)))
        assertFalse(verified.canOffer(setOf(ImageView.FRONT, ImageView.BACK)))
        assertFalse(verified.canOffer(emptySet()))
    }

    @Test
    fun `try-on route encodes outfit and snapshot`() {
        val outfit = fixtureId("outfit")
        val snapshot = fixtureId("snapshot")
        assertEquals("tryon/$outfit?snapshotId=$snapshot", Routes.tryOn(outfit, snapshot))
    }

    @Test
    fun `fake storage enforces revisions and keeps body images out of save`() = runBlocking {
        val storage = FakeProfileStorage()
        val created = storage.save(minimalProfile().copy(bodyImages = fullProfile().bodyImages), null).getOrThrow()
        assertEquals(1L, created.revision)
        assertNull(created.bodyImages.front)
        val conflict = storage.save(created.copy(heightCm = 170.0), expectedRevision = 5).exceptionOrNull()
        assertEquals(ErrorCode.CONFLICT, (conflict as WardrobeException).code)
        storage.failNext = ErrorCode.IO
        assertEquals(ErrorCode.IO, (storage.save(created, 1).exceptionOrNull() as WardrobeException).code)
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun `fake storage emits invalidation when photo removed or profile cleared`() = runBlocking {
        val storage = FakeProfileStorage()
        val profile = storage.save(minimalProfile(), null).getOrThrow()
        val front = personMedia(ImageView.FRONT)
        val withPhoto = storage.replaceBodyPhoto(ImageView.FRONT, front, profile.revision).getOrThrow()
        val removed = async { storage.invalidations.first() }
        yield()
        storage.replaceBodyPhoto(ImageView.FRONT, null, withPhoto.revision).getOrThrow()
        val event = removed.await()
        assertEquals(setOf(front.id), event.removedMediaIds)
        assertEquals(ProfileInvalidationReason.BODY_PHOTO_REMOVED, event.reason)

        val cleared = async { storage.invalidations.first() }
        yield()
        storage.clearProfile().getOrThrow()
        assertEquals(ProfileInvalidationReason.PROFILE_CLEARED, cleared.await().reason)
        assertEquals(LoadState.Ready(null), storage.observeProfile().first())
    }
}

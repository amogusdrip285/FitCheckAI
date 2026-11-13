package com.fitcheckai.core.model.profile.fakes

import com.fitcheckai.core.model.profile.ProfileInvalidation
import com.fitcheckai.core.model.profile.ProfileInvalidationReason
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.DeletionReceipt
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaScope
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.fakes.fixtureId
import com.fitcheckai.core.model.wardrobe.fakes.fixtureMedia
import com.fitcheckai.core.model.wardrobe.wardrobeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** D-006 fixtures. Test-only; never bound in production Hilt graphs. */
val FIXTURE_PROFILE_ID: String = fixtureId("profile")

fun personMedia(view: ImageView, owner: String = FIXTURE_PROFILE_ID): MediaRef =
    fixtureMedia(owner, view, "person-$owner-$view").let {
        it.copy(scope = MediaScope.PERSON, localRelativePath = "media/PERSON/$owner/${it.id}.jpg")
    }

/** Skipped everything: only the onboarding flag is set. */
fun minimalProfile() = UserProfile(id = FIXTURE_PROFILE_ID, createdAt = 100, onboardingCompleted = true)

fun fullProfile() = minimalProfile().copy(
    heightCm = 168.0, weightKg = 58.0, stylePreferences = listOf("Minimal", "Smart casual"),
    preferredCategories = setOf(Category.TOP, Category.DRESS), cityKey = "hanoi",
    bodyImages = ImagePair(personMedia(ImageView.FRONT), personMedia(ImageView.BACK)),
)

fun missingBackProfile() = fullProfile().copy(bodyImages = ImagePair(front = personMedia(ImageView.FRONT)))

class FakeProfileStorage(initial: UserProfile? = null) : ProfileStorage {
    private val mutex = Mutex()
    private val state = MutableStateFlow<LoadState<UserProfile?>>(LoadState.Ready(initial))
    private val events = MutableSharedFlow<ProfileInvalidation>(extraBufferCapacity = 16)
    override val invalidations: Flow<ProfileInvalidation> = events

    /** Next write fails with this code (save-fail fixture). */
    var failNext: ErrorCode? = null
    var writeCount = 0
        private set

    override fun observeProfile(): Flow<LoadState<UserProfile?>> = state

    fun emitLoadFailure() { state.value = LoadState.Failed(WardrobeException(ErrorCode.IO, "DEV read failure")) }

    private fun current(): UserProfile? = (state.value as? LoadState.Ready)?.value
    private fun fail() { failNext?.let { failNext = null; throw WardrobeException(it, "DEV injected $it") } }
    private fun checkRevision(old: UserProfile?, expected: Long?) {
        if (old?.revision != expected) throw WardrobeException(ErrorCode.CONFLICT, "DEV conflict")
    }

    override suspend fun save(profile: UserProfile, expectedRevision: Long?) = wardrobeResult {
        mutex.withLock {
            fail()
            val old = current()
            checkRevision(old, expectedRevision)
            val next = profile.copy(
                id = old?.id ?: profile.id, revision = (old?.revision ?: 0) + 1, createdAt = old?.createdAt ?: profile.createdAt,
                updatedAt = maxOf(profile.updatedAt, old?.updatedAt ?: 0) + 1, bodyImages = old?.bodyImages ?: ImagePair(),
            )
            next.validate()
            writeCount++
            state.value = LoadState.Ready(next)
            next
        }
    }

    override suspend fun replaceBodyPhoto(view: ImageView, ref: MediaRef?, expectedRevision: Long) = wardrobeResult {
        mutex.withLock {
            fail()
            val old = current() ?: throw WardrobeException(ErrorCode.NEEDS_INPUT, "DEV no profile")
            checkRevision(old, expectedRevision)
            val next = old.copy(revision = old.revision + 1, updatedAt = old.updatedAt + 1, bodyImages = old.bodyImages.with(view, ref))
            next.validate()
            writeCount++
            state.value = LoadState.Ready(next)
            old.bodyImages[view]?.takeIf { it.id != ref?.id }?.let {
                val reason = if (ref == null) ProfileInvalidationReason.BODY_PHOTO_REMOVED else ProfileInvalidationReason.BODY_PHOTO_REPLACED
                events.tryEmit(ProfileInvalidation(old.id, next.revision, setOf(it.id), reason))
            }
            next
        }
    }

    override suspend fun clearProfile() = wardrobeResult {
        mutex.withLock {
            fail()
            val old = current() ?: throw WardrobeException(ErrorCode.STALE_INPUT, "DEV no profile")
            writeCount++
            state.value = LoadState.Ready(null)
            events.tryEmit(ProfileInvalidation(old.id, old.revision + 1, old.bodyImages.refs().map { it.id }.toSet(),
                ProfileInvalidationReason.PROFILE_CLEARED))
            DeletionReceipt(old.id, old.revision + 1, old.updatedAt + 1, cleanupPending = false)
        }
    }
}

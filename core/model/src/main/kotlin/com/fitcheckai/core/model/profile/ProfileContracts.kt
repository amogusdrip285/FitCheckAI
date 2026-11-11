package com.fitcheckai.core.model.profile

import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.DeletionReceipt
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaScope
import com.fitcheckai.core.model.wardrobe.requireUuid
import kotlinx.coroutines.flow.Flow

// CT-04 — OWNER: Track D. Consumers (B projection, C try-on inputs) depend on these types only.

const val MAX_STYLE_PREFERENCES = 5
const val MAX_STYLE_LENGTH = 32
val HEIGHT_CM_RANGE = 50.0..250.0
val WEIGHT_KG_RANGE = 20.0..300.0

/**
 * Every user-facing field is optional. `null` means "not provided" — never substitute 0.
 * Weight/body images are never inferred and never sent to recommendation by default.
 */
data class UserProfile(
    val id: String,
    val revision: Long = 1,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val stylePreferences: List<String> = emptyList(),
    val preferredCategories: Set<Category> = emptySet(),
    val cityKey: String? = null,
    val bodyImages: ImagePair = ImagePair(),
    val onboardingCompleted: Boolean = false,
) {
    fun validate() {
        requireUuid(id)
        require(revision >= 1 && createdAt >= 0 && updatedAt >= createdAt)
        require(heightCm == null || (heightCm.isFinite() && heightCm in HEIGHT_CM_RANGE)) { "Chiều cao cần trong khoảng 50–250 cm." }
        require(weightKg == null || (weightKg.isFinite() && weightKg in WEIGHT_KG_RANGE)) { "Cân nặng cần trong khoảng 20–300 kg." }
        require(stylePreferences.size <= MAX_STYLE_PREFERENCES && stylePreferences.distinct().size == stylePreferences.size &&
            stylePreferences.all { it.isNotBlank() && it.length <= MAX_STYLE_LENGTH }) { "Chọn tối đa 5 phong cách, mỗi phong cách 32 ký tự." }
        require(Category.UNKNOWN !in preferredCategories)
        require(cityKey == null || cityKey.isNotBlank() && cityKey.length <= 64)
        bodyImages.validate(id, requireFront = false, scope = MediaScope.PERSON)
    }

    /** Default recommendation projection: no weight, no body images, no profile UUID. */
    fun toProjection(): ProfileProjection = ProfileProjection(heightCm, stylePreferences, preferredCategories)
}

data class ProfileProjection(
    val heightCm: Double? = null,
    val stylePreferences: List<String> = emptyList(),
    val preferredCategories: Set<Category> = emptySet(),
)

enum class ProfileInvalidationReason { BODY_PHOTO_REPLACED, BODY_PHOTO_REMOVED, PROFILE_CLEARED }

/** Emitted after a committed change that must invalidate try-on jobs built from removed person media. */
data class ProfileInvalidation(
    val profileId: String,
    val revision: Long,
    val removedMediaIds: Set<String>,
    val reason: ProfileInvalidationReason,
)

/** OWNER: Track D. Saving never performs network calls. */
interface ProfileStorage {
    /** Ready(null) = no profile yet; Failed = unreadable storage (never silently wiped). */
    fun observeProfile(): Flow<LoadState<UserProfile?>>
    val invalidations: Flow<ProfileInvalidation>

    /** [expectedRevision] null only when creating. Stale revision → CONFLICT. Body images are ignored here. */
    suspend fun save(profile: UserProfile, expectedRevision: Long?): Result<UserProfile>

    /** [ref] null removes the view. Ref must be PERSON scope owned by the profile and match [view]. */
    suspend fun replaceBodyPhoto(view: ImageView, ref: MediaRef?, expectedRevision: Long): Result<UserProfile>

    /** Deletes metadata and person photos; emits PROFILE_CLEARED. */
    suspend fun clearProfile(): Result<DeletionReceipt>
}

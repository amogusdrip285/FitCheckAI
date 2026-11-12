package com.fitcheckai.app.tryon

import com.fitcheckai.core.model.Category as TryOnCategory
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.tryon.TryOnSelection
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.feature.tryon.contract.ImagePair as TryOnImagePair
import com.fitcheckai.feature.tryon.contract.ImageView as TryOnImageView
import com.fitcheckai.feature.tryon.contract.MediaRef as TryOnMediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope as TryOnMediaScope
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnOutfitSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnPersonProfileSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnProfilePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// D-007 root wiring: maps CT-01/03/04 core types into :feature:tryon's contract types.
// The Try-On UI opens `localRelativePath` as a file, so refs carry the validated display path
// from MediaStore; refs MediaStore refuses to resolve are dropped rather than guessed.

internal fun MediaRef.toTryOn(media: MediaStore): TryOnMediaRef? {
    val path = media.displayPath(this) ?: return null
    return TryOnMediaRef(
        id = id,
        ownerEntityId = ownerEntityId,
        scope = TryOnMediaScope.valueOf(scope.name),
        view = TryOnImageView.valueOf(view.name),
        localRelativePath = path,
        mimeType = mimeType,
        width = width,
        height = height,
        byteCount = byteCount,
        contentHash = contentHash,
        revision = revision,
        derivedFromMediaId = derivedFromMediaId,
    )
}

internal fun ImagePair.toTryOn(media: MediaStore) =
    TryOnImagePair(front = front?.toTryOn(media), back = back?.toTryOn(media))

/** :feature:tryon reuses the v1 enum, which has no UNKNOWN; ACCESSORY is never a VTO category. */
private fun Category.toTryOn(): TryOnCategory =
    TryOnCategory.entries.firstOrNull { it.name == name } ?: TryOnCategory.ACCESSORY

internal fun TryOnSelection.toSnapshot(): TryOnOutfitSnapshot {
    val itemsById = items.associateBy { it.id }
    return TryOnOutfitSnapshot(
        outfitId = outfit.id,
        wardrobeSnapshotId = outfit.wardrobeSnapshotId,
        explanation = outfit.explanation,
        items = outfit.items.mapNotNull { ref ->
            val item = itemsById[ref.itemId] ?: return@mapNotNull null
            TryOnItemSummary(
                itemId = item.id,
                label = item.name,
                category = item.attributes.category.toTryOn(),
                colors = item.attributes.colors.map { it.name.lowercase() },
                revision = ref.revision,
            )
        },
    )
}

internal fun TryOnSelection.garmentImages(media: MediaStore): Map<String, TryOnImagePair> =
    items.associate { it.id to it.images.toTryOn(media) }

/** CT-04 → Track C port. Exposes only id/revision/body photos; never height, weight or style data. */
internal class ProfileStorageTryOnPort(
    private val profiles: ProfileStorage,
    private val media: MediaStore,
) : TryOnProfilePort {
    override fun observePersonProfile(): Flow<TryOnPersonProfileSnapshot?> =
        profiles.observeProfile().map { state ->
            val profile = (state as? LoadState.Ready)?.value ?: return@map null
            TryOnPersonProfileSnapshot(profile.id, profile.revision, profile.bodyImages.toTryOn(media))
        }
}

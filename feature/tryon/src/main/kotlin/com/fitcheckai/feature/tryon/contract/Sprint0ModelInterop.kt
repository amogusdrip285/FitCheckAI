package com.fitcheckai.feature.tryon.contract

import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Outfit

/**
 * Compatibility adapter bridging Sprint 0 `:core:model` (`Outfit`, `ClothingItem`) into v2
 * `TryOnOutfitSnapshot` while branch `lamb` remains based on Sprint 0 `HEAD` (`7d995a5`).
 *
 * ### Track D Integration Mapping (`origin/main` CT-03 / CT-04 / CT-06)
 * When integrating `:feature:tryon` into `origin/main` (`3666ef5`):
 * - **Navigation (`com.fitcheckai.core.model.nav.Routes`)**:
 *   - Route pattern: `Routes.TRYON_PATTERN` (`"tryon/{outfitId}?snapshotId={snapshotId}"`)
 *   - Route builder: `Routes.tryOn(outfitId, snapshotId)`
 *   - Register composable in `app/src/main/kotlin/com/fitcheckai/app/FitCheckApp.kt` calling `TryOnRoute(...)`.
 * - **Domain Contracts (`com.fitcheckai.core.model.tryon.*` & `wardrobe.*`)**:
 *   - `com.fitcheckai.core.model.wardrobe.MediaRef` <-> `com.fitcheckai.feature.tryon.contract.MediaRef`
 *   - `com.fitcheckai.core.model.wardrobe.ImagePair` <-> `com.fitcheckai.feature.tryon.contract.ImagePair`
 *   - `com.fitcheckai.core.model.wardrobe.ImageView` <-> `com.fitcheckai.feature.tryon.contract.ImageView`
 *   - `com.fitcheckai.core.model.tryon.TryOnDecision` <-> `com.fitcheckai.feature.tryon.contract.FeasibilityDecision`
 *   - `com.fitcheckai.core.model.tryon.TryOnCapability` <-> `com.fitcheckai.feature.tryon.contract.TryOnCapability`
 *     (Note: `:feature:tryon` extends capability with `providerId`, `supportedPresentationModes`, and `liveVerifiedCombos` for FOSTA v2).
 *   - `com.fitcheckai.core.model.tryon.VirtualTryOn` <-> `com.fitcheckai.feature.tryon.contract.VirtualTryOnService`
 * - **Profile & Media Stores (`core/data` & `core/imaging`)**:
 *   - Bind `ProfileStorage.observeProfile()` + `ProfileStorage.invalidations` to `TryOnProfilePort`.
 *   - Bind `LocalMediaStore` (`core/imaging`) to `TryOnMediaStorePort` (or keep `LocalDiskTryOnMediaStore` for isolated cache derivatives).
 *
 * Note: Does NOT read `ClothingItem.imagePath` as a provider URL or bypass `MediaStore`;
 * garment `ImagePair` references must always be supplied via `MediaRef` / `TryOnMediaStorePort`.
 */
object Sprint0ModelInterop {
    fun toOutfitSnapshot(
        outfit: Outfit,
        itemsById: Map<String, ClothingItem>,
        wardrobeSnapshotId: String = "sprint0-compat-${outfit.id}",
        revisionsById: Map<String, Long> = emptyMap(),
    ): TryOnOutfitSnapshot {
        val summaries = outfit.itemIds.mapNotNull { id ->
            val item = itemsById[id] ?: return@mapNotNull null
            TryOnItemSummary(
                itemId = item.id,
                label = item.attributes.subCategory,
                category = item.attributes.category,
                colors = item.attributes.colors,
                accentHex = item.attributes.colors.firstOrNull(),
                revision = revisionsById[id] ?: 1L,
            )
        }
        return TryOnOutfitSnapshot(
            outfitId = outfit.id,
            wardrobeSnapshotId = wardrobeSnapshotId,
            explanation = outfit.explanation,
            items = summaries,
        )
    }
}

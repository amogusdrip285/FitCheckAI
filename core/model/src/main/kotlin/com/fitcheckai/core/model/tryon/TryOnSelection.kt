package com.fitcheckai.core.model.tryon

import com.fitcheckai.core.model.recommendation.Outfit
import com.fitcheckai.core.model.wardrobe.ClothingItem
import java.util.concurrent.atomic.AtomicReference

// CT-03 handoff Today → Try-On. OWNER: Track D.
// Outfits are not persisted, so Today publishes the one the user picked (with the item
// snapshot it was validated against) and the Try-On destination looks it up by outfitId.
// In-memory only: after process death Try-On finds nothing and asks the user to pick again.

data class TryOnSelection(val outfit: Outfit, val items: List<ClothingItem>) {
    init {
        val ids = items.map { it.id }.toSet()
        require(outfit.items.all { it.itemId in ids }) { "Selection must carry every outfit item." }
    }
}

interface TryOnSelectionHandoff {
    fun publish(selection: TryOnSelection)

    /** Latest selection for [outfitId], or null if another outfit was picked since. */
    fun find(outfitId: String): TryOnSelection?
}

class InMemoryTryOnSelectionHandoff : TryOnSelectionHandoff {
    private val latest = AtomicReference<TryOnSelection?>(null)

    override fun publish(selection: TryOnSelection) = latest.set(selection)

    override fun find(outfitId: String): TryOnSelection? = latest.get()?.takeIf { it.outfit.id == outfitId }
}

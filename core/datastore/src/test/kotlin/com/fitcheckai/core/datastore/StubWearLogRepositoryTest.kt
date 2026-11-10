package com.fitcheckai.core.datastore

import com.fitcheckai.core.database.StubWardrobeRepository
import com.fitcheckai.core.database.seed.SeedData
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.OutfitSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class StubWearLogRepositoryTest {

    @Test
    fun `logWear records entry and marks items worn`() = runTest {
        val wardrobe = StubWardrobeRepository()
        val repo = StubWearLogRepository(wardrobe)
        val itemIds = SeedData.items.take(2).map { it.id }
        val outfit = Outfit(id = "o1", itemIds = itemIds, explanation = "", source = OutfitSource.RULES)
        val at = Instant.parse("2026-11-10T07:00:00Z")

        repo.logWear(outfit, at)

        assertEquals(1, repo.observeLogs().first().size)
        itemIds.forEach { assertEquals(1, wardrobe.getItem(it)?.wearCount) }
    }
}

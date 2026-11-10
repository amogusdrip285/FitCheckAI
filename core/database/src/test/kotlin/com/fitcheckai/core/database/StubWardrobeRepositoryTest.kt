package com.fitcheckai.core.database

import com.fitcheckai.core.database.seed.SeedData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StubWardrobeRepositoryTest {

    @Test
    fun `starts with seed items`() = runTest {
        val repo = StubWardrobeRepository()
        assertEquals(SeedData.items.size, repo.observeItems().first().size)
    }

    @Test
    fun `markWorn increments count and sets last worn`() = runTest {
        val repo = StubWardrobeRepository()
        val at = Instant.parse("2026-11-10T07:00:00Z")
        val id = SeedData.items.first().id

        repo.markWorn(listOf(id), at)

        val item = repo.getItem(id)
        assertEquals(1, item?.wearCount)
        assertEquals(at, item?.lastWornAt)
    }

    @Test
    fun `delete removes item`() = runTest {
        val repo = StubWardrobeRepository()
        val id = SeedData.items.first().id

        repo.delete(id)

        assertNull(repo.getItem(id))
    }
}

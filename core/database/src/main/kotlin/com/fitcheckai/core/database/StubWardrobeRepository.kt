package com.fitcheckai.core.database

import com.fitcheckai.core.database.seed.SeedData
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.WardrobeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID

/**
 * In-memory fixture kept for existing cross-track unit tests (including Track D).
 * Not bound in Hilt: the app always uses [WardrobeRepositoryImpl].
 */
class StubWardrobeRepository : WardrobeRepository {
    private val items = MutableStateFlow(SeedData.items)

    override fun observeItems(): Flow<List<ClothingItem>> = items.asStateFlow()

    override suspend fun getItem(id: String): ClothingItem? = items.value.firstOrNull { it.id == id }

    override suspend fun save(imagePath: String, attributes: ClothingAttributes): Result<ClothingItem> {
        val item = ClothingItem(
            id = UUID.randomUUID().toString(),
            imagePath = imagePath,
            attributes = attributes,
            createdAt = Instant.now(),
        )
        items.update { listOf(item) + it }
        return Result.success(item)
    }

    override suspend fun update(item: ClothingItem): Result<Unit> {
        items.update { list -> list.map { if (it.id == item.id) item else it } }
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        items.update { list -> list.filterNot { it.id == id } }
        return Result.success(Unit)
    }

    override suspend fun markWorn(itemIds: List<String>, at: Instant): Result<Unit> {
        val ids = itemIds.toSet()
        items.update { list ->
            list.map { if (it.id in ids) it.copy(wearCount = it.wearCount + 1, lastWornAt = at) else it }
        }
        return Result.success(Unit)
    }
}

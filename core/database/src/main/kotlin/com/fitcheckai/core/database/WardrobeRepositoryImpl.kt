package com.fitcheckai.core.database

import androidx.room.withTransaction
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.WardrobeRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class WardrobeRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val files: WardrobeFiles,
) : WardrobeRepository {
    private val dao get() = database.clothingItemDao()

    override fun observeItems(): Flow<List<ClothingItem>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getItem(id: String): ClothingItem? = dao.getById(id)?.toModel()

    override suspend fun save(imagePath: String, attributes: ClothingAttributes): Result<ClothingItem> = ioResult {
        val id = UUID.randomUUID().toString()
        val (storedPath, hash) = files.importImage(id, imagePath)
        try {
            val item = ClothingItem(id, storedPath, attributes, Instant.ofEpochMilli(System.currentTimeMillis()))
            dao.upsert(item.toEntity(hash))
            item
        } catch (error: Exception) {
            files.deleteOwned(storedPath)
            throw error
        }
    }

    override suspend fun update(item: ClothingItem): Result<Unit> = ioResult {
        database.withTransaction {
            val existing = requireNotNull(dao.getById(item.id)) { "Món đồ đã bị xóa." }
            require(existing.imagePath == item.imagePath) { "Thay ảnh bằng luồng thêm đồ." }
            val row = item.toEntity()
            // Do not overwrite wear counts/hash/creation time from a stale detail form.
            check(dao.updateAttributes(row.id, row.category, row.subCategory, row.colors, row.pattern,
                row.material, row.formality, row.seasons, row.styleTags) == 1)
        }
    }

    override suspend fun delete(id: String): Result<Unit> = ioResult {
        database.withTransaction {
            dao.getById(id)?.let { row ->
                dao.deleteById(id)
                files.deleteOwned(row.imagePath)
            }
        }
    }

    override suspend fun markWorn(itemIds: List<String>, at: Instant): Result<Unit> = ioResult {
        // SQLite's bind limit also applies to very large wardrobes.
        database.withTransaction {
            itemIds.distinct().chunked(900).forEach { dao.markWorn(it, at.toEpochMilli()) }
        }
    }

    private suspend fun <T> ioResult(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            // A short local write must finish together with its file operation if the screen closes.
            withContext(NonCancellable) { Result.success(block()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

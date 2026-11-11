package com.fitcheckai.core.database.wardrobe

import androidx.room.withTransaction
import com.fitcheckai.core.database.AppDatabase
import com.fitcheckai.core.model.wardrobe.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomWardrobeRepository @Inject constructor(private val db: AppDatabase, private val media: MediaStore) : WardrobeRepository {
    private val dao get() = db.wardrobeDao()
    private val mutations = Mutex()
    private val events = MutableSharedFlow<WardrobeInvalidation>(extraBufferCapacity = 64)
    override val invalidations = events.asSharedFlow()

    override fun observeItems(): Flow<LoadState<List<ClothingItem>>> = dao.observeActive()
        .map<List<WardrobeEntity>, LoadState<List<ClothingItem>>> { rows ->
            LoadState.Ready(rows.map { WardrobeCodec.decodeItem(requireNotNull(it.payload)) })
        }.onStart {
            emit(LoadState.Loading)
            mutations.withLock {
                cleanPending()
                val retained = dao.active().flatMap { WardrobeCodec.decodeItem(requireNotNull(it.payload)).images.refs() }.map { it.id }.toSet()
                media.pruneGarmentDrafts(retained, (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000).coerceAtLeast(0))
            }
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(LoadState.Failed(WardrobeException(ErrorCode.IO, "Không đọc được tủ đồ. Dữ liệu đã lưu vẫn được giữ lại.")))
        }

    override suspend fun getSnapshot(): Result<WardrobeSnapshot> = wardrobeResult {
        val items = dao.active().map { WardrobeCodec.decodeItem(requireNotNull(it.payload)) }
        val signature = items.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.revision}" }
        WardrobeSnapshot(UUID.nameUUIDFromBytes(signature.toByteArray()).toString(), items)
    }
    override suspend fun get(id: String): Result<ClothingItem?> = wardrobeResult {
        dao.get(id)?.takeIf { it.deletedAt == null }?.payload?.let(WardrobeCodec::decodeItem)
    }

    override suspend fun upsert(item: ClothingItem, expectedRevision: Long?): Result<ClothingItem> = mutations.withLock {
        val result = wardrobeResult {
            item.validate()
            checkRevision(dao.get(item.id), expectedRevision)
            item.images.refs().forEach { media.read(it).getOrThrow() }
            db.withTransaction {
                val old = dao.get(item.id)
                checkRevision(old, expectedRevision)
                val now = System.currentTimeMillis().coerceAtLeast(old?.updatedAt ?: item.createdAt)
                val saved = item.copy(revision = (old?.revision ?: 0) + 1,
                    createdAt = old?.createdAt ?: now, updatedAt = now, deletedAt = null)
                val oldItem = old?.payload?.let(WardrobeCodec::decodeItem)
                val retained = saved.images.refs().map { it.id }.toSet()
                oldItem?.images?.refs()?.filter { it.id !in retained }?.forEach {
                    dao.enqueue(MediaCleanupEntity(it.id, WardrobeCodec.encodeMedia(it)))
                }
                dao.put(saved.entity())
                saved
            }
        }
        if (result.isSuccess) {
            val saved = result.getOrThrow()
            events.emit(WardrobeInvalidation(saved.id, saved.revision, false))
            cleanPending()
        } else {
            // A failed save keeps the draft and its files so the user can retry or resolve a conflict.
            // Draft cancellation explicitly discards only refs not used by a saved item.
        }
        result
    }

    override suspend fun delete(id: String, expectedRevision: Long): Result<DeletionReceipt> = mutations.withLock { wardrobeResult {
        val tombstone = db.withTransaction {
            val old = dao.get(id) ?: throw WardrobeException(ErrorCode.STALE_INPUT, "Món đồ không còn trong tủ.")
            if (old.deletedAt != null) {
                if (expectedRevision !in setOf(old.revision, old.revision - 1)) throw WardrobeException(ErrorCode.CONFLICT, "Phiên bản món đồ đã thay đổi.")
                return@withTransaction old
            }
            if (old.revision != expectedRevision) throw WardrobeException(ErrorCode.CONFLICT, "Món đồ đã thay đổi. Hãy mở lại trước khi xóa.")
            WardrobeCodec.decodeItem(requireNotNull(old.payload)).images.refs().forEach {
                dao.enqueue(MediaCleanupEntity(it.id, WardrobeCodec.encodeMedia(it)))
            }
            val now = System.currentTimeMillis().coerceAtLeast(old.updatedAt)
            val deleted = old.copy(revision = old.revision + 1, updatedAt = now, deletedAt = now, payload = null)
            dao.put(deleted)
            dao.legacyId(id)?.let { db.clothingItemDao().deleteById(it) }
            deleted
        }
        events.emit(WardrobeInvalidation(id, tombstone.revision, true))
        cleanPending()
        DeletionReceipt(id, tombstone.revision, requireNotNull(tombstone.deletedAt),
            dao.pending().any { WardrobeCodec.decodeMedia(it.ref).ownerEntityId == id })
    } }

    suspend fun retryCleanup(): Result<Int> = mutations.withLock { cleanPending() }
    private suspend fun cleanPending(): Result<Int> = wardrobeResult {
        media.retryCleanup().getOrThrow()
        var count = 0
        dao.pending().forEach { entry ->
            val removed = media.remove(WardrobeCodec.decodeMedia(entry.ref))
            if (removed.getOrNull()?.cleanupPending == false) { dao.finishCleanup(entry.mediaId); count++ }
        }
        count
    }

    /** Used by cancel/replacement of an unsaved draft, never by an untrusted URI. */
    suspend fun discardDraft(refs: List<MediaRef>): Result<Unit> = mutations.withLock { wardrobeResult {
        db.withTransaction {
            val used = dao.active().flatMap { WardrobeCodec.decodeItem(requireNotNull(it.payload)).images.refs() }.map { it.id }.toSet()
            refs.filter { it.id !in used }.forEach { dao.enqueue(MediaCleanupEntity(it.id, WardrobeCodec.encodeMedia(it))) }
        }
        cleanPending().getOrThrow()
        Unit
    } }

    private fun ClothingItem.entity() = WardrobeEntity(id, revision, createdAt, updatedAt, deletedAt, WardrobeCodec.encodeItem(this))
    private fun checkRevision(old: WardrobeEntity?, expectedRevision: Long?) {
        if (old?.deletedAt != null) throw WardrobeException(ErrorCode.STALE_INPUT, "Món đồ đã bị xóa. Không thể khôi phục bằng bản sửa cũ.")
        if (old?.revision != expectedRevision) throw WardrobeException(ErrorCode.CONFLICT, "Món đồ đã thay đổi. Hãy tải bản mới trước khi lưu.")
    }
}

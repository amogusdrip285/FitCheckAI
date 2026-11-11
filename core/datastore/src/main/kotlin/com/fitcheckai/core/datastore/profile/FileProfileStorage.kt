package com.fitcheckai.core.datastore.profile

import com.fitcheckai.core.model.profile.ProfileInvalidation
import com.fitcheckai.core.model.profile.ProfileInvalidationReason
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.wardrobe.DeletionReceipt
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaRef
import com.fitcheckai.core.model.wardrobe.MediaScope
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.core.model.wardrobe.WardrobeException
import com.fitcheckai.core.model.wardrobe.wardrobeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * D-004: one typed JSON file written atomically (temp + fsync + rename) under a no-backup directory.
 * No network. A corrupt file surfaces as LoadState.Failed and is never silently wiped.
 * Person photo bytes stay in Track A's MediaStore; old refs are removed only after the metadata commit.
 */
class FileProfileStorage(
    private val directory: File,
    private val media: MediaStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProfileStorage {
    private val file = File(directory, FILE_NAME)
    private val mutex = Mutex()
    private val state = MutableStateFlow<LoadState<UserProfile?>>(LoadState.Loading)
    private val events = MutableSharedFlow<ProfileInvalidation>(extraBufferCapacity = 16)
    override val invalidations: Flow<ProfileInvalidation> = events

    override fun observeProfile(): Flow<LoadState<UserProfile?>> = flow {
        withContext(io) { mutex.withLock { if (state.value !is LoadState.Ready) state.value = read() } } // never read on main; retries after Failed
        emitAll(state)
    }

    override suspend fun save(profile: UserProfile, expectedRevision: Long?): Result<UserProfile> = write { old ->
        checkRevision(old, expectedRevision)
        val now = clock()
        val next = profile.copy(
            id = old?.id ?: profile.id,
            revision = (old?.revision ?: 0) + 1,
            createdAt = old?.createdAt ?: now,
            updatedAt = maxOf(now, old?.updatedAt ?: 0),
            bodyImages = old?.bodyImages ?: ImagePair(), // photos only change via replaceBodyPhoto
        )
        commit(next)
        next
    }

    override suspend fun replaceBodyPhoto(view: ImageView, ref: MediaRef?, expectedRevision: Long): Result<UserProfile> = write { old ->
        old ?: throw WardrobeException(ErrorCode.NEEDS_INPUT, "Hãy lưu hồ sơ trước khi thêm ảnh.")
        checkRevision(old, expectedRevision)
        if (ref != null && (ref.scope != MediaScope.PERSON || ref.view != view || ref.ownerEntityId != old.id)) {
            throw WardrobeException(ErrorCode.INVALID_REQUEST, "Ảnh không thuộc hồ sơ này.")
        }
        val previous = old.bodyImages[view]
        if (previous?.id == ref?.id) return@write old
        val next = old.copy(revision = old.revision + 1, updatedAt = maxOf(clock(), old.updatedAt), bodyImages = old.bodyImages.with(view, ref))
        commit(next)
        if (previous != null) {
            media.remove(previous) // failure leaves an orphan for MediaStore.retryCleanup; metadata is already consistent
            val reason = if (ref == null) ProfileInvalidationReason.BODY_PHOTO_REMOVED else ProfileInvalidationReason.BODY_PHOTO_REPLACED
            events.tryEmit(ProfileInvalidation(old.id, next.revision, setOf(previous.id), reason))
        }
        next
    }

    /**
     * Also the recovery path for an unreadable file (explicit user reset): metadata is deleted even though
     * refs are unknown; MediaStore cleanup of those orphans is then best-effort and reported as pending.
     */
    override suspend fun clearProfile(): Result<DeletionReceipt> = withContext(io) {
        wardrobeResult {
            mutex.withLock {
                if (state.value !is LoadState.Ready) state.value = read()
                val current = state.value
                val old = (current as? LoadState.Ready)?.value
                if (current is LoadState.Ready && old == null) throw WardrobeException(ErrorCode.STALE_INPUT, "Chưa có hồ sơ để xóa.")
                if (file.exists() && !file.delete()) throw WardrobeException(ErrorCode.IO, "Không xóa được hồ sơ.")
                tempFile().delete()
                state.value = LoadState.Ready(null)
                if (old == null) return@withLock DeletionReceipt(UNKNOWN_PROFILE_ID, 1, clock(), cleanupPending = true)
                val refs = old.bodyImages.refs()
                val pending = refs.map { media.remove(it) }.any { it.isFailure }
                events.tryEmit(ProfileInvalidation(old.id, old.revision + 1, refs.map { it.id }.toSet(), ProfileInvalidationReason.PROFILE_CLEARED))
                DeletionReceipt(old.id, old.revision + 1, maxOf(clock(), old.updatedAt), cleanupPending = pending)
            }
        }
    }

    private suspend fun <T> write(block: suspend (UserProfile?) -> T): Result<T> = withContext(io) {
        wardrobeResult {
            mutex.withLock {
                if (state.value !is LoadState.Ready) state.value = read()
                when (val current = state.value) {
                    is LoadState.Ready -> block(current.value)
                    is LoadState.Failed -> throw current.error
                    LoadState.Loading -> error("unreachable")
                }
            }
        }
    }

    private fun read(): LoadState<UserProfile?> = try {
        LoadState.Ready(if (file.exists()) ProfileCodec.decode(file.readText()) else null)
    } catch (_: Exception) {
        LoadState.Failed(WardrobeException(ErrorCode.IO, "Không đọc được hồ sơ. Bạn có thể đặt lại hồ sơ."))
    }

    private fun checkRevision(old: UserProfile?, expected: Long?) {
        if (old?.revision != expected) throw WardrobeException(ErrorCode.CONFLICT, "Hồ sơ vừa thay đổi. Hãy mở lại và thử lần nữa.")
    }

    private fun commit(next: UserProfile) {
        next.validate()
        directory.mkdirs()
        val tmp = tempFile()
        try {
            FileOutputStream(tmp).use { out ->
                out.write(ProfileCodec.encode(next).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tmp.delete() // no-op after a successful move
        }
        state.value = LoadState.Ready(next)
    }

    private fun tempFile() = File(directory, "$FILE_NAME.tmp")

    private companion object {
        const val FILE_NAME = "profile.json"
        const val UNKNOWN_PROFILE_ID = "00000000-0000-0000-0000-000000000000"
    }
}

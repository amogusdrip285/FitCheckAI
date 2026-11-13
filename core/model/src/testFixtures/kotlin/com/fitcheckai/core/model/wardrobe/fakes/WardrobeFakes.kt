package com.fitcheckai.core.model.wardrobe.fakes

import com.fitcheckai.core.model.wardrobe.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Test-only fixtures. Never included in the APK's production source set or Hilt bindings. */
fun fixtureId(label: String): String = UUID.nameUUIDFromBytes("fitcheck-test:$label".toByteArray()).toString()
fun fixtureMedia(owner: String, view: ImageView, key: String = "$owner-$view"): MediaRef {
    val id = fixtureId(key)
    return MediaRef(id, owner, MediaScope.GARMENT, view, "media/GARMENT/$owner/$id.jpg", "image/jpeg", 100, 200, 1,
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) })
}
fun sixFixtures(): List<ClothingItem> = Category.entries.filter { it != Category.UNKNOWN }.mapIndexed { index, category ->
    val id = fixtureId("item-$category")
    ClothingItem(id, "DEV $category", ImagePair(fixtureMedia(id, ImageView.FRONT),
        if (index < 3) fixtureMedia(id, ImageView.BACK) else null),
        ClothingAttributes(category, listOf(GarmentColor.BLUE), Pattern.SOLID), createdAt = 100, updatedAt = 100)
}

class FakeWardrobeRepository(initial: List<ClothingItem> = sixFixtures()) : WardrobeRepository {
    private val mutex = Mutex()
    private val rows = initial.associateBy { it.id }.toMutableMap()
    private val tombstones = mutableMapOf<String, DeletionReceipt>()
    private val flow = MutableStateFlow<LoadState<List<ClothingItem>>>(LoadState.Ready(initial))
    private val events = MutableSharedFlow<WardrobeInvalidation>(extraBufferCapacity = 64)
    override val invalidations: Flow<WardrobeInvalidation> = events
    var failNext: ErrorCode? = null
    var writeCount = 0
    override fun observeItems(): Flow<LoadState<List<ClothingItem>>> = flow
    private fun fail() { failNext?.let { failNext = null; throw WardrobeException(it, "DEV injected $it") } }
    private fun publish() { flow.value = LoadState.Ready(rows.values.sortedBy { it.id }) }
    override suspend fun get(id: String): Result<ClothingItem?> = wardrobeResult { fail(); rows[id] }
    override suspend fun getSnapshot(): Result<WardrobeSnapshot> = wardrobeResult {
        fail(); val items = rows.values.sortedBy { it.id }
        WardrobeSnapshot(fixtureId(items.joinToString { "${it.id}:${it.revision}" }), items)
    }
    override suspend fun upsert(item: ClothingItem, expectedRevision: Long?): Result<ClothingItem> = wardrobeResult { mutex.withLock {
        fail(); item.validate()
        if (item.id in tombstones) throw WardrobeException(ErrorCode.STALE_INPUT, "DEV deleted")
        val old = rows[item.id]
        if (old?.revision != expectedRevision) throw WardrobeException(ErrorCode.CONFLICT, "DEV conflict")
        val value = item.copy(revision = (old?.revision ?: 0) + 1, createdAt = old?.createdAt ?: item.createdAt,
            updatedAt = maxOf(item.updatedAt, old?.updatedAt ?: 0) + 1)
        rows[value.id] = value; writeCount++; publish(); events.tryEmit(WardrobeInvalidation(value.id, value.revision, false)); value
    } }
    override suspend fun delete(id: String, expectedRevision: Long): Result<DeletionReceipt> = wardrobeResult { mutex.withLock {
        fail()
        tombstones[id]?.let {
            if (expectedRevision !in setOf(it.revision, it.revision - 1)) throw WardrobeException(ErrorCode.CONFLICT, "DEV conflict")
            return@withLock it
        }
        val old = rows[id] ?: throw WardrobeException(ErrorCode.STALE_INPUT, "DEV deleted")
        if (old.revision != expectedRevision) throw WardrobeException(ErrorCode.CONFLICT, "DEV conflict")
        val receipt = DeletionReceipt(id, old.revision + 1, old.updatedAt + 1, false)
        tombstones[id] = receipt; rows.remove(id); publish(); events.tryEmit(WardrobeInvalidation(id, receipt.revision, true)); receipt
    } }
}

class FakeMediaStore : MediaStore {
    var failNext: ErrorCode? = null
    val removed = mutableListOf<String>()
    val missing = mutableSetOf<String>()
    private fun fail() { failNext?.let { failNext = null; throw WardrobeException(it, "DEV media $it") } }
    override suspend fun importImage(uri: String, scope: MediaScope, ownerId: String, view: ImageView): Result<MediaRef> = wardrobeResult {
        fail(); fixtureMedia(ownerId, view, uri).let { it.copy(scope = scope, localRelativePath = "media/$scope/$ownerId/${it.id}.jpg") }
    }
    override suspend fun read(ref: MediaRef): Result<ByteArray> = wardrobeResult {
        fail(); if (ref.id in missing || ref.id in removed) throw WardrobeException(ErrorCode.IO, "DEV missing image")
        byteArrayOf(1)
    }
    override suspend fun remove(ref: MediaRef): Result<DeletionReceipt> = wardrobeResult {
        fail(); removed += ref.id; DeletionReceipt(ref.id, ref.revision + 1, 100, false)
    }
    override suspend fun derive(ref: MediaRef, preset: DerivativePreset): Result<MediaRef> = wardrobeResult {
        fail(); fixtureMedia(ref.ownerEntityId, ref.view, ref.id + preset.name).copy(derivedFromMediaId = ref.id)
    }
    override fun displayPath(ref: MediaRef): String? = if (ref.id in missing || ref.id in removed) null else "/dev-fixture/${ref.id}.jpg"
    override suspend fun retryCleanup(): Result<Int> = wardrobeResult { fail(); 0 }
}

class FakeRecognition : ClothingRecognition {
    override val capability = RecognitionCapability("DEV fake", "fixture-v2", true, true)
    var respond: suspend (RecognitionRequest) -> Result<RecognitionResult> = { request ->
        Result.success(RecognitionResult(request.requestId, request.itemId, request.inputRevision,
            ClothingAttributes(Category.TOP), source = ResultSource.FAKE))
    }
    val requests = mutableListOf<RecognitionRequest>()
    override suspend fun recognize(request: RecognitionRequest): Result<RecognitionResult> {
        request.validate(capability); requests += request; return respond(request)
    }
}

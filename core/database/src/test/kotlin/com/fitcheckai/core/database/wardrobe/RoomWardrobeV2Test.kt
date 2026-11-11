package com.fitcheckai.core.database.wardrobe

import android.content.Context
import androidx.room.Room
import com.fitcheckai.core.database.AppDatabase
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.core.model.wardrobe.fakes.*
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomWardrobeV2Test {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomWardrobeRepository
    private lateinit var media: FakeMediaStore
    @Before fun setup() {
        context.deleteDatabase("v2-test")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        media = FakeMediaStore(); repo = RoomWardrobeRepository(db, media)
    }
    @After fun close() { db.close(); context.deleteDatabase("v2-test") }

    @Test fun `paired CRUD snapshots CAS and deletion cleanup`() = runBlocking {
        assertTrue(repo.getSnapshot().getOrThrow().items.isEmpty())
        val item = repo.upsert(sixFixtures().first()).getOrThrow()
        assertEquals(1L, item.revision)
        assertEquals(item, repo.get(item.id).getOrThrow())
        assertEquals(listOf(item), (repo.observeItems().first { it is LoadState.Ready } as LoadState.Ready).value)
        val before = repo.getSnapshot().getOrThrow()
        val replacement = fixtureMedia(item.id, ImageView.BACK, "replacement")
        val changed = repo.upsert(item.copy(name = "changed", images = item.images.copy(back = replacement)), 1).getOrThrow()
        assertEquals(item.id, changed.id); assertEquals(2L, changed.revision)
        assertTrue(item.images.back!!.id in media.removed)
        assertFalse(item.images.front!!.id in media.removed)
        assertFalse(repo.getSnapshot().getOrThrow().isCurrent(before.references))
        assertEquals(ErrorCode.CONFLICT, (repo.upsert(item, 1).exceptionOrNull() as WardrobeException).code)
        assertEquals(ErrorCode.CONFLICT, (repo.delete(item.id, 1).exceptionOrNull() as WardrobeException).code)
        repo.delete(item.id, 2).getOrThrow()
        assertNull(repo.get(item.id).getOrThrow())
        assertTrue(repo.getSnapshot().getOrThrow().items.isEmpty())
        assertNull(db.wardrobeDao().get(item.id)!!.payload)
        assertTrue(media.removed.containsAll(changed.images.refs().map { it.id }))
        assertEquals(3L, repo.delete(item.id, 2).getOrThrow().revision)
        assertEquals(ErrorCode.STALE_INPUT, (repo.upsert(item).exceptionOrNull() as WardrobeException).code)
    }
    @Test fun `one concurrent revision wins and media failure rolls back`() = runBlocking {
        val item = repo.upsert(sixFixtures().first()).getOrThrow()
        val results = (1..8).map { n -> async { repo.upsert(item.copy(name = "edit $n"), 1) } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        val before = repo.getSnapshot().getOrThrow()
        media.failNext = ErrorCode.IO
        assertTrue(repo.upsert(before.items.first().copy(name = "bad"), 2).isFailure)
        assertEquals(before, repo.getSnapshot().getOrThrow())
        val malformed = item.copy(images = ImagePair())
        assertTrue(repo.upsert(malformed).isFailure)
    }
    @Test fun `cleanup failure leaves durable journal and retries without deleting active refs`() = runBlocking {
        val item = repo.upsert(sixFixtures().first()).getOrThrow()
        media.failNext = ErrorCode.IO // retryCleanup fails first; DB tombstone still commits
        val receipt = repo.delete(item.id, 1).getOrThrow()
        assertTrue(receipt.cleanupPending)
        assertEquals(2, db.wardrobeDao().pending().size)
        repo.retryCleanup().getOrThrow()
        assertTrue(db.wardrobeDao().pending().isEmpty())
        val next = repo.upsert(sixFixtures()[1]).getOrThrow()
        val orphan = fixtureMedia(next.id, ImageView.BACK, "orphan")
        repo.discardDraft(next.images.refs() + orphan).getOrThrow()
        assertTrue(orphan.id in media.removed)
        assertFalse(next.images.front!!.id in media.removed)
    }
    @Test fun `reopen retains UUID revisions paired refs and tombstones`() = runBlocking {
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "v2-test").build()
        repo = RoomWardrobeRepository(db, media)
        val one = repo.upsert(sixFixtures()[0]).getOrThrow()
        val two = repo.upsert(sixFixtures()[1]).getOrThrow()
        repo.delete(two.id, 1).getOrThrow()
        val before = repo.getSnapshot().getOrThrow()
        db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, "v2-test").build()
        repo = RoomWardrobeRepository(db, media)
        assertEquals(before, repo.getSnapshot().getOrThrow())
        assertEquals(one.images, repo.get(one.id).getOrThrow()!!.images)
        assertTrue(repo.upsert(two).isFailure)
    }
    @Test fun `schema one migrates preserving old counters and repairable missing image`() = runBlocking {
        db.close()
        val schema = Json.parseToJsonElement(File("schemas/com.fitcheckai.core.database.AppDatabase/1.json").readText()).jsonObject.getValue("database").jsonObject
        val old = context.openOrCreateDatabase("v2-test", Context.MODE_PRIVATE, null)
        schema.getValue("entities").jsonArray.forEach { entry ->
            val table = entry.jsonObject
            old.execSQL(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
            table["indices"]?.jsonArray?.forEach { old.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content)) }
        }
        old.execSQL("INSERT INTO clothing_items VALUES ('seed-legacy', 'seed://old', NULL, 'TOP', 'Old shirt', 'json:[\"#FFFFFF\"]', 'solid', NULL, 2, 'json:[\"SUMMER\"]', 'json:[]', 100, 7, 90)")
        old.version = 1; old.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "v2-test").addMigrations(WardrobeMigration(context)).build()
        repo = RoomWardrobeRepository(db, media)
        val item = repo.getSnapshot().getOrThrow().items.single()
        requireUuid(item.id); item.validate()
        assertEquals("Old shirt", item.name); assertEquals(100L, item.createdAt)
        assertEquals(GarmentColor.WHITE, item.attributes.colors.single())
        assertTrue(item.attributes.needsReviewFields.isNotEmpty())
        assertEquals(7, db.clothingItemDao().getById("seed-legacy")!!.wearCount)
        assertEquals(item.id, db.wardrobeDao().migratedId("seed-legacy"))
        val bridge = LegacyWardrobeBridge(repo, media, db)
        assertEquals(item.id, bridge.getItem("seed-legacy")!!.id)
        assertEquals(7, bridge.observeItems().first().single().wearCount)
        assertTrue(bridge.markWorn(listOf(item.id), java.time.Instant.now()).isFailure)
        repo.delete(item.id, 1).getOrThrow()
        assertNull(db.clothingItemDao().getById("seed-legacy"))
    }
}

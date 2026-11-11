package com.fitcheckai.core.database

import android.content.Context
import androidx.room.Room
import com.fitcheckai.core.database.entity.FeedbackEntity
import com.fitcheckai.core.database.entity.OutfitEntity
import com.fitcheckai.core.database.entity.WearLogEntity
import com.fitcheckai.core.database.seed.DebugSeedCallback
import com.fitcheckai.core.database.seed.SeedData
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import java.io.File
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class WardrobeRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var files: WardrobeFiles
    private lateinit var repo: WardrobeRepositoryImpl
    private lateinit var source: File
    private val attrs = ClothingAttributes(Category.TOP, "Áo linen", listOf("#F0F0F0"))

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("persistence-test")
        File(context.filesDir, "wardrobe").deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        files = WardrobeFiles(context)
        repo = WardrobeRepositoryImpl(db, files)
        source = File(context.filesDir, "source.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    }

    @After fun tearDown() { db.close(); context.deleteDatabase("persistence-test") }

    @Test fun `save owns a durable copy with hash and flow reflects changes`() = runBlocking {
        assertTrue(repo.observeItems().first().isEmpty())
        val item = repo.save(source.path, attrs).getOrThrow()
        assertNotEquals(source.path, item.imagePath)
        assertArrayEquals(source.readBytes(), File(item.imagePath).readBytes())
        assertEquals(item, repo.getItem(item.id))
        assertEquals(listOf(item), repo.observeItems().first())
        assertEquals(64, db.clothingItemDao().getById(item.id)!!.imageHash!!.length)
        assertTrue(source.exists())
    }

    @Test fun `ten saves persist after database close and reopen`() = runBlocking {
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "persistence-test").build()
        repo = WardrobeRepositoryImpl(db, files)
        val saved = (1..10).map { repo.save(source.path, attrs.copy(subCategory = "Món $it")).getOrThrow() }
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "persistence-test").build()
        repo = WardrobeRepositoryImpl(db, files)
        val restored = repo.observeItems().first()
        assertEquals(saved.map { it.id }.toSet(), restored.map { it.id }.toSet())
        assertTrue(restored.all { File(it.imagePath).isFile })
    }

    @Test fun `stale detail update preserves concurrent wear and image hash`() = runBlocking {
        val old = repo.save(source.path, attrs).getOrThrow()
        val hash = db.clothingItemDao().getById(old.id)!!.imageHash
        val at = Instant.parse("2026-11-10T01:00:00Z")
        repo.markWorn(listOf(old.id, old.id), at).getOrThrow()
        repo.update(old.copy(attributes = attrs.copy(material = "linen"))).getOrThrow()
        val updated = repo.getItem(old.id)!!
        assertEquals(1, updated.wearCount)
        assertEquals(at, updated.lastWornAt)
        assertEquals(old.createdAt, updated.createdAt)
        assertEquals("linen", updated.attributes.material)
        assertEquals(hash, db.clothingItemDao().getById(old.id)!!.imageHash)
    }

    @Test fun `atomic wear increments handle concurrent calls duplicate ids and older times`() = runBlocking {
        val item = repo.save(source.path, attrs).getOrThrow()
        val newer = Instant.parse("2026-11-10T01:00:00Z")
        (1..12).map { async { repo.markWorn(listOf(item.id, item.id, "missing"), newer).getOrThrow() } }.awaitAll()
        repo.markWorn(listOf(item.id), newer.minusSeconds(200)).getOrThrow()
        repo.markWorn(emptyList(), newer).getOrThrow()
        assertEquals(13, repo.getItem(item.id)!!.wearCount)
        assertEquals(newer, repo.getItem(item.id)!!.lastWornAt)
    }

    @Test fun `wear updates support more than SQLite bind limit`() = runBlocking {
        val item = repo.save(source.path, attrs).getOrThrow()
        repo.markWorn((1..1000).map { "id-$it" } + item.id, Instant.now()).getOrThrow()
        assertEquals(1, repo.getItem(item.id)!!.wearCount)
    }

    @Test fun `delete removes record and owned file but keeps source and is idempotent`() = runBlocking {
        val item = repo.save(source.path, attrs).getOrThrow()
        repo.delete(item.id).getOrThrow()
        repo.delete(item.id).getOrThrow()
        assertNull(repo.getItem(item.id))
        assertFalse(File(item.imagePath).exists())
        assertTrue(source.exists())
        assertTrue(repo.observeItems().first().isEmpty())
    }

    @Test fun `delete tolerates missing images and never deletes files outside wardrobe`() = runBlocking {
        val item = repo.save(source.path, attrs).getOrThrow()
        File(item.imagePath).delete()
        repo.delete(item.id).getOrThrow()
        db.clothingItemDao().upsert(SeedData.items.first().copy(imagePath = source.path).toEntity())
        repo.delete(SeedData.items.first().id).getOrThrow()
        assertTrue(source.exists())
    }

    @Test fun `invalid save update and failed database writes return failure without orphan photos`() = runBlocking {
        assertTrue(repo.save("/missing/image.png", attrs).isFailure)
        val item = repo.save(source.path, attrs).getOrThrow()
        assertTrue(repo.update(item.copy(id = "missing")).isFailure)
        assertTrue(repo.update(item.copy(imagePath = source.path)).isFailure)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_save BEFORE INSERT ON clothing_items BEGIN SELECT RAISE(ABORT, 'write failed'); END",
        )
        assertTrue(repo.save(source.path, attrs).isFailure)
        assertEquals(1, File(context.filesDir, "wardrobe").listFiles()!!.size)
    }

    @Test fun `all shared DAOs round trip and upsert without duplicates`() = runBlocking {
        val outfit = OutfitEntity("outfit", ListCodec.encode(listOf("a", "b")), "CASUAL", "Lý do", "Gợi ý", "AI", 100)
        db.outfitDao().upsert(outfit)
        db.outfitDao().upsert(outfit.copy(explanation = "Updated"))
        assertEquals("Updated", db.outfitDao().getById("outfit")!!.explanation)
        assertNull(db.outfitDao().getById("missing"))
        val log = WearLogEntity("log", "outfit", outfit.itemIds, 100)
        db.wearLogDao().insert(log)
        db.wearLogDao().insert(log.copy(wornAtMillis = 200))
        db.wearLogDao().insert(log.copy(id = "later", wornAtMillis = 300))
        assertEquals(listOf(300L, 200L), db.wearLogDao().observeAll().first().map { it.wornAtMillis })
        db.feedbackDao().insert(FeedbackEntity(outfitId = "outfit", liked = true, reason = "Fits", createdAtMillis = 100))
        db.feedbackDao().insert(FeedbackEntity(outfitId = "outfit", liked = false, reason = null, createdAtMillis = 200))
        val feedback = db.feedbackDao().observeAll().first()
        assertEquals(listOf(false, true), feedback.map { it.liked })
        assertEquals(2, feedback.map { it.id }.distinct().size)
    }

    @Test fun `legacy test seeds have image files and are not restored after deletion`() = runBlocking {
        db.close()
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "persistence-test")
            .addCallback(DebugSeedCallback(context)).build()
        db = open()
        repo = WardrobeRepositoryImpl(db, files)
        val seed = repo.observeItems().first()
        assertEquals(18, seed.size)
        assertTrue(seed.all { File(it.imagePath).length() > 0 })
        seed.forEach { repo.delete(it.id).getOrThrow() }
        db.close()
        db = open()
        repo = WardrobeRepositoryImpl(db, files)
        assertTrue(repo.observeItems().first().isEmpty())
    }

    @Test fun `file imports reject traversal and empty input`() {
        assertTrue(runCatching { files.importImage("../outside", source.path) }.isFailure)
        source.writeBytes(byteArrayOf())
        assertTrue(runCatching { files.importImage("valid", source.path) }.isFailure)
    }
}

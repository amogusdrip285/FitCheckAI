package com.fitcheckai.core.database.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fitcheckai.core.database.AppDatabase
import com.fitcheckai.core.imaging.LocalMediaStore
import com.fitcheckai.core.model.wardrobe.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite/files, isolated test package/database; never touches the user's app database. */
@RunWith(AndroidJUnit4::class)
class RoomMediaDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "wardrobe-device-${UUID.randomUUID()}.db"
    private lateinit var db: AppDatabase
    private lateinit var media: LocalMediaStore
    private lateinit var repo: RoomWardrobeRepository
    private val refs = mutableListOf<MediaRef>()
    private val sources = mutableListOf<File>()
    private fun reopen() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(WardrobeMigration(context)).build()
        repo = RoomWardrobeRepository(db, media)
    }
    @Before fun setup() { media = LocalMediaStore(context); reopen() }
    @After fun close() = runBlocking { db.close(); refs.forEach { media.remove(it) }; sources.forEach { it.delete() }; context.deleteDatabase(name); Unit }
    private fun image(color: Int): File {
        val file = File.createTempFile("room-photo", ".png", context.cacheDir).also { sources += it }
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle(); return file
    }
    private suspend fun ref(owner: String, view: ImageView, color: Int) =
        media.importImage(image(color).path, MediaScope.GARMENT, owner, view).getOrThrow().also { refs += it }
    private suspend fun item(): ClothingItem {
        val id = UUID.randomUUID().toString()
        return ClothingItem(id, "Device QA", ImagePair(ref(id, ImageView.FRONT, Color.RED), ref(id, ImageView.BACK, Color.BLUE)))
    }

    @Test fun pairedSaveReopenReplaceDeleteKeepsOtherItemsAndMedia() = runBlocking {
        val saved = repo.upsert(item()).getOrThrow(); val other = repo.upsert(item()).getOrThrow()
        val before = repo.getSnapshot().getOrThrow()
        repeat(3) { db.close(); reopen(); assertEquals(before, repo.getSnapshot().getOrThrow()) }
        val changed = repo.upsert(saved.copy(images = saved.images.copy(back = ref(saved.id, ImageView.BACK, Color.GREEN))), 1).getOrThrow()
        assertEquals(saved.images.front, changed.images.front); assertTrue(media.read(saved.images.back!!).isFailure)
        assertFalse(repo.getSnapshot().getOrThrow().isCurrent(before.references))
        val badJournal = File(context.filesDir, "media-cleanup/qa-broken.json").apply { parentFile!!.mkdirs(); writeText("partial journal") }
        try { assertFalse(repo.delete(changed.id, changed.revision).getOrThrow().cleanupPending) }
        finally { badJournal.delete() }
        db.close(); reopen()
        assertEquals(listOf(other), repo.getSnapshot().getOrThrow().items)
        assertNull(db.wardrobeDao().get(saved.id)!!.payload)
        changed.images.refs().forEach { assertTrue(media.read(it).isFailure) }
        other.images.refs().forEach { assertTrue(media.read(it).isSuccess) }
        assertTrue(repo.upsert(saved).isFailure)
    }
    @Test fun sqliteAbortRollsBackCleanupAndConcurrentRevisionHasOneWinner() = runBlocking {
        val saved = repo.upsert(item()).getOrThrow()
        val replacement = ref(saved.id, ImageView.BACK, Color.GREEN)
        withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER qa_abort BEFORE INSERT ON wardrobe_v2 WHEN NEW.id='${saved.id}' AND NEW.revision>1 BEGIN SELECT RAISE(ABORT,'qa failure'); END") }
        assertTrue(repo.upsert(saved.copy(images = saved.images.copy(back = replacement)), 1).isFailure)
        assertEquals(saved, repo.get(saved.id).getOrThrow()); assertTrue(db.wardrobeDao().pending().isEmpty())
        assertTrue(media.read(saved.images.back!!).isSuccess)
        repo.discardDraft(saved.images.refs() + replacement).getOrThrow()
        assertTrue(media.read(replacement).isFailure); assertTrue(media.read(saved.images.front!!).isSuccess)
        withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER qa_abort") }
        val secondWriter = RoomWardrobeRepository(db, media)
        val results = (1..8).map { n -> async(Dispatchers.IO) {
            (if (n % 2 == 0) repo else secondWriter).upsert(saved.copy(name = "Writer $n"), 1)
        } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess }); assertEquals(7, results.count { (it.exceptionOrNull() as? WardrobeException)?.code == ErrorCode.CONFLICT })
    }
    @Test fun schemaOneMigratesOnAndroidWithoutResettingCountersOrImage() = runBlocking {
        db.close()
        val schema = context.assets.open("com.fitcheckai.core.database.AppDatabase/1.json").bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject.getValue("database").jsonObject }
        val oldImage = File(context.filesDir, "wardrobe/seed-native.png").apply { parentFile!!.mkdirs(); image(Color.RED).copyTo(this, overwrite = true) }.also { sources += it }
        withContext(Dispatchers.IO) {
            val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            schema.getValue("entities").jsonArray.forEach { entry ->
                val table = entry.jsonObject; val tableName = table.getValue("tableName").jsonPrimitive.content
                old.execSQL(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName))
                table["indices"]?.jsonArray?.forEach { old.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)) }
            }
            old.execSQL("INSERT INTO clothing_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                "seed-native", oldImage.path, null, "TOP", "Native legacy", "json:[\"#FFFFFF\"]", "solid", null, 2, "json:[]", "json:[]", 100, 7, 90))
            old.version = 1; old.close()
        }
        reopen()
        val migrated = repo.getSnapshot().getOrThrow().items.single(); migrated.validate(); refs += migrated.images.refs()
        assertEquals("Native legacy", migrated.name); assertEquals(100L, migrated.createdAt)
        assertEquals(7, db.clothingItemDao().getById("seed-native")!!.wearCount)
        assertEquals(migrated.id, db.wardrobeDao().migratedId("seed-native")); assertTrue(media.read(migrated.images.front!!).isSuccess)
        db.close(); reopen(); assertEquals(migrated, repo.get(migrated.id).getOrThrow())
    }
}

package com.fitcheckai.feature.wardrobe.v2

import android.content.Context
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.feature.wardrobe.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Bộ ảnh quần áo thật (CC0 / Wikimedia, xem manifest) đóng gói khi `.env` có FITCHECK_MOCK_WARDROBE=true. */
data class MockImportResult(val added: Int, val skipped: Int, val failed: Int)

@Singleton
class MockWardrobeImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WardrobeRepository,
    private val media: MediaStore,
) {
    private val mutex = Mutex()

    val isEnabled: Boolean get() = BuildConfig.MOCK_WARDROBE

    suspend fun loadManifest(): List<JsonObject> = withContext(Dispatchers.IO) {
        check(isEnabled) { "Mock wardrobe is disabled (FITCHECK_MOCK_WARDROBE)." }
        context.assets.open("$ASSET_DIR/manifest.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.getValue("items").jsonArray.map(JsonElement::jsonObject)
        }
    }

    /** Idempotent: items whose stable ID already exists are skipped. */
    suspend fun import(items: List<JsonObject>, onProgress: (Int) -> Unit = {}): MockImportResult = mutex.withLock {
        var added = 0; var skipped = 0; var failed = 0
        items.forEachIndexed { index, row ->
            when (importOne(row)) {
                Outcome.ADDED -> added++
                Outcome.SKIPPED -> skipped++
                Outcome.FAILED -> failed++
            }
            onProgress(index + 1)
        }
        MockImportResult(added, skipped, failed)
    }

    private enum class Outcome { ADDED, SKIPPED, FAILED }

    private suspend fun importOne(row: JsonObject): Outcome {
        val id = row.text("id")
        val refs = mutableListOf<MediaRef>()
        try {
            if (repository.get(id).getOrThrow() != null) return Outcome.SKIPPED
            for ((key, view) in listOf("front" to ImageView.FRONT, "back" to ImageView.BACK)) {
                val source = row[key] as? JsonObject ?: continue
                refs += importAsset(source.text("file"), id, view)
            }
            repository.upsert(toItem(row, refs)).getOrThrow()
            refs.clear() // committed refs belong to the repository now
            return Outcome.ADDED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Outcome.FAILED
        } finally {
            withContext(NonCancellable) { releaseOrphans(id, refs) }
        }
    }

    private suspend fun importAsset(file: String, ownerId: String, view: ImageView): MediaRef {
        val temp = withContext(Dispatchers.IO) {
            File.createTempFile("fixture-", ".jpg", context.cacheDir).also { out ->
                context.assets.open("$ASSET_DIR/$file").use { input -> out.outputStream().use(input::copyTo) }
            }
        }
        try {
            return media.importImage(temp.path, MediaScope.GARMENT, ownerId, view).getOrThrow()
        } finally {
            temp.delete()
        }
    }

    private suspend fun releaseOrphans(id: String, refs: List<MediaRef>) {
        if (refs.isEmpty()) return
        val saved = repository.get(id)
        if (saved.isFailure) return
        val retained = saved.getOrNull()?.images?.refs()?.map { it.id }.orEmpty()
        refs.filter { it.id !in retained }.forEach { media.remove(it) }
    }

    private fun toItem(row: JsonObject, refs: List<MediaRef>): ClothingItem {
        val kind = row["garmentType"]?.jsonPrimitive?.contentOrNull
        val review = setOf(AttributeField.CATEGORY, AttributeField.COLORS, AttributeField.PATTERN)
        val attributes = ClothingAttributes(
            Category.valueOf(row.text("category")),
            row.getValue("colors").jsonArray.map { GarmentColor.valueOf(it.jsonPrimitive.content) },
            Pattern.valueOf(row.text("pattern")),
            needsReviewFields = if (kind == null) review else review + AttributeField.GARMENT_TYPE,
            garmentType = kind,
        )
        val images = ImagePair(refs.first { it.view == ImageView.FRONT }, refs.find { it.view == ImageView.BACK })
        return ClothingItem(row.text("id"), row.text("name"), images, attributes, recognitionState = RecognitionState.REVIEW)
    }

    companion object {
        const val ASSET_DIR = "modern-clothing"
    }
}

internal fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

/**
 * Seeds the mock wardrobe once per install. Deleting seeded items never re-seeds them;
 * a failed run (nothing added and some failures) is retried on next launch.
 */
@Singleton
class MockWardrobeSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: MockWardrobeImporter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedOnce() {
        if (!importer.isEnabled) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        scope.launch {
            val result = runCatching { importer.import(importer.loadManifest()) }
            result.onFailure { android.util.Log.w(TAG, "Mock wardrobe seed failed", it) }
            val outcome = result.getOrNull() ?: return@launch
            if (outcome.added > 0 || outcome.failed == 0) prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            android.util.Log.i(TAG, "Mock wardrobe seeded: $outcome")
        }
    }

    private companion object {
        const val TAG = "MockWardrobe"
        const val PREFS = "mock_wardrobe"
        const val KEY_SEEDED = "seeded_v1"
    }
}

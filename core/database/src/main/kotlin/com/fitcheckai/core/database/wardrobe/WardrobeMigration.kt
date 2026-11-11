package com.fitcheckai.core.database.wardrobe

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitcheckai.core.database.ListCodec
import com.fitcheckai.core.model.wardrobe.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Preserves v1 records/tables for parked consumers; active wardrobe reads only the v2 table. */
class WardrobeMigration(private val context: Context) : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS wardrobe_v2 (id TEXT NOT NULL PRIMARY KEY, revision INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, payload TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS wardrobe_cleanup (mediaId TEXT NOT NULL PRIMARY KEY, ref TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS wardrobe_legacy_ids (legacyId TEXT NOT NULL PRIMARY KEY, itemId TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wardrobe_legacy_ids_itemId ON wardrobe_legacy_ids (itemId)")
        db.query("SELECT * FROM clothing_items").use { cursor ->
            while (cursor.moveToNext()) {
                fun text(key: String): String? = cursor.getColumnIndexOrThrow(key).let { if (cursor.isNull(it)) null else cursor.getString(it) }
                val oldId = requireNotNull(text("id"))
                val id = runCatching { requireUuid(oldId); oldId }.getOrElse {
                    UUID.nameUUIDFromBytes("fitcheck-v1:$oldId".toByteArray()).toString()
                }
                val mediaId = UUID.nameUUIDFromBytes("fitcheck-v1-front:$oldId".toByteArray()).toString()
                val original = runCatching { File(requireNotNull(text("imagePath"))).canonicalFile }.getOrNull()
                val base = context.filesDir.canonicalFile
                val safeLegacy = original?.parentFile == File(base, "wardrobe") && original?.extension == "png"
                val relative = if (safeLegacy) requireNotNull(original).relativeTo(base).invariantSeparatorsPath
                    else "media/GARMENT/$id/$mediaId.png"
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (safeLegacy && original?.isFile == true) BitmapFactory.decodeFile(original.path, options)
                // Missing/corrupt old images remain repairable items, never a reason to reset the DB.
                val ref = MediaRef(mediaId, id, MediaScope.GARMENT, ImageView.FRONT, relative, "image/png",
                    options.outWidth.coerceAtLeast(1), options.outHeight.coerceAtLeast(1),
                    (if (safeLegacy) requireNotNull(original).length() else 0).coerceAtLeast(1),
                    if (safeLegacy && original?.isFile == true) hash(original) else "0".repeat(64))
                val meta = File(base, "media-index/$mediaId.json")
                meta.parentFile!!.mkdirs()
                val atomic = AtomicFile(meta); val stream = atomic.startWrite()
                try { stream.write(WardrobeCodec.encodeMedia(ref).toByteArray()); atomic.finishWrite(stream) }
                catch (e: Exception) { atomic.failWrite(stream); throw e }
                fun list(key: String) = text(key)?.let(ListCodec::decode).orEmpty()
                val attributes = ClothingAttributes(
                    category = runCatching { Category.valueOf(text("category").orEmpty()) }.getOrDefault(Category.UNKNOWN),
                    colors = list("colors").map(::legacyColor).distinct().take(3),
                    pattern = runCatching { Pattern.valueOf(text("pattern").orEmpty().uppercase()) }.getOrDefault(Pattern.UNKNOWN),
                    material = text("material")?.takeIf { it.isNotBlank() }?.take(100),
                    formality = text("formality")?.toIntOrNull()?.takeIf { it in 1..5 },
                    seasonTags = list("seasons").filter { it in setOf("SPRING", "SUMMER", "AUTUMN", "WINTER") },
                    styleTags = list("styleTags").filter(String::isNotBlank).take(5).map { it.take(32) },
                    needsReviewFields = AttributeField.entries.toSet(),
                )
                val created = cursor.getLong(cursor.getColumnIndexOrThrow("createdAtMillis"))
                val item = ClothingItem(id, text("subCategory").orEmpty().ifBlank { "Món đồ đã nhập" }.take(100),
                    ImagePair(ref), attributes, createdAt = created, recognitionState = RecognitionState.REVIEW)
                db.execSQL("INSERT INTO wardrobe_v2(id,revision,createdAt,updatedAt,deletedAt,payload) VALUES(?,?,?,?,NULL,?)",
                    arrayOf<Any>(id, 1L, created, created, WardrobeCodec.encodeItem(item)))
                db.execSQL("INSERT INTO wardrobe_legacy_ids(legacyId,itemId) VALUES(?,?)", arrayOf(oldId, id))
            }
        }
    }

    companion object {
        fun legacyColor(hex: String): GarmentColor {
            val value = hex.removePrefix("#").toIntOrNull(16) ?: return GarmentColor.OTHER
            val red = (value shr 16 and 255) / 255f; val green = (value shr 8 and 255) / 255f; val blue = (value and 255) / 255f
            val max = maxOf(red, green, blue); val min = minOf(red, green, blue); val delta = max - min
            if (max < .18) return GarmentColor.BLACK
            if (delta < .12) return if (max > .88) GarmentColor.WHITE else GarmentColor.GRAY
            val hue = when (max) { red -> 60 * (((green - blue) / delta) % 6); green -> 60 * ((blue - red) / delta + 2); else -> 60 * ((red - green) / delta + 4) }.let { (it + 360) % 360 }
            return when {
                hue < 20 || hue >= 340 -> if (min > .4) GarmentColor.PINK else GarmentColor.RED
                hue < 55 -> if (max < .6) GarmentColor.BROWN else if (delta < .45) GarmentColor.BEIGE else GarmentColor.YELLOW
                hue < 80 -> GarmentColor.YELLOW
                hue < 175 -> GarmentColor.GREEN
                hue < 265 -> GarmentColor.BLUE
                else -> GarmentColor.PURPLE
            }
        }
        private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }
}

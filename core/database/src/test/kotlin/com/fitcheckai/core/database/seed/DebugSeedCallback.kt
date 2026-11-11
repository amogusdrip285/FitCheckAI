package com.fitcheckai.core.database.seed

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitcheckai.core.database.toEntity
import java.io.File

/** Runs exactly once, on database creation. Deleting all demo items never re-seeds them. */
class DebugSeedCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val folder = File(context.filesDir, "wardrobe").apply { mkdirs() }
        SeedData.items.forEach { item ->
            val image = File(folder, "${item.id}.png")
            context.assets.open("seed/${item.id}.png").use { input ->
                image.outputStream().use { input.copyTo(it) }
            }
            val row = item.copy(imagePath = image.absolutePath).toEntity()
            db.execSQL(
                "INSERT OR IGNORE INTO clothing_items " +
                    "(id,imagePath,imageHash,category,subCategory,colors,pattern,material,formality," +
                    "seasons,styleTags,createdAtMillis,wearCount,lastWornAtMillis) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(row.id, row.imagePath, row.imageHash, row.category, row.subCategory, row.colors,
                    row.pattern, row.material, row.formality, row.seasons, row.styleTags,
                    row.createdAtMillis, row.wearCount, row.lastWornAtMillis),
            )
        }
    }
}

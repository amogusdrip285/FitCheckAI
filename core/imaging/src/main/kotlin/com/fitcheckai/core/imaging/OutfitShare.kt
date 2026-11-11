package com.fitcheckai.core.imaging

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import com.fitcheckai.core.model.wardrobe.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepares a local share only. The caller opens the chooser after an explicit user action. */
class OutfitShare @Inject constructor(@ApplicationContext private val context: Context, private val media: MediaStore) {
    suspend fun prepare(title: String, selectedGarmentViews: List<MediaRef>): Result<Intent> = wardrobeResult {
        withContext(Dispatchers.IO) {
            require(selectedGarmentViews.size in 1..8)
            require(selectedGarmentViews.distinctBy { it.id }.size == selectedGarmentViews.size)
            require(selectedGarmentViews.all { it.scope == MediaScope.GARMENT }) { "Chỉ chia sẻ ảnh trang phục đã chọn." }
            val folder = File(context.cacheDir, "outfit-share").apply { check(mkdirs() || isDirectory) }
            cleanup()
            val columns = if (selectedGarmentViews.size == 1) 1 else 2
            val rows = (selectedGarmentViews.size + columns - 1) / columns
            val output = Bitmap.createBitmap(columns * 420, 100 + rows * 420, Bitmap.Config.ARGB_8888)
            val target = File(folder, "${UUID.randomUUID()}.jpg")
            try {
                val canvas = Canvas(output); canvas.drawColor(Color.WHITE)
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 40, 33); textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
                val heading = title.trim().ifBlank { "Phối đồ tham khảo" }.take(100)
                val visible = text.breakText(heading, true, output.width - 40f, null)
                canvas.drawText(heading.take(visible), 20f, 38f, text)
                text.textSize = 18f; text.typeface = Typeface.DEFAULT
                canvas.drawText("Ảnh trang phục · Không phải ảnh mặc thử", 20f, 72f, text)
                selectedGarmentViews.forEachIndexed { index, ref ->
                    val bytes = media.read(ref).getOrThrow()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw WardrobeException(ErrorCode.IO, "Ảnh không đọc được.")
                    try {
                        val scale = minOf(380f / bitmap.width, 370f / bitmap.height)
                        val left = index % columns * 420 + (420 - bitmap.width * scale) / 2
                        val top = 100 + index / columns * 420 + (380 - bitmap.height * scale) / 2
                        canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
                        canvas.drawText(if (ref.view == ImageView.FRONT) "Mặt trước" else "Mặt sau", index % columns * 420 + 20f, 100 + index / columns * 420 + 402f, text)
                    } finally { bitmap.recycle() }
                }
                target.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.wardrobe.share", target)
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Ảnh outfit", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (error: Exception) { target.delete(); throw error }
              finally { output.recycle() }
        }
    }
    /** Temp export files only; no original, person or result media is removed. */
    suspend fun cleanup(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        File(context.cacheDir, "outfit-share").listFiles().orEmpty().count {
            it.isFile && it.extension == "jpg" && now - it.lastModified() > 24L * 60 * 60 * 1000 && it.delete()
        }
    }
}

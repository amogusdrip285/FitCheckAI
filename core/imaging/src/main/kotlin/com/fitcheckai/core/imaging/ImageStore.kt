package com.fitcheckai.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class UploadImage(val path: String, val sha256: String, val mimeType: String = "image/jpeg")

/** Normalized drafts are durable across recreation. Repository copies only the chosen PNG on save. */
@Singleton
class ImageStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val drafts get() = File(context.filesDir, "wardrobe/drafts").apply {
        check(mkdirs() || isDirectory) { "Không thể tạo thư mục ảnh." }
    }

    suspend fun importSource(sourceUri: String): String = withContext(Dispatchers.IO) {
        val bitmap = decode(sourceUri)
        try { write(bitmap, "png", Bitmap.CompressFormat.PNG) } finally { bitmap.recycle() }
    }

    internal suspend fun decode(sourceUri: String): Bitmap = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(sourceUri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Tệp đã chọn không phải ảnh hợp lệ." }
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, MAX_EDGE)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val decoded = open(sourceUri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Không đọc được ảnh.")
        var current = decoded
        try {
            val exif = open(sourceUri).use { ExifInterface(it) }
            val transform = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            if (!transform.isIdentity) {
                current = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
                if (current !== decoded) decoded.recycle()
            }
            val edge = max(current.width, current.height)
            if (edge > MAX_EDGE) {
                val ratio = MAX_EDGE.toFloat() / edge
                val scaled = Bitmap.createScaledBitmap(current,
                    (current.width * ratio).roundToInt().coerceAtLeast(1),
                    (current.height * ratio).roundToInt().coerceAtLeast(1), true)
                if (scaled !== current) current.recycle()
                current = scaled
            }
            current
        } catch (error: Exception) {
            current.recycle()
            throw error
        }
    }

    internal suspend fun saveForeground(bitmap: Bitmap): String {
        var created: String? = null
        return try { withContext(Dispatchers.IO) { write(bitmap, "png", Bitmap.CompressFormat.PNG).also { created = it } } }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) { discard(created) }
            throw cancelled
        }
    }

    /** White composite avoids turning a transparent background black in JPEG. Hash the exact upload bytes. */
    suspend fun prepareUpload(path: String): UploadImage = withContext(Dispatchers.Default) {
        val source = decode(path)
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(source, 0f, 0f, null) }
            withContext(Dispatchers.IO) {
                val output = write(flattened, "jpg", Bitmap.CompressFormat.JPEG)
                UploadImage(output, sha256(File(output)))
            }
        } finally {
            source.recycle()
            flattened.recycle()
        }
    }

    fun createCaptureFile(): File = File(drafts, "${UUID.randomUUID()}.jpg")

    suspend fun discard(vararg paths: String?) = withContext(Dispatchers.IO) {
        paths.filterNotNull().distinct().forEach { path ->
            val file = File(path).canonicalFile
            if (file.parentFile == drafts.canonicalFile) file.delete()
        }
    }

    /** Process-death leftovers only. Recent drafts may still belong to a saved navigation entry. */
    suspend fun pruneOldDrafts() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        drafts.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        Unit
    }

    private fun open(source: String): InputStream {
        // Absolute file paths are not URIs (including Windows drive letters in JVM tests).
        if (File(source).isAbsolute) return File(source).inputStream()
        val uri = Uri.parse(source)
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> File(requireNotNull(uri.path)).inputStream()
            null -> File(source).inputStream()
            else -> throw IllegalArgumentException("Chỉ hỗ trợ ảnh trên thiết bị.")
        } ?: throw IOException("Không mở được ảnh. Hãy chọn lại ảnh.")
    }

    private fun write(bitmap: Bitmap, extension: String, format: Bitmap.CompressFormat): String {
        val file = File(drafts, "${UUID.randomUUID()}.$extension")
        val temporary = File(drafts, "${file.name}.tmp")
        try {
            temporary.outputStream().use { check(bitmap.compress(format, JPEG_QUALITY, it)) }
            check(temporary.renameTo(file)) { "Không thể ghi ảnh." }
            return file.absolutePath
        } finally { temporary.delete() }
    }

    companion object {
        const val MAX_EDGE = 768
        private const val JPEG_QUALITY = 85

        internal fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (max(width, height) / (sample * 2) >= target) sample *= 2
            return sample
        }

        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                var size = input.read(bytes)
                while (size != -1) {
                    digest.update(bytes, 0, size)
                    size = input.read(bytes)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

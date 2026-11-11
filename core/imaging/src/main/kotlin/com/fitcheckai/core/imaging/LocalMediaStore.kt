package com.fitcheckai.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import androidx.exifinterface.media.ExifInterface
import com.fitcheckai.core.model.wardrobe.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Immutable originals and derivatives. PERSON/result metadata and bytes both live outside backup. */
@Singleton
class LocalMediaStore @Inject constructor(@ApplicationContext private val context: Context) : MediaStore {
    private val mutex = Mutex()
    private fun root(scope: MediaScope) = if (scope == MediaScope.GARMENT) context.filesDir else context.noBackupFilesDir
    private fun index(scope: MediaScope) = File(root(scope), "media-index").apply { mkdirs() }
    private fun metadata(ref: MediaRef) = File(index(ref.scope), "${ref.id}.json")
    private fun journal(scope: MediaScope) = File(root(scope), "media-cleanup").apply { mkdirs() }

    override suspend fun importImage(uri: String, scope: MediaScope, ownerId: String, view: ImageView): Result<MediaRef> =
        wardrobeResult { withContext(Dispatchers.IO) {
            requireUuid(ownerId)
            val bytes = open(uri).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var count = input.read(buffer)
                while (count != -1) {
                    if (output.size() + count > MAX_INPUT_BYTES) throw WardrobeException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh nguồn vượt 32 MiB. Hãy chọn bản nhỏ hơn.")
                    output.write(buffer, 0, count); count = input.read(buffer)
                }
                output.toByteArray()
            }
            val bitmap = normalize(bytes, 2048)
            try { mutex.withLock { persist(bitmap, scope, ownerId, view, 2048, 4L * 1024 * 1024, null, false) } }
            finally { bitmap.recycle() }
        } }

    override suspend fun read(ref: MediaRef): Result<ByteArray> = wardrobeResult { withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = ownedFile(ref).readBytes()
            if (bytes.size.toLong() != ref.byteCount || sha(bytes) != ref.contentHash) {
                throw WardrobeException(ErrorCode.IO, "Ảnh đã thay đổi hoặc bị hỏng. Hãy chọn lại ảnh.")
            }
            bytes
        }
    } }

    override fun displayPath(ref: MediaRef): String? = runCatching { ownedFile(ref).absolutePath }.getOrNull()

    override suspend fun derive(ref: MediaRef, preset: DerivativePreset): Result<MediaRef> = wardrobeResult {
        withContext(Dispatchers.IO) {
            val bytes = read(ref).getOrThrow()
            val bitmap = normalize(bytes, preset.maxEdge)
            try { mutex.withLock {
                ownedFile(ref)
                persist(bitmap, ref.scope, ref.ownerEntityId, ref.view, preset.maxEdge,
                    preset.maxBytes, ref.id, preset != DerivativePreset.SEGMENTATION)
            } }
            finally { bitmap.recycle() }
        }
    }

    /** Records a segmentation result as a derivative; never replaces the original reference. */
    suspend fun importSegmented(uri: String, original: MediaRef): Result<MediaRef> = wardrobeResult {
        read(original).getOrThrow()
        val imported = importImage(uri, original.scope, original.ownerEntityId, original.view).getOrThrow()
        try {
            withContext(Dispatchers.IO) { mutex.withLock {
                ownedFile(original)
                val derived = imported.copy(derivedFromMediaId = original.id)
                atomicWrite(metadata(derived), WardrobeCodec.encodeMedia(derived).toByteArray())
                derived
            } }
        } catch (error: Exception) {
            withContext(kotlinx.coroutines.NonCancellable) { remove(imported) }
            throw error
        }
    }

    override suspend fun remove(ref: MediaRef): Result<DeletionReceipt> = wardrobeResult { withContext(Dispatchers.IO) {
        mutex.withLock {
            ref.validate()
            val stored = metadata(ref)
            if (!stored.exists()) {
                // Retrying a completed deletion is safe; never touch a path from an unregistered ref.
                return@withLock DeletionReceipt(ref.id, ref.revision + 1, System.currentTimeMillis(), false)
            }
            require(WardrobeCodec.decodeMedia(stored.readText()) == ref) { "Ảnh không thuộc tham chiếu được cấp." }
            val descendants = index(ref.scope).listFiles().orEmpty().filter { it.extension == "json" }
                .mapNotNull { runCatching { WardrobeCodec.decodeMedia(it.readText()) }.getOrNull() }
            val ids = mutableSetOf(ref.id)
            do { val before = ids.size; descendants.filter { it.derivedFromMediaId in ids }.forEach { ids += it.id } }
            while (before != ids.size)
            val refs = descendants.filter { it.id in ids }
            refs.forEach { atomicWrite(File(journal(it.scope), "${it.id}.json"), WardrobeCodec.encodeMedia(it).toByteArray()) }
            val pending = refs.map { clean(it) }.any { !it }
            DeletionReceipt(ref.id, ref.revision + 1, System.currentTimeMillis(), pending)
        }
    } }

    override suspend fun retryCleanup(): Result<Int> = wardrobeResult { withContext(Dispatchers.IO) { mutex.withLock {
        var cleaned = 0
        listOf(context.filesDir, context.noBackupFilesDir).forEach { base ->
            File(base, "media-cleanup").listFiles().orEmpty().filter { it.extension == "json" }.forEach { pending ->
                val ref = WardrobeCodec.decodeMedia(pending.readText())
                if (clean(ref)) cleaned++
            }
        }
        cleaned
    } } }

    override suspend fun pruneGarmentDrafts(retainedIds: Set<String>, olderThan: Long): Result<Int> = wardrobeResult {
        withContext(Dispatchers.IO) {
            require(olderThan >= 0)
            val candidates = mutex.withLock {
                index(MediaScope.GARMENT).listFiles().orEmpty().filter { it.extension == "json" && it.lastModified() < olderThan }
                    .mapNotNull { file -> runCatching { WardrobeCodec.decodeMedia(file.readText()) }.getOrNull() }
                    .filter { it.scope == MediaScope.GARMENT && it.derivedFromMediaId == null && it.id !in retainedIds }
            }
            var count = 0
            for (ref in candidates) if (remove(ref).getOrThrow().cleanupPending.not()) count++
            count
        }
    }

    private fun clean(ref: MediaRef): Boolean {
        // The journal is app-private; still validate scope, metadata and canonical containment.
        val stored = metadata(ref)
        if (stored.exists() && WardrobeCodec.decodeMedia(stored.readText()) != ref) return false
        val file = containedFile(ref)
        if (file.exists() && !file.delete()) return false
        if (stored.exists() && !stored.delete()) return false
        return File(journal(ref.scope), "${ref.id}.json").let { !it.exists() || it.delete() }
    }

    private fun ownedFile(ref: MediaRef): File {
        ref.validate()
        require(metadata(ref).isFile && WardrobeCodec.decodeMedia(metadata(ref).readText()) == ref) { "Tham chiếu ảnh không hợp lệ." }
        val file = containedFile(ref)
        if (!file.isFile) throw WardrobeException(ErrorCode.IO, "Không tìm thấy file ảnh. Bạn có thể chọn lại ảnh.")
        return file
    }

    private fun containedFile(ref: MediaRef): File {
        ref.validate()
        val base = root(ref.scope).canonicalFile
        val file = File(base, ref.localRelativePath).canonicalFile
        require(file.path.startsWith(base.path + File.separator))
        val modern = ref.localRelativePath.startsWith("media/${ref.scope.name}/${ref.ownerEntityId}/") &&
            file.nameWithoutExtension == ref.id
        val legacy = ref.scope == MediaScope.GARMENT && ref.localRelativePath.startsWith("wardrobe/") &&
            ref.localRelativePath.count { it == '/' } == 1 && file.extension == "png"
        require(modern || legacy) { "Không được truy cập ảnh ngoài phạm vi quản lý." }
        return file
    }

    private fun persist(source: Bitmap, scope: MediaScope, owner: String, view: ImageView, maxEdge: Int,
        maxBytes: Long, parent: String?, flatten: Boolean): MediaRef {
        var current = source
        try {
            if (flatten && source.hasAlpha()) {
                current = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                Canvas(current).apply { drawColor(Color.WHITE); drawBitmap(source, 0f, 0f, null) }
                current.setHasAlpha(false)
            }
            val png = !flatten && current.hasAlpha()
            val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            var bytes: ByteArray
            var quality = 90
            while (true) {
                bytes = ByteArrayOutputStream().use { output ->
                    check(current.compress(format, quality, output)); output.toByteArray()
                }
                if (bytes.size <= maxBytes) break
                if (!png && quality > 65) { quality -= 10; continue }
                require(max(current.width, current.height) > 256) { "Không thể thu nhỏ ảnh trong giới hạn cho phép." }
                val smaller = Bitmap.createScaledBitmap(current, (current.width * .8).toInt().coerceAtLeast(1),
                    (current.height * .8).toInt().coerceAtLeast(1), true)
                if (current !== source) current.recycle()
                current = smaller
            }
            check(max(current.width, current.height) <= maxEdge)
            val id = UUID.randomUUID().toString()
            val relative = "media/${scope.name}/$owner/$id.${if (png) "png" else "jpg"}"
            val ref = MediaRef(id, owner, scope, view, relative, if (png) "image/png" else "image/jpeg",
                current.width, current.height, bytes.size.toLong(), sha(bytes), derivedFromMediaId = parent)
            val file = containedFile(ref)
            try {
                atomicWrite(file, bytes)
                atomicWrite(metadata(ref), WardrobeCodec.encodeMedia(ref).toByteArray())
                return ref
            } catch (e: Exception) { file.delete(); metadata(ref).delete(); throw e }
        } finally { if (current !== source) current.recycle() }
    }

    private fun normalize(bytes: ByteArray, edge: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outMimeType in setOf("image/jpeg", "image/png") && options.outWidth > 0 && options.outHeight > 0) { "Chỉ nhận ảnh JPEG hoặc PNG hợp lệ." }
        require(options.outWidth.toLong() * options.outHeight <= 100_000_000) { "Ảnh vượt 100 megapixel." }
        options.inSampleSize = ImageStore.sampleSize(options.outWidth, options.outHeight, edge)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException()
        try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val matrix = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(exif.rotationDegrees.toFloat()) }
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (oriented !== bitmap) bitmap.recycle()
                bitmap = oriented
            }
            val longest = max(bitmap.width, bitmap.height)
            if (longest > edge) {
                val ratio = edge.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            return bitmap
        } catch (e: Exception) { bitmap.recycle(); throw e }
    }

    private fun open(source: String) = when {
        File(source).isAbsolute -> File(source).inputStream()
        else -> when (val uri = Uri.parse(source)) {
            else -> when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri) ?: throw IOException()
                "file" -> File(requireNotNull(uri.path)).inputStream()
                else -> throw IllegalArgumentException("Chọn ảnh từ thiết bị.")
            }
        }
    }

    companion object {
        private const val MAX_INPUT_BYTES = 32 * 1024 * 1024
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun atomicWrite(file: File, bytes: ByteArray) {
            check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (e: Exception) { atomic.failWrite(stream); throw e }
        }
    }
}

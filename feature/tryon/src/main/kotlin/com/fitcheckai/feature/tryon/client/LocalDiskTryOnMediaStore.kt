package com.fitcheckai.feature.tryon.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope
import com.fitcheckai.feature.tryon.contract.TryOnError
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure binary header + TIFF EXIF orientation (`0x0112`) inspector for Track C (`Deliverable B`).
 *
 * Works identically in JVM unit tests and Android runtime without trusting file extensions.
 */
data class InspectedImageHeader(
    val mimeType: String,
    val rawWidth: Int,
    val rawHeight: Int,
    val exifOrientation: Int,
    val byteCount: Long,
) {
    val swapsDimensions: Boolean
        get() = exifOrientation in 5..8

    val orientedWidth: Int
        get() = if (swapsDimensions) rawHeight else rawWidth

    val orientedHeight: Int
        get() = if (swapsDimensions) rawWidth else rawHeight
}

object TryOnImageValidator {
    private val pngSignature = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )

    fun inspect(
        bytes: ByteArray,
        minDimensionPx: Int = 64,
        maxDimensionPx: Int = 8192,
    ): Result<InspectedImageHeader> {
        if (bytes.isEmpty()) {
            return invalidImage("Tệp ảnh rỗng (0 byte).")
        }

        val header = when {
            isPng(bytes) -> parsePng(bytes)
            isJpeg(bytes) -> parseJpeg(bytes)
            isWebp(bytes) -> parseWebp(bytes)
            else -> null
        } ?: return invalidImage(
            "Định dạng tệp không hợp lệ hoặc dữ liệu ảnh bị hỏng (chỉ chấp nhận JPEG, PNG, WebP hợp lệ).",
        )

        if (header.orientedWidth < minDimensionPx || header.orientedHeight < minDimensionPx) {
            return invalidImage(
                "Độ phân giải ảnh quá nhỏ (${header.orientedWidth}x${header.orientedHeight}px; tối thiểu ${minDimensionPx}x${minDimensionPx}px).",
            )
        }
        if (header.orientedWidth > maxDimensionPx || header.orientedHeight > maxDimensionPx) {
            return invalidImage(
                "Độ phân giải ảnh vượt ngưỡng an toàn (${header.orientedWidth}x${header.orientedHeight}px; tối đa ${maxDimensionPx}px).",
            )
        }

        return Result.success(header)
    }

    private fun isPng(b: ByteArray): Boolean {
        if (b.size < 24) return false
        for (i in pngSignature.indices) {
            if (b[i] != pngSignature[i]) return false
        }
        return true
    }

    private fun parsePng(b: ByteArray): InspectedImageHeader? {
        if (b.size < 24) return null
        val ihdrTag = readAscii(b, 12, 4)
        if (ihdrTag != "IHDR") return null
        val width = readU32Be(b, 16)
        val height = readU32Be(b, 20)
        if (width <= 0 || height <= 0) return null
        return InspectedImageHeader(
            mimeType = "image/png",
            rawWidth = width,
            rawHeight = height,
            exifOrientation = 1,
            byteCount = b.size.toLong(),
        )
    }

    private fun isJpeg(b: ByteArray): Boolean =
        b.size >= 4 && u8(b, 0) == 0xFF && u8(b, 1) == 0xD8 && u8(b, 2) == 0xFF

    private fun parseJpeg(b: ByteArray): InspectedImageHeader? {
        var offset = 2
        var width = 0
        var height = 0
        var orientation = 1

        while (offset + 3 < b.size) {
            if (u8(b, offset) != 0xFF) {
                offset += 1
                continue
            }
            while (offset < b.size && u8(b, offset) == 0xFF) {
                offset += 1
            }
            if (offset >= b.size) break
            val marker = u8(b, offset)
            offset += 1

            if (marker == 0xD9 || marker == 0xDA) {
                break
            }
            if (marker == 0x01 || marker in 0xD0..0xD8) {
                continue
            }
            if (offset + 1 >= b.size) return null
            val segmentLen = readU16Be(b, offset)
            if (segmentLen < 2 || offset + segmentLen > b.size) return null

            val payloadStart = offset + 2
            val payloadLen = segmentLen - 2

            if (marker == 0xE1 && payloadLen >= 14 && readAscii(b, payloadStart, 6) == "Exif\u0000\u0000") {
                val parsedOrientation = parseTiffExifOrientation(b, payloadStart + 6, payloadLen - 6)
                if (parsedOrientation in 1..8) {
                    orientation = parsedOrientation
                }
            } else if (isJpegSofMarker(marker) && payloadLen >= 5) {
                height = readU16Be(b, payloadStart + 1)
                width = readU16Be(b, payloadStart + 3)
            }

            offset += segmentLen
        }

        if (width <= 0 || height <= 0) return null
        return InspectedImageHeader(
            mimeType = "image/jpeg",
            rawWidth = width,
            rawHeight = height,
            exifOrientation = orientation,
            byteCount = b.size.toLong(),
        )
    }

    private fun isJpegSofMarker(marker: Int): Boolean =
        marker in setOf(
            0xC0, 0xC1, 0xC2, 0xC3,
            0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB,
            0xCD, 0xCE, 0xCF,
        )

    private fun parseTiffExifOrientation(b: ByteArray, tiffStart: Int, tiffLen: Int): Int {
        if (tiffLen < 8 || tiffStart + tiffLen > b.size) return 1
        val endian = readAscii(b, tiffStart, 2)
        val littleEndian = when (endian) {
            "II" -> true
            "MM" -> false
            else -> return 1
        }
        val magic = readU16(b, tiffStart + 2, littleEndian)
        if (magic != 0x002A) return 1
        val ifdOffset = readU32(b, tiffStart + 4, littleEndian)
        if (ifdOffset < 8 || ifdOffset + 2 > tiffLen) return 1

        val dirStart = tiffStart + ifdOffset
        val entryCount = readU16(b, dirStart, littleEndian)
        var cursor = dirStart + 2
        val limit = tiffStart + tiffLen

        for (i in 0 until entryCount) {
            if (cursor + 12 > limit) break
            val tag = readU16(b, cursor, littleEndian)
            if (tag == 0x0112) {
                val value = readU16(b, cursor + 8, littleEndian)
                return if (value in 1..8) value else 1
            }
            cursor += 12
        }
        return 1
    }

    private fun isWebp(b: ByteArray): Boolean =
        b.size >= 16 && readAscii(b, 0, 4) == "RIFF" && readAscii(b, 8, 4) == "WEBP"

    private fun parseWebp(b: ByteArray): InspectedImageHeader? {
        if (b.size < 20) return null
        val chunkType = readAscii(b, 12, 4)
        var width = 0
        var height = 0
        when (chunkType) {
            "VP8X" -> {
                if (b.size < 30) return null
                width = 1 + (u8(b, 24) or (u8(b, 25) shl 8) or (u8(b, 26) shl 16))
                height = 1 + (u8(b, 27) or (u8(b, 28) shl 8) or (u8(b, 29) shl 16))
            }
            "VP8L" -> {
                if (b.size < 25 || u8(b, 20) != 0x2F) return null
                val bits = u8(b, 21) or (u8(b, 22) shl 8) or (u8(b, 23) shl 16) or (u8(b, 24) shl 24)
                width = (bits and 0x3FFF) + 1
                height = ((bits ushr 14) and 0x3FFF) + 1
            }
            "VP8 " -> {
                if (b.size < 30) return null
                if (u8(b, 23) != 0x9D || u8(b, 24) != 0x01 || u8(b, 25) != 0x2A) return null
                width = (u8(b, 26) or (u8(b, 27) shl 8)) and 0x3FFF
                height = (u8(b, 28) or (u8(b, 29) shl 8)) and 0x3FFF
            }
            else -> return null
        }
        if (width <= 0 || height <= 0) return null
        return InspectedImageHeader(
            mimeType = "image/webp",
            rawWidth = width,
            rawHeight = height,
            exifOrientation = 1,
            byteCount = b.size.toLong(),
        )
    }

    private fun invalidImage(message: String): Result<InspectedImageHeader> =
        Result.failure(
            TryOnException(
                TryOnError(
                    code = TryOnErrorCode.INVALID_REQUEST,
                    message = message,
                    retryable = false,
                ),
            ),
        )

    private fun u8(b: ByteArray, idx: Int): Int = b[idx].toInt() and 0xFF

    private fun readU16Be(b: ByteArray, idx: Int): Int =
        (u8(b, idx) shl 8) or u8(b, idx + 1)

    private fun readU32Be(b: ByteArray, idx: Int): Int =
        (u8(b, idx) shl 24) or (u8(b, idx + 1) shl 16) or (u8(b, idx + 2) shl 8) or u8(b, idx + 3)

    private fun readU16(b: ByteArray, idx: Int, littleEndian: Boolean): Int =
        if (littleEndian) {
            u8(b, idx) or (u8(b, idx + 1) shl 8)
        } else {
            readU16Be(b, idx)
        }

    private fun readU32(b: ByteArray, idx: Int, littleEndian: Boolean): Int =
        if (littleEndian) {
            u8(b, idx) or (u8(b, idx + 1) shl 8) or (u8(b, idx + 2) shl 16) or (u8(b, idx + 3) shl 24)
        } else {
            readU32Be(b, idx)
        }

    private fun readAscii(b: ByteArray, start: Int, len: Int): String {
        if (start < 0 || start + len > b.size) return ""
        return String(b, start, len, Charsets.ISO_8859_1)
    }
}

/**
 * Private-storage file implementation of [TryOnMediaStorePort] (`Deliverable B` & `Deliverable D`).
 *
 * Guarantees:
 * - Validates magic bytes (JPEG/PNG/WebP), pixel bounds, and TIFF EXIF Orientation (`0x0112`).
 * - Never overwrites original user/wardrobe photos; temporary upload derivatives are written only
 *   under `cacheRootDir/tryon_upload_derivatives/`.
 * - Never strips garment backgrounds or over-compresses logos/printed text.
 * - Saves AI/mock output images strictly inside private `filesRootDir/tryon_results/` (never public gallery).
 */
class LocalDiskTryOnMediaStore(
    private val filesRootDir: File,
    private val cacheRootDir: File,
    extraAllowedReadRoots: List<File> = emptyList(),
    private val minDimensionPx: Int = 64,
    private val maxUploadDimensionPx: Int = 1536,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TryOnMediaStorePort {

    private val canonicalFilesRoot: File = filesRootDir.canonicalFile
    private val canonicalCacheRoot: File = cacheRootDir.canonicalFile
    private val canonicalReadRoots: List<File> =
        (listOf(canonicalFilesRoot, canonicalCacheRoot) + extraAllowedReadRoots.map { it.canonicalFile }).distinct()

    private val derivativesDir: File
        get() = File(canonicalCacheRoot, "tryon_upload_derivatives").apply {
            mkdirs()
            restrictOwnerOnly(this)
        }

    private val resultsDir: File
        get() = File(canonicalFilesRoot, "tryon_results").apply {
            mkdirs()
            restrictOwnerOnly(this)
        }

    override suspend fun readBytes(ref: MediaRef): Result<ByteArray> = withContext(ioDispatcher) {
        val file = resolveReadableFile(ref.localRelativePath).getOrElse { return@withContext Result.failure(it) }
        if (!file.exists() || !file.isFile) {
            return@withContext Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.IO,
                        message = "Không tìm thấy tệp ảnh cục bộ cho '${ref.id}'.",
                    ),
                ),
            )
        }
        runCatching { file.readBytes() }.recoverCatching { cause ->
            throw TryOnException(
                TryOnError(
                    code = TryOnErrorCode.IO,
                    message = "Không thể đọc tệp ảnh cục bộ '${ref.id}'.",
                ),
                cause,
            )
        }
    }

    override suspend fun prepareUploadDerivative(
        ref: MediaRef,
        maxBytes: Long,
    ): Result<MediaRef> = withContext(ioDispatcher) {
        val rawBytes = readBytes(ref).getOrElse { return@withContext Result.failure(it) }
        val inspected = TryOnImageValidator.inspect(
            bytes = rawBytes,
            minDimensionPx = minDimensionPx,
        ).getOrElse { return@withContext Result.failure(it) }

        val needsExifTransform = inspected.exifOrientation in 2..8
        val needsDownscale =
            inspected.orientedWidth > maxUploadDimensionPx || inspected.orientedHeight > maxUploadDimensionPx
        val needsByteSqueeze = rawBytes.size.toLong() > maxBytes

        // Fast non-destructive path: keep original bytes intact (preserves garment logos/text 100%)
        if (!needsExifTransform && !needsDownscale && !needsByteSqueeze) {
            return@withContext Result.success(
                ref.copy(
                    mimeType = inspected.mimeType,
                    width = inspected.orientedWidth,
                    height = inspected.orientedHeight,
                    byteCount = rawBytes.size.toLong(),
                    contentHash = sha256Hex(rawBytes),
                ),
            )
        }

        // Derivative path: normalize EXIF orientation and/or downscale into temporary cache without touching original
        transcodeUploadDerivative(
            originalRef = ref,
            rawBytes = rawBytes,
            inspected = inspected,
            maxBytes = maxBytes,
        )
    }

    override suspend fun saveTryOnOutput(
        jobId: String,
        view: ImageView,
        mimeType: String,
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): Result<MediaRef> = withContext(ioDispatcher) {
        val inspected = TryOnImageValidator.inspect(
            bytes = bytes,
            minDimensionPx = minDimensionPx,
        ).getOrElse { cause ->
            return@withContext Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.INVALID_AI_OUTPUT,
                        message = "Ảnh kết quả trả về không đúng định dạng hợp lệ hoặc bị hỏng.",
                    ),
                    cause,
                ),
            )
        }

        val safeJobSlug = jobId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifEmpty { "job" }
        val ext = extensionForMime(inspected.mimeType)
        val outId = "out-${safeJobSlug}-${view.name.lowercase()}-${UUID.randomUUID().toString().take(8)}"
        val targetFile = File(resultsDir, "$outId.$ext")
        val tmpFile = File(resultsDir, "$outId.$ext.tmp")

        runCatching {
            FileOutputStream(tmpFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                fos.fd.sync()
            }
            Files.move(
                tmpFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            restrictOwnerOnly(targetFile)
        }.onFailure { cause ->
            tmpFile.delete()
            return@withContext Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.IO,
                        message = "Không thể lưu ảnh kết quả vào bộ nhớ riêng tư của ứng dụng.",
                    ),
                    cause,
                ),
            )
        }

        Result.success(
            MediaRef(
                id = outId,
                ownerEntityId = jobId,
                scope = MediaScope.TRYON_RESULT,
                view = view,
                localRelativePath = targetFile.absolutePath,
                mimeType = inspected.mimeType,
                width = inspected.orientedWidth,
                height = inspected.orientedHeight,
                byteCount = bytes.size.toLong(),
                contentHash = sha256Hex(bytes),
                revision = 1L,
            ),
        )
    }

    override suspend fun remove(ref: MediaRef): Result<Unit> = withContext(ioDispatcher) {
        if (ref.localRelativePath.startsWith("seed://")) {
            return@withContext Result.success(Unit)
        }
        val candidate = resolveWritableArtifactFile(ref.localRelativePath)
            .getOrElse { return@withContext Result.failure(it) }
        if (!candidate.exists()) {
            return@withContext Result.success(Unit)
        }
        if (candidate.delete()) {
            Result.success(Unit)
        } else {
            Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.IO,
                        message = "Không thể xóa tệp tạm '${ref.id}'.",
                    ),
                ),
            )
        }
    }

    private fun transcodeUploadDerivative(
        originalRef: MediaRef,
        rawBytes: ByteArray,
        inspected: InspectedImageHeader,
        maxBytes: Long,
    ): Result<MediaRef> {
        val sampleSize = computePowerOfTwoSampleSize(
            width = inspected.orientedWidth,
            height = inspected.orientedHeight,
            maxDimensionPx = maxUploadDimensionPx,
        )
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded: Bitmap? = runCatching {
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
        }.getOrNull()

        if (decoded == null) {
            val code = if (rawBytes.size.toLong() > maxBytes) {
                TryOnErrorCode.PAYLOAD_TOO_LARGE
            } else {
                TryOnErrorCode.INVALID_REQUEST
            }
            return Result.failure(
                TryOnException(
                    TryOnError(
                        code = code,
                        message = "Không thể giải mã/chuẩn hóa tệp ảnh đầu vào '${originalRef.id}' trong giới hạn ${maxBytes / 1024} KB.",
                    ),
                ),
            )
        }

        var working: Bitmap = decoded
        try {
            val matrix = buildExifAndScaleMatrix(
                rawWidth = working.width,
                rawHeight = working.height,
                exifOrientation = inspected.exifOrientation,
                maxDimensionPx = maxUploadDimensionPx,
            )
            if (!matrix.isIdentity) {
                val transformed = Bitmap.createBitmap(
                    working,
                    0,
                    0,
                    working.width,
                    working.height,
                    matrix,
                    true,
                )
                if (transformed !== working) {
                    working.recycle()
                    working = transformed
                }
            }

            val finalWidth = working.width
            val finalHeight = working.height

            // Preserve garment transparency / sharp edges as PNG first when possible
            var encodedBytes: ByteArray? = null
            var encodedMime = "image/jpeg"

            if (originalRef.scope == MediaScope.GARMENT && inspected.mimeType == "image/png") {
                val pngOut = ByteArrayOutputStream()
                if (working.compress(Bitmap.CompressFormat.PNG, 100, pngOut)) {
                    val candidate = pngOut.toByteArray()
                    if (candidate.isNotEmpty() && candidate.size.toLong() <= maxBytes) {
                        encodedBytes = candidate
                        encodedMime = "image/png"
                    }
                }
            }

            if (encodedBytes == null) {
                val qualitySteps = intArrayOf(92, 86, 80, 74)
                for (q in qualitySteps) {
                    val jpgOut = ByteArrayOutputStream()
                    if (working.compress(Bitmap.CompressFormat.JPEG, q, jpgOut)) {
                        val candidate = jpgOut.toByteArray()
                        if (candidate.isNotEmpty() && candidate.size.toLong() <= maxBytes) {
                            encodedBytes = candidate
                            encodedMime = "image/jpeg"
                            break
                        }
                    }
                }
            }

            val validEncoded = encodedBytes ?: return Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.PAYLOAD_TOO_LARGE,
                        message = "Ảnh '${originalRef.id}' vẫn vượt giới hạn ${maxBytes / 1024} KB sau khi chuẩn hóa kích thước.",
                    ),
                ),
            )

            val ext = extensionForMime(encodedMime)
            val derivId = "deriv-${originalRef.id.take(24)}-${UUID.randomUUID().toString().take(8)}"
            val derivFile = File(derivativesDir, "$derivId.$ext")
            FileOutputStream(derivFile).use { fos ->
                fos.write(validEncoded)
                fos.flush()
            }
            restrictOwnerOnly(derivFile)

            return Result.success(
                MediaRef(
                    id = derivId,
                    ownerEntityId = originalRef.ownerEntityId,
                    scope = originalRef.scope,
                    view = originalRef.view,
                    localRelativePath = derivFile.absolutePath,
                    mimeType = encodedMime,
                    width = finalWidth,
                    height = finalHeight,
                    byteCount = validEncoded.size.toLong(),
                    contentHash = sha256Hex(validEncoded),
                    revision = originalRef.revision,
                    derivedFromMediaId = originalRef.id,
                ),
            )
        } finally {
            if (!working.isRecycled) {
                working.recycle()
            }
        }
    }

    private fun resolveReadableFile(localPath: String): Result<File> {
        if (localPath.isBlank() || localPath.startsWith("seed://")) {
            return Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.INVALID_REQUEST,
                        message = "Đường dẫn ảnh đầu vào không phải tệp thực tế trên thiết bị.",
                    ),
                ),
            )
        }
        val rawFile = File(localPath).let { f ->
            if (f.isAbsolute) f else File(canonicalFilesRoot, localPath)
        }
        val canonical = runCatching { rawFile.canonicalFile }.getOrElse {
            return Result.failure(
                TryOnException(
                    TryOnError(code = TryOnErrorCode.INVALID_REQUEST, message = "Đường dẫn tệp ảnh không hợp lệ."),
                ),
            )
        }
        val allowed = canonicalReadRoots.any { root -> isInsideRoot(canonical, root) }
        if (!allowed) {
            return Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.FORBIDDEN,
                        message = "Từ chối đọc tệp nằm ngoài vùng lưu trữ riêng tư cho phép của ứng dụng.",
                    ),
                ),
            )
        }
        return Result.success(canonical)
    }

    private fun resolveWritableArtifactFile(localPath: String): Result<File> {
        val rawFile = File(localPath).let { f ->
            if (f.isAbsolute) f else File(canonicalFilesRoot, localPath)
        }
        val canonical = runCatching { rawFile.canonicalFile }.getOrElse {
            return Result.failure(
                TryOnException(
                    TryOnError(code = TryOnErrorCode.INVALID_REQUEST, message = "Đường dẫn tệp tạm không hợp lệ."),
                ),
            )
        }
        val writableAllowed =
            isInsideRoot(canonical, canonicalFilesRoot) || isInsideRoot(canonical, canonicalCacheRoot)
        if (!writableAllowed) {
            return Result.failure(
                TryOnException(
                    TryOnError(
                        code = TryOnErrorCode.FORBIDDEN,
                        message = "Từ chối xóa tệp ngoài thư mục riêng tư của ứng dụng.",
                    ),
                ),
            )
        }
        return Result.success(canonical)
    }

    private fun isInsideRoot(target: File, root: File): Boolean {
        val rootPath = root.path
        val targetPath = target.path
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return "sha256:" + digest.joinToString("") { "%02x".format(it) }.take(32)
        }

        internal fun computePowerOfTwoSampleSize(
            width: Int,
            height: Int,
            maxDimensionPx: Int,
        ): Int {
            var inSampleSize = 1
            val longest = max(width, height)
            while (longest / (inSampleSize * 2) >= maxDimensionPx) {
                inSampleSize *= 2
            }
            return inSampleSize
        }

        internal fun buildExifAndScaleMatrix(
            rawWidth: Int,
            rawHeight: Int,
            exifOrientation: Int,
            maxDimensionPx: Int,
        ): Matrix {
            val matrix = Matrix()
            when (exifOrientation) {
                2 -> matrix.postScale(-1f, 1f)
                3 -> matrix.postRotate(180f)
                4 -> matrix.postScale(1f, -1f)
                5 -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                6 -> matrix.postRotate(90f)
                7 -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                8 -> matrix.postRotate(270f)
            }

            val orientedW = if (exifOrientation in 5..8) rawHeight else rawWidth
            val orientedH = if (exifOrientation in 5..8) rawWidth else rawHeight
            val longest = max(orientedW, orientedH)
            if (longest > maxDimensionPx && longest > 0) {
                val scale = maxDimensionPx.toFloat() / longest.toFloat()
                // Ensure dimensions stay >= 1px
                val safeScale = scale.coerceAtLeast(1f / longest.toFloat())
                if ((orientedW * safeScale).roundToInt() >= 1 && (orientedH * safeScale).roundToInt() >= 1) {
                    matrix.postScale(safeScale, safeScale)
                }
            }
            return matrix
        }

        private fun extensionForMime(mimeType: String): String = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        private fun restrictOwnerOnly(file: File) {
            runCatching {
                file.setReadable(false, false)
                file.setReadable(true, true)
                file.setWritable(false, false)
                file.setWritable(true, true)
            }
        }
    }
}

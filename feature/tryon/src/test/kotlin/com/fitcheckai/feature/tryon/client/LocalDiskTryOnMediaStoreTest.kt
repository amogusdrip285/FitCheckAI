package com.fitcheckai.feature.tryon.client

import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

class LocalDiskTryOnMediaStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `TryOnImageValidator inspects PNG, EXIF-rotated JPEG, and WebP and rejects corrupt or tiny files`() {
        val pngBytes = buildValidRgbPngBytes(width = 192, height = 256)
        val pngHeader = TryOnImageValidator.inspect(pngBytes).getOrThrow()
        assertEquals("image/png", pngHeader.mimeType)
        assertEquals(192, pngHeader.orientedWidth)
        assertEquals(256, pngHeader.orientedHeight)
        assertEquals(1, pngHeader.exifOrientation)

        // Raw sensor 1024x768 with EXIF Orientation 6 (90 CW) -> oriented 768x1024 portrait
        val exifJpeg = buildValidJpegHeaderWithExifBytes(rawWidth = 1024, rawHeight = 768, orientation = 6)
        val jpegHeader = TryOnImageValidator.inspect(exifJpeg).getOrThrow()
        assertEquals("image/jpeg", jpegHeader.mimeType)
        assertEquals(1024, jpegHeader.rawWidth)
        assertEquals(768, jpegHeader.rawHeight)
        assertEquals(6, jpegHeader.exifOrientation)
        assertEquals(768, jpegHeader.orientedWidth)
        assertEquals(1024, jpegHeader.orientedHeight)

        val webpBytes = buildValidWebpVp8xBytes(width = 768, height = 1024)
        val webpHeader = TryOnImageValidator.inspect(webpBytes).getOrThrow()
        assertEquals("image/webp", webpHeader.mimeType)
        assertEquals(768, webpHeader.orientedWidth)
        assertEquals(1024, webpHeader.orientedHeight)

        // Empty 0-byte rejected
        val emptyErr = TryOnImageValidator.inspect(ByteArray(0)).exceptionOrNull() as TryOnException
        assertEquals(TryOnErrorCode.INVALID_REQUEST, emptyErr.tryOnError.code)

        // HTML disguised as .jpg rejected
        val htmlErr = TryOnImageValidator.inspect("<html>not an image</html>".encodeToByteArray())
            .exceptionOrNull() as TryOnException
        assertEquals(TryOnErrorCode.INVALID_REQUEST, htmlErr.tryOnError.code)

        // Too-small 16x16 PNG rejected
        val tinyPng = buildValidRgbPngBytes(width = 16, height = 16)
        val tinyErr = TryOnImageValidator.inspect(tinyPng).exceptionOrNull() as TryOnException
        assertEquals(TryOnErrorCode.INVALID_REQUEST, tinyErr.tryOnError.code)
    }

    @Test
    fun `LocalDiskTryOnMediaStore preserves upright garment bytes, blocks path traversal, and saves and deletes valid outputs`() = runTest {
        val filesRoot = tempFolder.newFolder("app_files")
        val cacheRoot = tempFolder.newFolder("app_cache")
        val outsideRoot = tempFolder.newFolder("outside_secret")
        val secretFile = File(outsideRoot, "secret.png").apply {
            writeBytes(buildValidRgbPngBytes(128, 128))
        }

        val store = LocalDiskTryOnMediaStore(
            filesRootDir = filesRoot,
            cacheRootDir = cacheRoot,
        )

        // 1. Non-destructive fast path keeps garment PNG bytes 100% identical (protects logos/text)
        val garmentBytes = buildValidRgbPngBytes(width = 384, height = 512)
        val garmentFile = File(filesRoot, "wardrobe/shirt_front.png").apply {
            parentFile?.mkdirs()
            writeBytes(garmentBytes)
        }
        val originalRef = MediaRef(
            id = "garment-shirt-1",
            ownerEntityId = "item-1",
            scope = MediaScope.GARMENT,
            view = ImageView.FRONT,
            localRelativePath = "wardrobe/shirt_front.png",
            mimeType = "image/png",
            width = 100, // Stale metadata gets refreshed from real header
            height = 100,
            byteCount = garmentBytes.size.toLong(),
            contentHash = "old",
            revision = 1L,
        )

        val prepared = store.prepareUploadDerivative(originalRef, maxBytes = 1024 * 1024L).getOrThrow()
        assertEquals("garment-shirt-1", prepared.id)
        assertEquals(384, prepared.width)
        assertEquals(512, prepared.height)
        assertArrayEquals(garmentBytes, store.readBytes(prepared).getOrThrow())
        assertArrayEquals(garmentBytes, garmentFile.readBytes())

        // 2. Path traversal outside private root is rejected with FORBIDDEN
        val traversalRef = originalRef.copy(
            id = "traversal",
            localRelativePath = "../outside_secret/${secretFile.name}",
        )
        val traversalErr = store.readBytes(traversalRef).exceptionOrNull() as TryOnException
        assertEquals(TryOnErrorCode.FORBIDDEN, traversalErr.tryOnError.code)

        // 3. Corrupted AI output bytes are rejected with INVALID_AI_OUTPUT
        val badOutRes = store.saveTryOnOutput(
            jobId = "job-openai-1",
            view = ImageView.FRONT,
            mimeType = "image/png",
            bytes = "NOT_AN_IMAGE".encodeToByteArray(),
            width = 192,
            height = 256,
        )
        assertEquals(
            TryOnErrorCode.INVALID_AI_OUTPUT,
            (badOutRes.exceptionOrNull() as TryOnException).tryOnError.code,
        )

        // 4. Valid PNG output is saved in private filesRoot/tryon_results and cleanly deleted
        val validOutBytes = buildValidRgbPngBytes(width = 192, height = 256)
        val savedOut = store.saveTryOnOutput(
            jobId = "job-openai-1",
            view = ImageView.FRONT,
            mimeType = "image/png",
            bytes = validOutBytes,
            width = 192,
            height = 256,
        ).getOrThrow()
        val savedFile = File(savedOut.localRelativePath)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.canonicalPath.startsWith(File(filesRoot, "tryon_results").canonicalPath))
        assertEquals(192, savedOut.width)
        assertEquals(256, savedOut.height)

        store.remove(savedOut).getOrThrow()
        assertFalse(savedFile.exists())
    }

    companion object {
        fun buildValidRgbPngBytes(width: Int, height: Int): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

            val ihdr = ByteArray(13)
            writeU32Be(ihdr, 0, width)
            writeU32Be(ihdr, 4, height)
            ihdr[8] = 8 // bit depth
            ihdr[9] = 2 // color type RGB
            ihdr[10] = 0
            ihdr[11] = 0
            ihdr[12] = 0
            writePngChunk(out, "IHDR", ihdr)

            val rawScanlines = ByteArray(height * (1 + width * 3))
            var p = 0
            for (y in 0 until height) {
                rawScanlines[p++] = 0 // filter None
                for (x in 0 until width) {
                    rawScanlines[p++] = ((x + y) and 0xFF).toByte()
                    rawScanlines[p++] = 140.toByte()
                    rawScanlines[p++] = 110.toByte()
                }
            }
            val deflater = Deflater(Deflater.BEST_SPEED)
            deflater.setInput(rawScanlines)
            deflater.finish()
            val idatBuf = ByteArrayOutputStream()
            val tmp = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(tmp)
                if (count > 0) idatBuf.write(tmp, 0, count)
            }
            deflater.end()
            writePngChunk(out, "IDAT", idatBuf.toByteArray())
            writePngChunk(out, "IEND", ByteArray(0))
            return out.toByteArray()
        }

        fun buildValidJpegHeaderWithExifBytes(rawWidth: Int, rawHeight: Int, orientation: Int): ByteArray {
            val out = ByteArrayOutputStream()
            // SOI
            out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            // APP1 Exif (big-endian MM, 1 IFD0 entry tag 0x0112 SHORT count=1 value=orientation)
            val exifPayload = byteArrayOf(
                'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
                'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A,
                0x00, 0x00, 0x00, 0x08, // IFD0 offset = 8
                0x00, 0x01, // 1 entry
                0x01, 0x12, // tag Orientation
                0x00, 0x03, // type SHORT
                0x00, 0x00, 0x00, 0x01, // count = 1
                0x00, orientation.toByte(), 0x00, 0x00, // value
                0x00, 0x00, 0x00, 0x00, // next IFD = 0
            )
            val app1Len = exifPayload.size + 2
            out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (app1Len ushr 8).toByte(), (app1Len and 0xFF).toByte()))
            out.write(exifPayload)

            // SOF0 (baseline DCT)
            val sofPayload = byteArrayOf(
                8,
                (rawHeight ushr 8).toByte(),
                (rawHeight and 0xFF).toByte(),
                (rawWidth ushr 8).toByte(),
                (rawWidth and 0xFF).toByte(),
                3,
                1, 0x11, 0,
                2, 0x11, 0,
                3, 0x11, 0,
            )
            val sofLen = sofPayload.size + 2
            out.write(byteArrayOf(0xFF.toByte(), 0xC0.toByte(), (sofLen ushr 8).toByte(), (sofLen and 0xFF).toByte()))
            out.write(sofPayload)
            // EOI
            out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
            return out.toByteArray()
        }

        fun buildValidWebpVp8xBytes(width: Int, height: Int): ByteArray {
            val wMinus1 = width - 1
            val hMinus1 = height - 1
            return byteArrayOf(
                'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
                22, 0, 0, 0,
                'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
                'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte(),
                10, 0, 0, 0,
                0, 0, 0, 0,
                (wMinus1 and 0xFF).toByte(),
                ((wMinus1 ushr 8) and 0xFF).toByte(),
                ((wMinus1 ushr 16) and 0xFF).toByte(),
                (hMinus1 and 0xFF).toByte(),
                ((hMinus1 ushr 8) and 0xFF).toByte(),
                ((hMinus1 ushr 16) and 0xFF).toByte(),
            )
        }

        private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
            val lenBytes = ByteArray(4)
            writeU32Be(lenBytes, 0, data.size)
            val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
            out.write(lenBytes)
            out.write(typeBytes)
            out.write(data)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(data)
            val crcBytes = ByteArray(4)
            writeU32Be(crcBytes, 0, crc.value.toInt())
            out.write(crcBytes)
        }

        private fun writeU32Be(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = ((value ushr 24) and 0xFF).toByte()
            buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
            buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
            buf[offset + 3] = (value and 0xFF).toByte()
        }
    }
}

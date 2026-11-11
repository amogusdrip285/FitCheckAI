package com.fitcheckai.core.imaging

import com.fitcheckai.core.model.wardrobe.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Optional on-device preview; callers own and release the returned derivative. */
interface GarmentCutoutProcessor {
    suspend fun readiness(requestDownload: Boolean): SegmentationReadiness
    suspend fun create(original: MediaRef): Result<MediaRef>
}

class MlKitGarmentCutoutProcessor @Inject constructor(private val segmenter: MlKitSegmenter,
    private val media: LocalMediaStore, private val images: ImageStore) : GarmentCutoutProcessor {
    override suspend fun readiness(requestDownload: Boolean) = segmenter.readiness(requestDownload)
    override suspend fun create(original: MediaRef): Result<MediaRef> = wardrobeResult {
        require(original.scope == MediaScope.GARMENT)
        media.read(original).getOrThrow()
        val source = media.displayPath(original) ?: throw WardrobeException(ErrorCode.IO, "Không đọc được ảnh gốc.")
        var temporary: String? = null
        var created: MediaRef? = null
        try {
            temporary = segmenter.removeBackground(source).getOrThrow()
            media.importSegmented(temporary, original).getOrThrow().also { created = it }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { created?.let { media.remove(it) } }
            throw cancelled
        } finally {
            withContext(NonCancellable) { images.discard(temporary) }
        }
    }
}

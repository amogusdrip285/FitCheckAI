package com.fitcheckai.feature.wardrobe

import com.fitcheckai.core.imaging.ImageStore
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingTagger
import com.fitcheckai.core.model.ImageSegmenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface PhotoProcessor {
    suspend fun importSource(uri: String): String
    suspend fun segment(original: String): Result<String>
    suspend fun tag(image: String): Result<ClothingAttributes>
    suspend fun discard(paths: List<String>)
    fun createCaptureFile(): File
}

class DefaultPhotoProcessor @Inject constructor(
    private val images: ImageStore,
    private val segmenter: ImageSegmenter,
    private val tagger: ClothingTagger,
) : PhotoProcessor {
    override suspend fun importSource(uri: String): String {
        images.pruneOldDrafts()
        return images.importSource(uri)
    }
    override suspend fun segment(original: String): Result<String> = segmenter.removeBackground(original)
    override suspend fun tag(image: String): Result<ClothingAttributes> {
        val upload = images.prepareUpload(image)
        try {
            // Track B receives <=768px JPEG; UploadImage also exposes SHA-256 of these exact bytes.
            return tagger.tag(upload.path)
        } finally { withContext(NonCancellable) { images.discard(upload.path) } }
    }
    override suspend fun discard(paths: List<String>) = images.discard(*paths.toTypedArray())
    override fun createCaptureFile(): File = images.createCaptureFile()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WardrobePhotoModule {
    @Binds abstract fun photoProcessor(impl: DefaultPhotoProcessor): PhotoProcessor
}

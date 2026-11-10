package com.fitcheckai.core.imaging

import com.fitcheckai.core.model.ImageSegmenter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPRINT 0 STUB — trả lại nguyên URI đầu vào, không tách nền.
 * TODO(Track A): MlKitSegmenter (Subject Segmentation) + lưu PNG vào filesDir/wardrobe/{id}.png.
 */
@Singleton
class StubImageSegmenter @Inject constructor() : ImageSegmenter {
    override suspend fun removeBackground(sourceUri: String): Result<String> = Result.success(sourceUri)
}

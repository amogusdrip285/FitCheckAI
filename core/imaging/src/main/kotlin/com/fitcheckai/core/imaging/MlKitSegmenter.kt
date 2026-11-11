package com.fitcheckai.core.imaging

import android.content.Context
import android.graphics.Bitmap
import com.fitcheckai.core.model.ImageSegmenter
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NoSubjectException : IllegalStateException("Không tìm thấy món đồ rõ ràng; ảnh gốc được giữ lại.")
class SegmentationUnavailableException : IllegalStateException("Tách nền chưa sẵn sàng; ảnh gốc được giữ lại.")
enum class SegmentationReadiness { READY, DOWNLOADING, UNAVAILABLE }

@Singleton
class MlKitSegmenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val images: ImageStore,
) : ImageSegmenter {
    private fun client() = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundBitmap().build(),
    )

    suspend fun readiness(requestDownload: Boolean = false): SegmentationReadiness = try {
        withTimeoutOrNull(5_000) {
            val segmenter = client()
            try {
                val modules = ModuleInstall.getClient(context)
                if (modules.areModulesAvailable(segmenter).await().areModulesAvailable()) SegmentationReadiness.READY
                else if (requestDownload) {
                    modules.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).await()
                    if (modules.areModulesAvailable(segmenter).await().areModulesAvailable()) SegmentationReadiness.READY
                    else SegmentationReadiness.DOWNLOADING
                } else SegmentationReadiness.UNAVAILABLE
            } finally { segmenter.close() }
        } ?: SegmentationReadiness.UNAVAILABLE
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { SegmentationReadiness.UNAVAILABLE }

    override suspend fun warmUp() {
        try {
            withTimeoutOrNull(5_000) {
                val segmenter = client()
                try {
                    ModuleInstall.getClient(context).installModules(
                        ModuleInstallRequest.newBuilder().addApi(segmenter).build(),
                    ).await()
                } finally { segmenter.close() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Onboarding must still work offline and on devices without Play services.
        }
    }

    override suspend fun removeBackground(sourceUri: String): Result<String> = try {
        val path = withTimeoutOrNull(8_000) {
            val bitmap = images.decode(sourceUri)
            val segmenter = client()
            var pending: Task<SubjectSegmentationResult>? = null
            var foreground: Bitmap? = null
            try {
                val task = segmenter.process(InputImage.fromBitmap(bitmap, 0))
                pending = task
                val result = task.await()
                val output = result.foregroundBitmap ?: throw NoSubjectException()
                foreground = output
                if (!hasForeground(output)) throw NoSubjectException()
                images.saveForeground(output)
            } finally {
                val task = pending
                if (task != null && !task.isComplete) {
                    // ML Kit's Task outlives a cancelled coroutine. Keep its input until it finishes.
                    task.addOnCompleteListener {
                        if (it.isSuccessful) it.result.foregroundBitmap?.takeIf { image -> image !== bitmap }?.recycle()
                        bitmap.recycle()
                        segmenter.close()
                    }
                } else {
                    foreground?.takeIf { it !== bitmap }?.recycle()
                    bitmap.recycle()
                    segmenter.close()
                }
            }
        } ?: throw SegmentationUnavailableException()
        Result.success(path)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // The caller has already persisted the original and can present a useful fallback notice.
        Result.failure(error)
    }

    private suspend fun hasForeground(bitmap: Bitmap): Boolean = withContext(Dispatchers.Default) {
        val row = IntArray(bitmap.width)
        var opaque = 0
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            opaque += row.count { (it ushr 24) > 127 }
        }
        opaque > bitmap.width * bitmap.height / 100
    }
}

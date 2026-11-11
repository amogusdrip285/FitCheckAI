package com.fitcheckai.core.imaging

import android.content.Context
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Owns only this screen's use cases; never unbinds another consumer's camera. */
class WardrobeCamera(private val context: Context) {
    val previewView = PreviewView(context).apply {
        // TextureView stays inside Compose's bounds during layout/navigation transitions.
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FIT_CENTER
    }
    private val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private val preview = Preview.Builder().build()
    private var provider: ProcessCameraProvider? = null
    private var disposed = false

    suspend fun bind(owner: LifecycleOwner) {
        val cameraProvider = suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (continuation.isActive) {
                    try { continuation.resume(future.get()) } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        if (disposed) return
        provider = cameraProvider
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else { CameraSelector.DEFAULT_FRONT_CAMERA }
        cameraProvider.bindToLifecycle(owner, selector, preview, capture)
    }

    suspend fun capture(file: File): String = suspendCancellableCoroutine { continuation ->
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) continuation.resume(file.absolutePath) else file.delete()
                }
                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            })
    }

    fun close() {
        disposed = true
        provider?.unbind(preview, capture)
    }
}

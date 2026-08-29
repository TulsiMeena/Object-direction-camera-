package com.example.vision.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class CameraState {
    object Idle : CameraState()
    object Initializing : CameraState()
    object Running : CameraState()
    data class Error(val message: String, val throwable: Throwable? = null) : CameraState()
}

/**
 * Robust CameraX manager handling preview, image analysis, torch, camera flipping, and lifecycle binding.
 */
class CameraManager(
    private val context: Context,
    initialFrameProcessor: VisionFrameProcessor
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var currentFrameProcessor: VisionFrameProcessor = initialFrameProcessor

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var isFrontCamera = false
    private var isTorchOn = false

    fun setFrameProcessor(newProcessor: VisionFrameProcessor) {
        currentFrameProcessor = newProcessor
    }

    fun getCurrentFrameProcessor(): VisionFrameProcessor = currentFrameProcessor

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean = false,
        onInitialized: () -> Unit = {}
    ) {
        _cameraState.value = CameraState.Initializing
        isFrontCamera = useFrontCamera

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView)
                _cameraState.value = CameraState.Running
                onInitialized()
            } catch (exc: Exception) {
                _cameraState.value = CameraState.Error(
                    message = exc.localizedMessage ?: "Failed to start camera",
                    throwable = exc
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val provider = cameraProvider ?: return

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Preview use-case
        val preview = Preview.Builder()
            .setTargetResolution(Size(1280, 720))
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        // Image Analysis use-case with STRATEGY_KEEP_ONLY_LATEST to avoid frame buildup
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                if (cameraExecutor.isShutdown) {
                    cameraExecutor = Executors.newSingleThreadExecutor()
                }
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    currentFrameProcessor.processImage(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            // Restore torch state if back camera
            if (!isFrontCamera && isTorchOn) {
                camera?.cameraControl?.enableTorch(true)
            }
        } catch (exc: Exception) {
            _cameraState.value = CameraState.Error("Failed to bind camera use cases: ${exc.message}", exc)
        }
    }

    fun toggleCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        isFrontCamera = !isFrontCamera
        isTorchOn = false
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        if (isFrontCamera) return false
        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        return isTorchOn
    }

    fun isTorchEnabled(): Boolean = isTorchOn
    fun isFrontFacing(): Boolean = isFrontCamera

    fun stopCamera() {
        try {
            if (isTorchOn) {
                camera?.cameraControl?.enableTorch(false)
                isTorchOn = false
            }
            cameraProvider?.unbindAll()
            camera = null
            _cameraState.value = CameraState.Idle
        } catch (_: Exception) {}
    }

    fun release() {
        stopCamera()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        currentFrameProcessor.release()
    }
}

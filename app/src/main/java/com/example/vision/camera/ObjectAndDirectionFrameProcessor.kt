package com.example.vision.camera

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.vision.detector.VisionObjectDetector
import com.example.vision.model.PerformanceMetrics
import com.example.vision.model.TrackedObject
import com.example.vision.model.VisionSettings
import com.example.vision.tracking.ObjectTracker
import com.example.vision.util.CoordinateTransformer
import com.example.vision.util.PerformanceMonitor
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated frame processor for Object & Direction detection.
 * Executes on background coroutines with non-blocking backpressure handling.
 */
class ObjectAndDirectionFrameProcessor(
    private val objectDetector: VisionObjectDetector,
    private val objectTracker: ObjectTracker = ObjectTracker(),
    private val coordinateTransformer: CoordinateTransformer = CoordinateTransformer(),
    private val performanceMonitor: PerformanceMonitor = PerformanceMonitor()
) : VisionFrameProcessor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)

    private val _trackedObjects = MutableStateFlow<List<TrackedObject>>(emptyList())
    override val trackedObjects: StateFlow<List<TrackedObject>> = _trackedObjects.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    override val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    @Volatile
    private var currentSettings = VisionSettings()

    @Volatile
    private var viewWidth = 0f
    @Volatile
    private var viewHeight = 0f

    override fun updateSettings(settings: VisionSettings) {
        currentSettings = settings
    }

    override fun updateViewDimensions(width: Float, height: Float) {
        viewWidth = width
        viewHeight = height
    }

    @OptIn(ExperimentalGetImage::class)
    override fun processImage(imageProxy: ImageProxy) {
        performanceMonitor.onCameraFrame()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Bounded frame processing: If previous inference is still executing, drop frame immediately
        if (!isProcessing.compareAndSet(false, true)) {
            performanceMonitor.onFrameDropped()
            _performanceMetrics.value = performanceMonitor.getMetrics()
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val isFrontCamera = currentSettings.isFrontCamera

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            try {
                // 1. Run local real-time object detection
                val rawDetections = objectDetector.detect(inputImage, currentSettings.confidenceLevel)
                val inferenceDuration = SystemClock.elapsedRealtime() - startTime
                performanceMonitor.onDetectionComplete(inferenceDuration)

                // 2. Update coordinate transformation matrix
                coordinateTransformer.update(
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rotationDegrees = rotationDegrees,
                    isFrontCamera = isFrontCamera
                )

                // 3. Update multi-frame tracking & direction analysis
                val updatedTracked = objectTracker.update(
                    rawDetections = rawDetections,
                    coordinateTransformer = coordinateTransformer,
                    now = System.currentTimeMillis()
                )

                _trackedObjects.value = updatedTracked
                performanceMonitor.onTrackingRendered()
                _performanceMetrics.value = performanceMonitor.getMetrics()
            } catch (_: Exception) {
                // Resilient error handling - never crash on bad frame
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    override fun release() {
        scope.cancel()
        objectDetector.close()
        objectTracker.reset()
        performanceMonitor.reset()
        _trackedObjects.value = emptyList()
    }
}

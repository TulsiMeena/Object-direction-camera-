package com.example.vision.color

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.vision.camera.VisionFrameProcessor
import com.example.vision.model.PerformanceMetrics
import com.example.vision.model.SampledColor
import com.example.vision.model.TrackedObject
import com.example.vision.model.VisionSettings
import com.example.vision.util.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated frame processor for real-time camera color sampling.
 * Directly extracts pixel color data from live ImageProxy in background coroutines.
 */
class ColorDetectorProcessor(
    private val performanceMonitor: PerformanceMonitor = PerformanceMonitor()
) : VisionFrameProcessor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)

    private val _sampledColor = MutableStateFlow(
        ColorDictionary.findClosestColor(128, 128, 128, 0.5f, 0.5f)
    )
    val sampledColor: StateFlow<SampledColor> = _sampledColor.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    override val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    override val trackedObjects: StateFlow<List<TrackedObject>> = MutableStateFlow(emptyList())

    @Volatile
    private var sampleNormalizedX = 0.5f
    @Volatile
    private var sampleNormalizedY = 0.5f

    @Volatile
    private var viewWidth = 1080f
    @Volatile
    private var viewHeight = 1920f

    fun setSamplePoint(normalizedX: Float, normalizedY: Float) {
        sampleNormalizedX = normalizedX.coerceIn(0.05f, 0.95f)
        sampleNormalizedY = normalizedY.coerceIn(0.05f, 0.95f)
    }

    override fun updateSettings(settings: VisionSettings) {}

    override fun updateViewDimensions(width: Float, height: Float) {
        viewWidth = width
        viewHeight = height
    }

    @OptIn(ExperimentalGetImage::class)
    override fun processImage(imageProxy: ImageProxy) {
        performanceMonitor.onCameraFrame()

        if (!isProcessing.compareAndSet(false, true)) {
            performanceMonitor.onFrameDropped()
            _performanceMetrics.value = performanceMonitor.getMetrics()
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height

        scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            try {
                // Extract RGB at normalized sample position
                val (r, g, b) = samplePixelRgb(imageProxy, sampleNormalizedX, sampleNormalizedY, rotationDegrees)
                val closest = ColorDictionary.findClosestColor(r, g, b, sampleNormalizedX, sampleNormalizedY)

                _sampledColor.value = closest
                val duration = SystemClock.elapsedRealtime() - startTime
                performanceMonitor.onDetectionComplete(duration)
                performanceMonitor.onTrackingRendered()
                _performanceMetrics.value = performanceMonitor.getMetrics()
            } catch (_: Exception) {
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    /**
     * Converts YUV_420_888 pixels in a 7x7 patch around (normalizedX, normalizedY) to average RGB.
     */
    private fun samplePixelRgb(
        image: ImageProxy,
        normX: Float,
        normY: Float,
        rotationDegrees: Int
    ): Triple<Int, Int, Int> {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val imgW = image.width
        val imgH = image.height

        // Account for camera sensor orientation
        val (targetX, targetY) = when (rotationDegrees) {
            90 -> Pair((normY * imgW).toInt(), ((1f - normX) * imgH).toInt())
            180 -> Pair(((1f - normX) * imgW).toInt(), ((1f - normY) * imgH).toInt())
            270 -> Pair(((1f - normY) * imgW).toInt(), (normX * imgH).toInt())
            else -> Pair((normX * imgW).toInt(), (normY * imgH).toInt())
        }

        val clampedCenterX = targetX.coerceIn(4, imgW - 5)
        val clampedCenterY = targetY.coerceIn(4, imgH - 5)

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sampleCount = 0

        // Sample 5x5 window for robust noise reduction
        val sampleRadius = 2
        for (dy in -sampleRadius..sampleRadius) {
            val yPos = clampedCenterY + dy
            for (dx in -sampleRadius..sampleRadius) {
                val xPos = clampedCenterX + dx

                val yIndex = yPos * yRowStride + xPos * yPixelStride
                val uvIndex = (yPos / 2) * uvRowStride + (xPos / 2) * uvPixelStride

                if (yIndex < yBuffer.limit() && uvIndex < uBuffer.limit() && uvIndex < vBuffer.limit()) {
                    val yVal = (yBuffer.get(yIndex).toInt() and 0xFF)
                    val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                    val vVal = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                    // Standard BT.601 YUV to RGB matrix
                    val r = (yVal + 1.402f * vVal).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.772f * uVal).toInt().coerceIn(0, 255)

                    sumR += r
                    sumG += g
                    sumB += b
                    sampleCount++
                }
            }
        }

        if (sampleCount == 0) return Triple(128, 128, 128)

        return Triple(
            (sumR / sampleCount).toInt(),
            (sumG / sampleCount).toInt(),
            (sumB / sampleCount).toInt()
        )
    }

    override fun release() {
        scope.cancel()
        performanceMonitor.reset()
    }
}

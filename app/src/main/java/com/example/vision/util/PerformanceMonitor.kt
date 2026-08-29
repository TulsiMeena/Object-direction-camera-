package com.example.vision.util

import android.os.SystemClock
import com.example.vision.model.PerformanceMetrics

/**
 * Accurately measures real pipeline latency, camera FPS, detection FPS,
 * overlay tracking FPS, and dropped frames.
 */
class PerformanceMonitor {

    private var lastCameraFrameTime = 0L
    private var lastDetectionTime = 0L
    private var lastTrackingTime = 0L

    private var cameraFrameCount = 0L
    private var detectionCount = 0L
    private var trackingCount = 0L
    private var droppedFramesCount = 0L
    private var totalInferenceTimeSum = 0L
    private var lastInferenceDurationMs = 0L

    private var smoothedCameraFps = 0.0
    private var smoothedDetectionFps = 0.0
    private var smoothedTrackingFps = 0.0

    private val fpsSmoothingFactor = 0.15

    /**
     * Call when a new camera frame arrives from CameraX.
     */
    fun onCameraFrame() {
        val now = SystemClock.elapsedRealtime()
        if (lastCameraFrameTime > 0) {
            val delta = now - lastCameraFrameTime
            if (delta > 0) {
                val instantFps = 1000.0 / delta
                smoothedCameraFps = if (smoothedCameraFps == 0.0) {
                    instantFps
                } else {
                    (smoothedCameraFps * (1.0 - fpsSmoothingFactor)) + (instantFps * fpsSmoothingFactor)
                }
            }
        }
        lastCameraFrameTime = now
        cameraFrameCount++
    }

    /**
     * Call when a frame is dropped because the detector is still running previous inference.
     */
    fun onFrameDropped() {
        droppedFramesCount++
    }

    /**
     * Call when detection finishes with actual inference time in milliseconds.
     */
    fun onDetectionComplete(durationMs: Long) {
        val now = SystemClock.elapsedRealtime()
        lastInferenceDurationMs = durationMs
        totalInferenceTimeSum += durationMs

        if (lastDetectionTime > 0) {
            val delta = now - lastDetectionTime
            if (delta > 0) {
                val instantFps = 1000.0 / delta
                smoothedDetectionFps = if (smoothedDetectionFps == 0.0) {
                    instantFps
                } else {
                    (smoothedDetectionFps * (1.0 - fpsSmoothingFactor)) + (instantFps * fpsSmoothingFactor)
                }
            }
        }
        lastDetectionTime = now
        detectionCount++
    }

    /**
     * Call when overlay tracking finishes rendering a frame.
     */
    fun onTrackingRendered() {
        val now = SystemClock.elapsedRealtime()
        if (lastTrackingTime > 0) {
            val delta = now - lastTrackingTime
            if (delta > 0) {
                val instantFps = 1000.0 / delta
                smoothedTrackingFps = if (smoothedTrackingFps == 0.0) {
                    instantFps
                } else {
                    (smoothedTrackingFps * (1.0 - fpsSmoothingFactor)) + (instantFps * fpsSmoothingFactor)
                }
            }
        }
        lastTrackingTime = now
        trackingCount++
    }

    /**
     * Gets a snapshot of current real performance metrics.
     */
    fun getMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            cameraFps = String.format("%.1f", smoothedCameraFps.coerceAtLeast(0.0)).toDoubleOrNull() ?: 0.0,
            detectionFps = String.format("%.1f", smoothedDetectionFps.coerceAtLeast(0.0)).toDoubleOrNull() ?: 0.0,
            trackingFps = String.format("%.1f", smoothedTrackingFps.coerceAtLeast(0.0)).toDoubleOrNull() ?: 0.0,
            inferenceTimeMs = lastInferenceDurationMs,
            droppedFrames = droppedFramesCount,
            totalProcessedFrames = detectionCount
        )
    }

    fun reset() {
        lastCameraFrameTime = 0L
        lastDetectionTime = 0L
        lastTrackingTime = 0L
        cameraFrameCount = 0L
        detectionCount = 0L
        trackingCount = 0L
        droppedFramesCount = 0L
        totalInferenceTimeSum = 0L
        lastInferenceDurationMs = 0L
        smoothedCameraFps = 0.0
        smoothedDetectionFps = 0.0
        smoothedTrackingFps = 0.0
    }
}

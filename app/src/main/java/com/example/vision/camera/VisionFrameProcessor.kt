package com.example.vision.camera

import androidx.camera.core.ImageProxy
import com.example.vision.model.PerformanceMetrics
import com.example.vision.model.TrackedObject
import com.example.vision.model.VisionSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for vision frame processing engines.
 * This modular design allows swapping or chaining new computer vision processors in later prompts.
 */
interface VisionFrameProcessor {
    val trackedObjects: StateFlow<List<TrackedObject>>
    val performanceMetrics: StateFlow<PerformanceMetrics>

    fun updateSettings(settings: VisionSettings)
    fun updateViewDimensions(width: Float, height: Float)
    fun processImage(imageProxy: ImageProxy)
    fun release()
}

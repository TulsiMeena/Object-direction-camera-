package com.example.vision.hand.processor

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.vision.camera.VisionFrameProcessor
import com.example.vision.hand.detector.HandTrackingDetector
import com.example.vision.hand.detector.MediaPipeHandLandmarkerHelper
import com.example.vision.hand.drawing.AirDrawingEngine
import com.example.vision.hand.model.AirDrawingSettings
import com.example.vision.hand.model.DrawingHandPreference
import com.example.vision.hand.model.HandLandmarkType
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.model.Handedness
import com.example.vision.hand.model.LandmarkPoint3D
import com.example.vision.hand.smoothing.LandmarkSmoother
import com.example.vision.model.PerformanceMetrics
import com.example.vision.model.TrackedObject
import com.example.vision.model.VisionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance frame processor for real-time Hand Tracking and Air Drawing.
 * Coordinates MediaPipe Tasks Vision inference, coordinate mapping, temporal smoothing,
 * pinch detection, drawing stroke generation, and live FPS telemetry.
 */
class HandTrackingFrameProcessor(
    private val context: Context,
    customDetector: HandTrackingDetector? = null
) : VisionFrameProcessor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val detector: HandTrackingDetector = customDetector ?: MediaPipeHandLandmarkerHelper(context)
    private val smoother = LandmarkSmoother()
    val drawingEngine = AirDrawingEngine()

    private val isBusy = AtomicBoolean(false)
    private var viewWidth = 1080f
    private var viewHeight = 2400f
    private var isFrontCamera = false

    private val _handPoses = MutableStateFlow<List<HandPose>>(emptyList())
    val handPoses: StateFlow<List<HandPose>> = _handPoses.asStateFlow()

    private val _airDrawingSettings = MutableStateFlow(AirDrawingSettings())
    val airDrawingSettings: StateFlow<AirDrawingSettings> = _airDrawingSettings.asStateFlow()

    // For interface compatibility
    private val _trackedObjects = MutableStateFlow<List<TrackedObject>>(emptyList())
    override val trackedObjects: StateFlow<List<TrackedObject>> = _trackedObjects.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    override val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    // FPS & Latency counters
    private val totalProcessedFrames = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private var lastCameraFrameTime = 0L
    private var lastDetectionTime = 0L
    private var cameraFpsRolling = 30.0
    private var trackingFpsRolling = 30.0
    private var lastInferenceTimeMs = 0L

    init {
        // Collect raw detector outputs and transform to display coordinates + smoothing
        scope.launch {
            detector.handLandmarkerResult.collect { rawPoses ->
                val now = SystemClock.elapsedRealtime()
                if (lastDetectionTime > 0) {
                    val dt = (now - lastDetectionTime).coerceAtLeast(1)
                    val instantFps = 1000.0 / dt
                    trackingFpsRolling = trackingFpsRolling * 0.85 + instantFps * 0.15
                }
                lastDetectionTime = now

                processDetectedHands(rawPoses)
                updatePerformanceMetrics()
            }
        }
    }

    override fun updateSettings(settings: VisionSettings) {
        this.isFrontCamera = settings.isFrontCamera
    }

    fun updateAirDrawingSettings(settings: AirDrawingSettings) {
        _airDrawingSettings.value = settings
    }

    override fun updateViewDimensions(width: Float, height: Float) {
        if (width > 0 && height > 0) {
            this.viewWidth = width
            this.viewHeight = height
        }
    }

    override fun processImage(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (lastCameraFrameTime > 0) {
            val dt = (now - lastCameraFrameTime).coerceAtLeast(1)
            val instantFps = 1000.0 / dt
            cameraFpsRolling = cameraFpsRolling * 0.9 + instantFps * 0.1
        }
        lastCameraFrameTime = now

        if (!_airDrawingSettings.value.isTrackingEnabled) {
            imageProxy.close()
            return
        }

        // Backpressure management: drop frame if inference is currently busy to keep latency minimal
        if (isBusy.compareAndSet(false, true)) {
            totalProcessedFrames.incrementAndGet()
            detector.processImageProxy(imageProxy, isFrontCamera) { inferenceMs ->
                lastInferenceTimeMs = inferenceMs
                isBusy.set(false)
            }
        } else {
            droppedFrames.incrementAndGet()
            imageProxy.close()
        }
    }

    private fun processDetectedHands(rawPoses: List<HandPose>) {
        if (rawPoses.isEmpty()) {
            _handPoses.value = emptyList()
            drawingEngine.onFingerMoved(
                point = null,
                isPinching = false,
                currentTool = _airDrawingSettings.value.currentTool,
                brushColor = _airDrawingSettings.value.brushColor,
                brushSize = _airDrawingSettings.value.brushSize
            )
            return
        }

        // Map normalized landmark coordinates to screen dimensions
        val mappedPoses = rawPoses.map { pose ->
            val mappedLandmarks = pose.landmarks.map { lm ->
                val screenX = if (isFrontCamera) {
                    (1.0f - lm.x) * viewWidth
                } else {
                    lm.x * viewWidth
                }
                val screenY = lm.y * viewHeight
                lm.copy(screenX = screenX, screenY = screenY)
            }

            val minX = mappedLandmarks.minOf { it.screenX }
            val maxX = mappedLandmarks.maxOf { it.screenX }
            val minY = mappedLandmarks.minOf { it.screenY }
            val maxY = mappedLandmarks.maxOf { it.screenY }

            val indexTip = mappedLandmarks.getOrNull(HandLandmarkType.INDEX_TIP.index)
            val thumbTip = mappedLandmarks.getOrNull(HandLandmarkType.THUMB_TIP.index)
            val wrist = mappedLandmarks.getOrNull(HandLandmarkType.WRIST.index)

            pose.copy(
                landmarks = mappedLandmarks,
                boundingBox = RectF(minX, minY, maxX, maxY),
                indexTipPosition = PointF(indexTip?.screenX ?: 0f, indexTip?.screenY ?: 0f),
                thumbTipPosition = PointF(thumbTip?.screenX ?: 0f, thumbTip?.screenY ?: 0f),
                wristPosition = PointF(wrist?.screenX ?: 0f, wrist?.screenY ?: 0f)
            )
        }

        // Apply temporal smoothing & hysteresis pinch detection
        val smoothedPoses = smoother.smooth(
            rawHandPoses = mappedPoses,
            smoothingLevel = _airDrawingSettings.value.smoothingLevel
        )
        _handPoses.value = smoothedPoses

        // Determine drawing pointer hand
        val settings = _airDrawingSettings.value
        if (settings.isAirDrawingActive) {
            val drawingHand = when (settings.drawingHandPreference) {
                DrawingHandPreference.RIGHT -> smoothedPoses.firstOrNull { it.handedness == Handedness.RIGHT } ?: smoothedPoses.firstOrNull()
                DrawingHandPreference.LEFT -> smoothedPoses.firstOrNull { it.handedness == Handedness.LEFT } ?: smoothedPoses.firstOrNull()
                DrawingHandPreference.AUTO -> smoothedPoses.firstOrNull { it.handedness == Handedness.RIGHT } ?: smoothedPoses.firstOrNull()
            }

            if (drawingHand != null && drawingHand.isValid) {
                val indexTip = drawingHand.landmarks[HandLandmarkType.INDEX_TIP.index]
                val point = PointF(indexTip.screenX, indexTip.screenY)
                drawingEngine.onFingerMoved(
                    point = point,
                    isPinching = drawingHand.isPinching,
                    currentTool = settings.currentTool,
                    brushColor = settings.brushColor,
                    brushSize = settings.brushSize
                )
            } else {
                drawingEngine.onFingerMoved(
                    point = null,
                    isPinching = false,
                    currentTool = settings.currentTool,
                    brushColor = settings.brushColor,
                    brushSize = settings.brushSize
                )
            }
        }
    }

    private fun updatePerformanceMetrics() {
        _performanceMetrics.value = PerformanceMetrics(
            cameraFps = (cameraFpsRolling * 10).toInt() / 10.0,
            detectionFps = (trackingFpsRolling * 10).toInt() / 10.0,
            trackingFps = (trackingFpsRolling * 10).toInt() / 10.0,
            inferenceTimeMs = lastInferenceTimeMs,
            droppedFrames = droppedFrames.get(),
            totalProcessedFrames = totalProcessedFrames.get()
        )
    }

    override fun release() {
        scope.cancel()
        detector.close()
        smoother.reset()
        isBusy.set(false)
    }
}

package com.example.vision.hand.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.model.Handedness
import com.example.vision.hand.model.LandmarkPoint3D
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface for Hand Tracking detector
 */
interface HandTrackingDetector {
    fun processImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean, onInferenceComplete: (Long) -> Unit)
    val handLandmarkerResult: StateFlow<List<HandPose>>
    fun close()
}

/**
 * MediaPipe Tasks Vision Hand Landmarker wrapper.
 * Runs on-device with GPU delegate if supported, falling back to CPU.
 * Tracks up to 2 hands simultaneously with 21 landmarks per hand in real-time stream mode.
 */
class MediaPipeHandLandmarkerHelper(
    private val context: Context,
    private val minHandDetectionConfidence: Float = 0.5f,
    private val minHandTrackingConfidence: Float = 0.5f,
    private val minHandPresenceConfidence: Float = 0.5f,
    private val maxNumHands: Int = 2
) : HandTrackingDetector {

    private var handLandmarker: HandLandmarker? = null
    private val isInitialized = AtomicBoolean(false)
    private var lastInferenceStartTime = 0L
    private var onInferenceCompleteCallback: ((Long) -> Unit)? = null

    private val _handLandmarkerResult = MutableStateFlow<List<HandPose>>(emptyList())
    override val handLandmarkerResult: StateFlow<List<HandPose>> = _handLandmarkerResult.asStateFlow()

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            // Try GPU first
            setupWithDelegate(Delegate.GPU)
        } catch (e: Exception) {
            // Fallback to CPU
            try {
                setupWithDelegate(Delegate.CPU)
            } catch (fallbackEx: Exception) {
                // Keep resilient
            }
        }
    }

    private fun setupWithDelegate(delegate: Delegate) {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(delegate)

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinHandDetectionConfidence(minHandDetectionConfidence)
            .setMinTrackingConfidence(minHandTrackingConfidence)
            .setMinHandPresenceConfidence(minHandPresenceConfidence)
            .setNumHands(maxNumHands)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: HandLandmarkerResult, inputImage: MPImage ->
                val inferenceDuration = SystemClock.elapsedRealtime() - lastInferenceStartTime
                onInferenceCompleteCallback?.invoke(inferenceDuration)
                handleLandmarkerResult(result)
            }
            .setErrorListener { error: RuntimeException ->
                // Log and gracefully handle stream error
            }

        handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        isInitialized.set(true)
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun processImageProxy(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean,
        onInferenceComplete: (Long) -> Unit
    ) {
        if (!isInitialized.get() || handLandmarker == null) {
            imageProxy.close()
            return
        }

        this.onInferenceCompleteCallback = onInferenceComplete
        lastInferenceStartTime = SystemClock.elapsedRealtime()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val mpImage = MediaImageBuilder(mediaImage).build()
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            val frameTimestampMs = SystemClock.uptimeMillis()
            handLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTimestampMs)
        } catch (e: Exception) {
            // Error handling
        } finally {
            imageProxy.close()
        }
    }

    private fun handleLandmarkerResult(result: HandLandmarkerResult) {
        val landmarksList = result.landmarks()
        val handednesses = result.handednesses()

        if (landmarksList.isNullOrEmpty()) {
            _handLandmarkerResult.value = emptyList()
            return
        }

        val handPoses = mutableListOf<HandPose>()

        for (i in landmarksList.indices) {
            val landmarks = landmarksList[i]
            if (landmarks.size != 21) continue

            // Determine handedness & score
            var handedness = Handedness.UNKNOWN
            var confidence = 0.85f

            if (i < handednesses.size && handednesses[i].isNotEmpty()) {
                val category = handednesses[i][0]
                confidence = category.score()
                val categoryName = category.categoryName().lowercase()
                handedness = when {
                    categoryName.contains("right") -> Handedness.RIGHT
                    categoryName.contains("left") -> Handedness.LEFT
                    else -> Handedness.UNKNOWN
                }
            }

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            val landmarkPoints = landmarks.map { lm ->
                val x = lm.x()
                val y = lm.y()
                val z = lm.z()

                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                LandmarkPoint3D(x = x, y = y, z = z)
            }

            val boundingBox = RectF(minX, minY, maxX, maxY)

            handPoses.add(
                HandPose(
                    handedness = handedness,
                    confidence = confidence,
                    landmarks = landmarkPoints,
                    boundingBox = boundingBox
                )
            )
        }

        _handLandmarkerResult.value = handPoses
    }

    override fun close() {
        try {
            isInitialized.set(false)
            handLandmarker?.close()
            handLandmarker = null
        } catch (_: Exception) {}
    }
}

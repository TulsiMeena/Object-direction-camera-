package com.example.vision.hand.smoothing

import android.graphics.PointF
import com.example.vision.hand.model.HandLandmarkType
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.model.Handedness
import com.example.vision.hand.model.LandmarkPoint3D
import com.example.vision.hand.model.SmoothingLevel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Adaptive temporal filter and jitter reducer for 21 3D hand landmarks.
 * Dynamically scales smoothing based on landmark movement velocity:
 * - Slow movement: Higher smoothing to eliminate camera sensor micro-jitter.
 * - Fast movement: Lower smoothing to retain crisp, lag-free responsiveness.
 */
class LandmarkSmoother {

    // Previous raw landmarks for temporal smoothing
    private val previousLandmarks = mutableMapOf<Handedness, List<LandmarkPoint3D>>()
    private val previousPinchState = mutableMapOf<Handedness, Boolean>()

    companion object {
        // Pinch thresholds normalized by palm scale (wrist to middle_mcp distance)
        private const val PINCH_ENGAGE_THRESHOLD = 0.34f
        private const val PINCH_RELEASE_THRESHOLD = 0.46f
    }

    /**
     * Smooths hand landmarks and detects pinch state with hysteresis.
     */
    fun smooth(
        rawHandPoses: List<HandPose>,
        smoothingLevel: SmoothingLevel
    ): List<HandPose> {
        val smoothedPoses = mutableListOf<HandPose>()

        for (pose in rawHandPoses) {
            val handedness = pose.handedness
            val currentLandmarks = pose.landmarks
            val previous = previousLandmarks[handedness]

            val smoothedLandmarks = if (previous == null || previous.size != 21 || currentLandmarks.size != 21) {
                currentLandmarks
            } else {
                currentLandmarks.mapIndexed { idx, curr ->
                    val prev = previous[idx]
                    // Calculate normalized velocity
                    val dist = hypot(curr.x - prev.x, curr.y - prev.y)

                    // Adaptive alpha: fast movements get higher alpha (less smoothing/lag)
                    val baseAlpha = 1.0f - smoothingLevel.factor * 0.6f
                    val dynamicAlpha = (baseAlpha + (dist * 4.0f)).coerceIn(0.25f, 0.95f)

                    val smoothX = prev.x + (curr.x - prev.x) * dynamicAlpha
                    val smoothY = prev.y + (curr.y - prev.y) * dynamicAlpha
                    val smoothZ = prev.z + (curr.z - prev.z) * dynamicAlpha
                    val smoothScreenX = prev.screenX + (curr.screenX - prev.screenX) * dynamicAlpha
                    val smoothScreenY = prev.screenY + (curr.screenY - prev.screenY) * dynamicAlpha

                    LandmarkPoint3D(
                        x = smoothX,
                        y = smoothY,
                        z = smoothZ,
                        screenX = smoothScreenX,
                        screenY = smoothScreenY
                    )
                }
            }

            previousLandmarks[handedness] = smoothedLandmarks

            // Compute normalized pinch metric
            val wrist = smoothedLandmarks[HandLandmarkType.WRIST.index]
            val middleMcp = smoothedLandmarks[HandLandmarkType.MIDDLE_MCP.index]
            val palmScale = hypot(middleMcp.x - wrist.x, middleMcp.y - wrist.y).coerceAtLeast(0.05f)

            val thumbTip = smoothedLandmarks[HandLandmarkType.THUMB_TIP.index]
            val indexTip = smoothedLandmarks[HandLandmarkType.INDEX_TIP.index]
            val rawPinchDistance = hypot(thumbTip.x - indexTip.x, thumbTip.y - indexTip.y)
            val normalizedPinchDistance = rawPinchDistance / palmScale

            val wasPinching = previousPinchState[handedness] ?: false
            val isNowPinching = if (wasPinching) {
                // Must exceed release threshold to stop pinching (hysteresis)
                normalizedPinchDistance <= PINCH_RELEASE_THRESHOLD
            } else {
                // Must be tighter than engage threshold to start pinching
                normalizedPinchDistance <= PINCH_ENGAGE_THRESHOLD
            }
            previousPinchState[handedness] = isNowPinching

            val indexTipPos = PointF(indexTip.screenX, indexTip.screenY)
            val thumbTipPos = PointF(thumbTip.screenX, thumbTip.screenY)
            val wristPos = PointF(wrist.screenX, wrist.screenY)

            smoothedPoses.add(
                pose.copy(
                    landmarks = smoothedLandmarks,
                    isPinching = isNowPinching,
                    pinchDistance = normalizedPinchDistance,
                    indexTipPosition = indexTipPos,
                    thumbTipPosition = thumbTipPos,
                    wristPosition = wristPos
                )
            )
        }

        // Clean up handedness keys not seen in this frame
        val currentKeys = rawHandPoses.map { it.handedness }.toSet()
        previousLandmarks.keys.retainAll(currentKeys)
        previousPinchState.keys.retainAll(currentKeys)

        return smoothedPoses
    }

    fun reset() {
        previousLandmarks.clear()
        previousPinchState.clear()
    }
}

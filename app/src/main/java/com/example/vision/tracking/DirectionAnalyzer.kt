package com.example.vision.tracking

import android.graphics.PointF
import com.example.vision.model.Direction
import com.example.vision.model.MovementStatus
import com.example.vision.model.TimestampedPoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Multi-frame temporal direction and motion analyzer with hysteresis.
 */
class DirectionAnalyzer {

    companion object {
        // Minimum pixels moved across temporal window to qualify as MOVING in sensor coordinates
        private const val MOVEMENT_THRESHOLD_PX = 18f
        // Hysteresis threshold to return to STATIONARY
        private const val STATIONARY_THRESHOLD_PX = 10f
        // Dominant axis ratio threshold (e.g. deltaX must be 1.4x deltaY to be clearly horizontal)
        private const val AXIS_DOMINANCE_RATIO = 1.35f
        // Time window in ms for direction velocity calculation
        private const val TIME_WINDOW_MS = 600L
    }

    data class AnalysisResult(
        val direction: Direction,
        val movementStatus: MovementStatus,
        val speedPxPerSec: Float
    )

    /**
     * Analyzes trajectory history to compute smooth direction and status.
     */
    fun analyze(
        history: List<TimestampedPoint>,
        previousDirection: Direction,
        previousStatus: MovementStatus
    ): AnalysisResult {
        if (history.size < 3) {
            return AnalysisResult(
                direction = Direction.STATIONARY,
                movementStatus = MovementStatus.STATIONARY,
                speedPxPerSec = 0f
            )
        }

        val now = history.last().timestamp
        val recentPoints = history.filter { (now - it.timestamp) <= TIME_WINDOW_MS }

        if (recentPoints.size < 2) {
            return AnalysisResult(
                direction = previousDirection,
                movementStatus = previousStatus,
                speedPxPerSec = 0f
            )
        }

        val oldestPoint = recentPoints.first()
        val latestPoint = recentPoints.last()

        val deltaX = latestPoint.point.x - oldestPoint.point.x
        val deltaY = latestPoint.point.y - oldestPoint.point.y
        val totalDistance = hypot(deltaX, deltaY)
        val timeElapsedSec = ((latestPoint.timestamp - oldestPoint.timestamp).coerceAtLeast(1L)) / 1000f
        val speedPxPerSec = totalDistance / timeElapsedSec

        // Motion status calculation with hysteresis
        val isCurrentlyMoving = when (previousStatus) {
            MovementStatus.MOVING -> totalDistance > STATIONARY_THRESHOLD_PX
            MovementStatus.STATIONARY -> totalDistance > MOVEMENT_THRESHOLD_PX
        }

        val status = if (isCurrentlyMoving) MovementStatus.MOVING else MovementStatus.STATIONARY

        if (status == MovementStatus.STATIONARY) {
            return AnalysisResult(
                direction = Direction.STATIONARY,
                movementStatus = MovementStatus.STATIONARY,
                speedPxPerSec = speedPxPerSec
            )
        }

        val absX = abs(deltaX)
        val absY = abs(deltaY)

        val candidateDirection = if (absX > absY * AXIS_DOMINANCE_RATIO) {
            if (deltaX > 0) Direction.RIGHT else Direction.LEFT
        } else if (absY > absX * AXIS_DOMINANCE_RATIO) {
            if (deltaY > 0) Direction.DOWN else Direction.UP
        } else {
            // Mixed vector: retain previous non-stationary direction if still aligning, or pick primary component
            if (absX >= absY) {
                if (deltaX > 0) Direction.RIGHT else Direction.LEFT
            } else {
                if (deltaY > 0) Direction.DOWN else Direction.UP
            }
        }

        return AnalysisResult(
            direction = candidateDirection,
            movementStatus = status,
            speedPxPerSec = speedPxPerSec
        )
    }
}

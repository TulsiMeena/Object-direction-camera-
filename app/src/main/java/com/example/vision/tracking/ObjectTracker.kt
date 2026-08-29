package com.example.vision.tracking

import android.graphics.PointF
import android.graphics.RectF
import com.example.vision.model.RawDetection
import com.example.vision.model.TimestampedPoint
import com.example.vision.model.TrackedObject
import com.example.vision.util.CoordinateTransformer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time object tracker that assigns persistent, formatted IDs (e.g. "CAR #01", "PERSON #01"),
 * tracks trajectories across frames, and updates direction & movement states.
 */
class ObjectTracker(
    private val directionAnalyzer: DirectionAnalyzer = DirectionAnalyzer()
) {

    companion object {
        private const val MAX_HISTORY_POINTS = 20
        private const val OBJECT_EXPIRATION_TIME_MS = 600L
        private const val MIN_IOU_MATCH_THRESHOLD = 0.25f
        private const val MAX_CENTER_DISTANCE_PX = 180f
    }

    private val categoryCounters = mutableMapOf<String, Int>()
    private val activeTrackedObjects = mutableMapOf<Int, TrackedObject>()
    private var nextSyntheticId = 1000

    /**
     * Updates tracked objects with new raw detections from current frame.
     */
    fun update(
        rawDetections: List<RawDetection>,
        coordinateTransformer: CoordinateTransformer,
        now: Long = System.currentTimeMillis()
    ): List<TrackedObject> {
        val updatedMap = mutableMapOf<Int, TrackedObject>()
        val unmatchedDetections = rawDetections.toMutableList()

        // 1. Try to match incoming detections to existing active tracked objects
        for ((existingId, existingObj) in activeTrackedObjects) {
            var bestMatchIndex = -1
            var bestScore = 0f

            for (i in unmatchedDetections.indices) {
                val candidate = unmatchedDetections[i]

                // Direct ML Kit tracking ID match
                if (candidate.internalId == existingObj.rawTrackingId && candidate.internalId > 0) {
                    bestMatchIndex = i
                    bestScore = 1.0f
                    break
                }

                // Or fallback to category + spatial match (IoU & center distance)
                if (candidate.categoryName == existingObj.label) {
                    val iou = calculateIoU(existingObj.sensorBoundingBox, candidate.boundingBox)
                    val centerDist = calculateCenterDistance(existingObj.sensorBoundingBox, candidate.boundingBox)

                    if (iou >= MIN_IOU_MATCH_THRESHOLD || centerDist <= MAX_CENTER_DISTANCE_PX) {
                        val score = iou + (1f - (centerDist / MAX_CENTER_DISTANCE_PX).coerceIn(0f, 1f))
                        if (score > bestScore) {
                            bestScore = score
                            bestMatchIndex = i
                        }
                    }
                }
            }

            if (bestMatchIndex >= 0) {
                val matched = unmatchedDetections.removeAt(bestMatchIndex)
                val center = PointF(matched.boundingBox.centerX(), matched.boundingBox.centerY())

                val newHistory = (existingObj.history + TimestampedPoint(center, now))
                    .takeLast(MAX_HISTORY_POINTS)

                val analysis = directionAnalyzer.analyze(
                    history = newHistory,
                    previousDirection = existingObj.direction,
                    previousStatus = existingObj.movementStatus
                )

                val displayRect = coordinateTransformer.transformRect(matched.boundingBox)

                updatedMap[existingId] = existingObj.copy(
                    confidence = matched.confidence,
                    sensorBoundingBox = matched.boundingBox,
                    displayBoundingBox = displayRect,
                    direction = analysis.direction,
                    movementStatus = analysis.movementStatus,
                    history = newHistory,
                    speedPxPerSec = analysis.speedPxPerSec,
                    lastSeenTimestamp = now
                )
            } else {
                // Not seen in this frame, keep if still within expiration window
                if ((now - existingObj.lastSeenTimestamp) < OBJECT_EXPIRATION_TIME_MS) {
                    val displayRect = coordinateTransformer.transformRect(existingObj.sensorBoundingBox)
                    updatedMap[existingId] = existingObj.copy(
                        displayBoundingBox = displayRect
                    )
                }
            }
        }

        // 2. Create new tracked objects for remaining unmatched detections
        for (newDet in unmatchedDetections) {
            val key = if (newDet.internalId > 0) newDet.internalId else nextSyntheticId++
            val category = newDet.categoryName
            val currentCounter = (categoryCounters[category] ?: 0) + 1
            categoryCounters[category] = currentCounter

            val formattedId = String.format("%s #%02d", category, currentCounter)
            val center = PointF(newDet.boundingBox.centerX(), newDet.boundingBox.centerY())
            val displayRect = coordinateTransformer.transformRect(newDet.boundingBox)

            updatedMap[key] = TrackedObject(
                trackingId = formattedId,
                rawTrackingId = newDet.internalId,
                label = category,
                confidence = newDet.confidence,
                sensorBoundingBox = newDet.boundingBox,
                displayBoundingBox = displayRect,
                direction = com.example.vision.model.Direction.STATIONARY,
                movementStatus = com.example.vision.model.MovementStatus.STATIONARY,
                history = listOf(TimestampedPoint(center, now)),
                speedPxPerSec = 0f,
                lastSeenTimestamp = now
            )
        }

        activeTrackedObjects.clear()
        activeTrackedObjects.putAll(updatedMap)

        return activeTrackedObjects.values.toList()
    }

    /**
     * Clears all active tracking history and counters.
     */
    fun reset() {
        activeTrackedObjects.clear()
        categoryCounters.clear()
        nextSyntheticId = 1000
    }

    private fun calculateIoU(rect1: RectF, rect2: RectF): Float {
        val intersectionLeft = max(rect1.left, rect2.left)
        val intersectionTop = max(rect1.top, rect2.top)
        val intersectionRight = min(rect1.right, rect2.right)
        val intersectionBottom = min(rect1.bottom, rect2.bottom)

        val intersectionArea = max(0f, intersectionRight - intersectionLeft) * max(0f, intersectionBottom - intersectionTop)
        val area1 = rect1.width() * rect1.height()
        val area2 = rect2.width() * rect2.height()
        val unionArea = area1 + area2 - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    private fun calculateCenterDistance(rect1: RectF, rect2: RectF): Float {
        val dx = rect1.centerX() - rect2.centerX()
        val dy = rect1.centerY() - rect2.centerY()
        return hypot(dx, dy)
    }
}

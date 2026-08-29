package com.example.vision.tracking

import android.graphics.PointF
import android.graphics.RectF
import com.example.vision.model.Direction
import com.example.vision.model.LockedTarget
import com.example.vision.model.MovementStatus
import com.example.vision.model.TrackedObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Dedicated Target Lock and Tracking Engine.
 * Allows user to lock onto a specific detected object, maintains identity,
 * and performs short-term predictive recovery if temporarily occluded.
 */
class TargetLockEngine {

    companion object {
        private const val MAX_RECOVERY_TIME_MS = 1400L
        private const val MIN_LOCK_CONFIDENCE = 0.30f
        private const val MAX_TRACKING_HISTORY = 30
    }

    private val _lockedTarget = MutableStateFlow<LockedTarget?>(null)
    val lockedTarget: StateFlow<LockedTarget?> = _lockedTarget.asStateFlow()

    private var targetTrackingId: String? = null
    private var lastVelocityX = 0f
    private var lastVelocityY = 0f
    private var lastFrameTime = 0L

    fun lockOnObject(obj: TrackedObject) {
        targetTrackingId = obj.trackingId
        val center = PointF(obj.displayBoundingBox.centerX(), obj.displayBoundingBox.centerY())
        lastVelocityX = 0f
        lastVelocityY = 0f
        lastFrameTime = System.currentTimeMillis()

        _lockedTarget.value = LockedTarget(
            targetId = obj.trackingId,
            label = obj.label,
            boundingBox = RectF(obj.displayBoundingBox),
            direction = obj.direction,
            movementStatus = obj.movementStatus,
            confidence = obj.confidence,
            speedPxPerSec = obj.speedPxPerSec,
            isRecovering = false,
            lastSeenTimestamp = System.currentTimeMillis(),
            trajectory = listOf(center)
        )
    }

    fun unlock() {
        targetTrackingId = null
        _lockedTarget.value = null
    }

    fun updateWithFrame(activeObjects: List<TrackedObject>, now: Long = System.currentTimeMillis()) {
        val currentLock = _lockedTarget.value ?: return
        val targetId = targetTrackingId ?: return

        // 1. Check for direct match by ID
        val directMatch = activeObjects.find { it.trackingId == targetId }

        if (directMatch != null && directMatch.confidence >= MIN_LOCK_CONFIDENCE) {
            val newCenter = PointF(directMatch.displayBoundingBox.centerX(), directMatch.displayBoundingBox.centerY())
            val oldCenter = currentLock.trajectory.lastOrNull() ?: newCenter

            val dt = ((now - lastFrameTime).coerceAtLeast(1L)) / 1000f
            if (dt > 0f) {
                lastVelocityX = (newCenter.x - oldCenter.x) / dt
                lastVelocityY = (newCenter.y - oldCenter.y) / dt
            }
            lastFrameTime = now

            val updatedTrajectory = (currentLock.trajectory + newCenter).takeLast(MAX_TRACKING_HISTORY)

            _lockedTarget.value = currentLock.copy(
                boundingBox = RectF(directMatch.displayBoundingBox),
                direction = directMatch.direction,
                movementStatus = directMatch.movementStatus,
                confidence = directMatch.confidence,
                speedPxPerSec = directMatch.speedPxPerSec,
                isRecovering = false,
                lastSeenTimestamp = now,
                trajectory = updatedTrajectory
            )
            return
        }

        // 2. Spatial proximity fallback with matching label
        val nearbyMatch = activeObjects.filter { it.label == currentLock.label }
            .minByOrNull { calculateDistance(it.displayBoundingBox, currentLock.boundingBox) }

        if (nearbyMatch != null && calculateDistance(nearbyMatch.displayBoundingBox, currentLock.boundingBox) < 180f) {
            val newCenter = PointF(nearbyMatch.displayBoundingBox.centerX(), nearbyMatch.displayBoundingBox.centerY())
            val updatedTrajectory = (currentLock.trajectory + newCenter).takeLast(MAX_TRACKING_HISTORY)

            _lockedTarget.value = currentLock.copy(
                boundingBox = RectF(nearbyMatch.displayBoundingBox),
                direction = nearbyMatch.direction,
                movementStatus = nearbyMatch.movementStatus,
                confidence = nearbyMatch.confidence,
                speedPxPerSec = nearbyMatch.speedPxPerSec,
                isRecovering = false,
                lastSeenTimestamp = now,
                trajectory = updatedTrajectory
            )
            return
        }

        // 3. Predictive short-term recovery using linear velocity extrapolation
        val elapsedSinceSeen = now - currentLock.lastSeenTimestamp
        if (elapsedSinceSeen <= MAX_RECOVERY_TIME_MS) {
            val dtSec = elapsedSinceSeen / 1000f
            val predictedBox = RectF(currentLock.boundingBox)
            predictedBox.offset(lastVelocityX * (dtSec * 0.2f), lastVelocityY * (dtSec * 0.2f))

            _lockedTarget.value = currentLock.copy(
                boundingBox = predictedBox,
                isRecovering = true,
                confidence = max(0.1f, currentLock.confidence * 0.85f)
            )
        } else {
            // Target lost beyond recovery threshold -> cleanly release lock
            unlock()
        }
    }

    private fun calculateDistance(r1: RectF, r2: RectF): Float {
        val dx = r1.centerX() - r2.centerX()
        val dy = r1.centerY() - r2.centerY()
        return hypot(dx, dy)
    }
}

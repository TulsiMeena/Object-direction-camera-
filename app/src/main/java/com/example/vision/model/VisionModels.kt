package com.example.vision.model

import android.graphics.PointF
import android.graphics.RectF

/**
 * Detection confidence threshold levels.
 */
enum class ConfidenceLevel(val threshold: Float, val label: String) {
    LOW(0.35f, "Low (35%)"),
    MEDIUM(0.55f, "Medium (55%)"),
    HIGH(0.75f, "High (75%)")
}

/**
 * 2D image-plane movement direction.
 */
enum class Direction(val symbol: String, val displayName: String) {
    LEFT("←", "LEFT"),
    RIGHT("→", "RIGHT"),
    UP("↑", "UP"),
    DOWN("↓", "DOWN"),
    STATIONARY("—", "STATIONARY"),
    UNKNOWN("—", "UNKNOWN")
}

/**
 * Object movement status based on temporal movement analysis.
 */
enum class MovementStatus(val displayName: String) {
    MOVING("MOVING"),
    STATIONARY("STATIONARY")
}

/**
 * Raw detection result directly from the vision model.
 */
data class RawDetection(
    val internalId: Int,
    val categoryName: String,
    val confidence: Float,
    val boundingBox: RectF,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Point in time for tracking history.
 */
data class TimestampedPoint(
    val point: PointF,
    val timestamp: Long
)

/**
 * A persistent tracked object across multiple camera frames with smoothed trajectory & movement metrics.
 */
data class TrackedObject(
    val trackingId: String,          // Formatted: e.g. "CAR #01", "PERSON #02"
    val rawTrackingId: Int,
    val label: String,               // Formatted uppercase: e.g. "CAR", "PERSON"
    val confidence: Float,
    val sensorBoundingBox: RectF,    // Coordinates in camera sensor space
    val displayBoundingBox: RectF,   // Coordinates mapped to screen UI space
    val direction: Direction = Direction.STATIONARY,
    val movementStatus: MovementStatus = MovementStatus.STATIONARY,
    val history: List<TimestampedPoint> = emptyList(),
    val speedPxPerSec: Float = 0f,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Real runtime performance metrics measured from camera and inference pipeline.
 */
data class PerformanceMetrics(
    val cameraFps: Double = 0.0,
    val detectionFps: Double = 0.0,
    val trackingFps: Double = 0.0,
    val inferenceTimeMs: Long = 0L,
    val droppedFrames: Long = 0L,
    val totalProcessedFrames: Long = 0L
)

/**
 * User-configurable vision settings.
 */
data class VisionSettings(
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    val showPerformanceOverlay: Boolean = true,
    val isFrontCamera: Boolean = false,
    val isFlashlightOn: Boolean = false
)

/**
 * Measurement units supported by the Measure tool.
 */
enum class MeasurementUnit(val label: String, val symbol: String, val toMetersFactor: Float) {
    METERS("Meters", "m", 1.0f),
    CENTIMETERS("Centimeters", "cm", 0.01f),
    FEET("Feet", "ft", 0.3048f),
    INCHES("Inches", "in", 0.0254f)
}

/**
 * Modes of measurement.
 */
enum class MeasurementMode(val label: String, val icon: String) {
    DISTANCE_2PT("Point-to-Point", "📏"),
    OBJECT_DIMENSIONS("Height & Width", "📐"),
    SURFACE_AREA("Area Estimation", "⬜")
}

/**
 * 3D point in world/sensor space.
 */
data class MeasurementPoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val screenX: Float = 0f,
    val screenY: Float = 0f,
    val label: String = "POINT"
)

/**
 * Result of a point-to-point or geometric measurement.
 */
data class MeasurementResult(
    val distanceMeters: Float,
    val pointA: MeasurementPoint3D?,
    val pointB: MeasurementPoint3D?,
    val isEstimated: Boolean = true,
    val widthMeters: Float = 0f,
    val heightMeters: Float = 0f,
    val areaSqMeters: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Sampled color information from live camera frame.
 */
data class SampledColor(
    val name: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val hex: String,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val luminance: Float,
    val isApproximate: Boolean = true,
    val sampleX: Float = 0.5f, // Normalized 0..1
    val sampleY: Float = 0.5f  // Normalized 0..1
)

/**
 * Saved color swatch for palette history.
 */
data class ColorSwatch(
    val id: String,
    val color: SampledColor,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Direction / Compass heading state.
 */
enum class CardinalDirection(val symbol: String, val rangeStart: Float, val rangeEnd: Float) {
    N("N", 337.5f, 22.5f),
    NE("NE", 22.5f, 67.5f),
    E("E", 67.5f, 112.5f),
    SE("SE", 112.5f, 157.5f),
    S("S", 157.5f, 202.5f),
    SW("SW", 202.5f, 247.5f),
    W("W", 247.5f, 292.5f),
    NW("NW", 292.5f, 337.5f);

    companion object {
        fun fromHeading(heading: Float): CardinalDirection {
            val normalized = (heading % 360f + 360f) % 360f
            return when {
                normalized >= 337.5f || normalized < 22.5f -> N
                normalized >= 22.5f && normalized < 67.5f -> NE
                normalized >= 67.5f && normalized < 112.5f -> E
                normalized >= 112.5f && normalized < 157.5f -> SE
                normalized >= 157.5f && normalized < 202.5f -> S
                normalized >= 202.5f && normalized < 247.5f -> SW
                normalized >= 247.5f && normalized < 292.5f -> W
                else -> NW
            }
        }
    }
}

/**
 * Heading and sensor telemetry.
 */
data class CompassHeading(
    val azimuthDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val cardinal: CardinalDirection = CardinalDirection.N,
    val accuracy: Int = 3, // 0 = unreliable, 3 = high
    val isCalibrated: Boolean = true,
    val magneticFieldStrengthUf: Float = 45f
)

/**
 * Locked target status for Object Tracking tool.
 */
data class LockedTarget(
    val targetId: String,
    val label: String,
    val boundingBox: RectF,
    val direction: Direction,
    val movementStatus: MovementStatus,
    val confidence: Float,
    val speedPxPerSec: Float,
    val isRecovering: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val trajectory: List<PointF> = emptyList()
)

/**
 * Category filter for Smart Detect.
 */
enum class SmartDetectCategory(val displayName: String, val icon: String) {
    ALL("All", "✨"),
    VEHICLES("Vehicles", "🚗"),
    PEOPLE("People", "🧍"),
    ANIMALS("Animals", "🐕"),
    ELECTRONICS("Electronics", "💻"),
    FURNITURE("Furniture", "🪑"),
    OBJECTS("Objects", "📦")
}

/**
 * Feature items displayed on the main studio dashboard.
 */
data class VisionFeature(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val isEnabled: Boolean = false,
    val badge: String = "Coming Soon"
)

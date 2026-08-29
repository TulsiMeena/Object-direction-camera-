package com.example.vision.scan3d.model

import android.graphics.PointF

enum class ScanStatus {
    IDLE,
    SCANNING,
    PAUSED,
    FINISHED
}

enum class TrackingStatus {
    INITIALIZING,
    ACTIVE,
    GOOD,
    POOR,
    LOST
}

enum class PlaneType(val label: String) {
    FLOOR("Floor Surface"),
    CEILING("Ceiling Surface"),
    WALL("Wall Surface"),
    HORIZONTAL_UPWARD("Horizontal Up"),
    HORIZONTAL_DOWNWARD("Horizontal Down"),
    VERTICAL("Vertical Wall"),
    UNKNOWN("Unknown Surface")
}

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    fun distanceTo(other: Point3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Point3D(x * scale, y * scale, z * scale)
}

data class SpatialPoint(
    val point: Point3D,
    val confidence: Float = 1.0f
)

data class DetectedPlane(
    val id: String,
    val type: PlaneType,
    val center: Point3D,
    val normal: Point3D,
    val polygonPoints: List<Point3D>,
    val areaSquareMeters: Float,
    val extentX: Float,
    val extentZ: Float,
    val isSelected: Boolean = false,
    val trackingState: String = "TRACKING"
)

data class PointMeasurement(
    val pointA: Point3D,
    val pointB: Point3D,
    val screenPointA: PointF? = null,
    val screenPointB: PointF? = null,
    val distanceMeters: Float,
    val isEstimated: Boolean = true
)

data class RoomDimensions(
    val estimatedWidthM: Float,
    val estimatedLengthM: Float,
    val estimatedHeightM: Float,
    val floorAreaSqM: Float,
    val estimatedVolumeCuM: Float,
    val wallSurfaceAreaSqM: Float,
    val isEstimated: Boolean = true
)

data class SpatialMesh(
    val vertices: List<Point3D>,
    val normals: List<Point3D>,
    val faces: List<IntArray>, // Triangle vertex indices (0-based)
    val colors: List<Int>,     // ARGB colors per face or vertex
    val boundingBoxMin: Point3D,
    val boundingBoxMax: Point3D
)

data class GeneratedHouseModel(
    val id: String,
    val timestamp: Long,
    val roomDimensions: RoomDimensions,
    val detectedPlanes: List<DetectedPlane>,
    val mesh: SpatialMesh,
    val totalVertices: Int,
    val totalFaces: Int,
    val totalPlanes: Int,
    val scanCoveragePercent: Int,
    val pointCloudCount: Int
)

data class ScanPerformanceMetrics(
    val cameraFps: Int = 0,
    val arTrackingFps: Int = 0,
    val renderFps: Int = 0,
    val depthProcessingMs: Long = 0L,
    val meshProcessingMs: Long = 0L,
    val droppedFrames: Int = 0,
    val thermalState: String = "NOMINAL",
    val memoryUsageMb: Long = 0L
)

data class DeviceCapabilityReport(
    val isArCoreSupported: Boolean,
    val isArCoreInstalled: Boolean,
    val isDepthSupported: Boolean,
    val isCameraSupported: Boolean,
    val isSensorsSupported: Boolean,
    val failureReason: String? = null
)

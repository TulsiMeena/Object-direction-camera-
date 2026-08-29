package com.example.vision.measure

import android.graphics.PointF
import com.example.vision.model.MeasurementMode
import com.example.vision.model.MeasurementPoint3D
import com.example.vision.model.MeasurementResult
import com.example.vision.model.MeasurementUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Engine for Point-to-Point Distance, Object Height/Width, and Surface Area estimation.
 * Employs sensor fusion and perspective ray-plane intersection.
 */
class MeasureEngine {

    private val _currentUnit = MutableStateFlow(MeasurementUnit.METERS)
    val currentUnit: StateFlow<MeasurementUnit> = _currentUnit.asStateFlow()

    private val _currentMode = MutableStateFlow(MeasurementMode.DISTANCE_2PT)
    val currentMode: StateFlow<MeasurementMode> = _currentMode.asStateFlow()

    private val _pointA = MutableStateFlow<MeasurementPoint3D?>(null)
    val pointA: StateFlow<MeasurementPoint3D?> = _pointA.asStateFlow()

    private val _pointB = MutableStateFlow<MeasurementPoint3D?>(null)
    val pointB: StateFlow<MeasurementPoint3D?> = _pointB.asStateFlow()

    private val _measurementResult = MutableStateFlow<MeasurementResult?>(null)
    val measurementResult: StateFlow<MeasurementResult?> = _measurementResult.asStateFlow()

    private val _calibrationScale = MutableStateFlow(1.0f)
    val calibrationScale: StateFlow<Float> = _calibrationScale.asStateFlow()

    // Default assumed camera height off floor (meters)
    private var estimatedDeviceHeightMeters = 1.35f

    fun setUnit(unit: MeasurementUnit) {
        _currentUnit.value = unit
    }

    fun setMode(mode: MeasurementMode) {
        _currentMode.value = mode
        resetPoints()
    }

    fun setCalibrationScale(scale: Float) {
        _calibrationScale.value = scale.coerceIn(0.2f, 3.0f)
        recompute()
    }

    /**
     * Estimates 3D world coordinates from screen touch (screenX, screenY), device pitch/roll, and field of view.
     */
    fun placePoint(
        screenX: Float,
        screenY: Float,
        viewWidth: Float,
        viewHeight: Float,
        pitchDeg: Float = -20f,
        rollDeg: Float = 0f
    ) {
        val normX = (screenX / viewWidth.coerceAtLeast(1f)) * 2f - 1f
        val normY = (screenY / viewHeight.coerceAtLeast(1f)) * 2f - 1f

        // Perspective depth estimation based on ground intersection and pitch angle
        val pitchRad = Math.toRadians(pitchDeg.toDouble().coerceIn(-89.0, 89.0)).toFloat()
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)

        // Estimated depth from camera based on vertical look angle
        val verticalLookAngle = pitchRad + (normY * 0.45f)
        val estimatedDepth = if (abs(sin(verticalLookAngle)) > 0.05f) {
            (estimatedDeviceHeightMeters / abs(sin(verticalLookAngle))).coerceIn(0.3f, 15f)
        } else {
            2.0f
        }

        val worldX = normX * estimatedDepth * 0.55f * _calibrationScale.value
        val worldY = -normY * estimatedDepth * 0.55f * _calibrationScale.value
        val worldZ = -estimatedDepth * _calibrationScale.value

        val newPoint = MeasurementPoint3D(
            x = worldX,
            y = worldY,
            z = worldZ,
            screenX = screenX,
            screenY = screenY,
            label = if (_pointA.value == null) "POINT A" else "POINT B"
        )

        if (_pointA.value == null) {
            _pointA.value = newPoint
            _pointB.value = null
            _measurementResult.value = null
        } else if (_pointB.value == null) {
            _pointB.value = newPoint
            recompute()
        } else {
            // Both already set, restart with Point A
            _pointA.value = newPoint
            _pointB.value = null
            _measurementResult.value = null
        }
    }

    fun resetPoints() {
        _pointA.value = null
        _pointB.value = null
        _measurementResult.value = null
    }

    private fun recompute() {
        val a = _pointA.value ?: return
        val b = _pointB.value ?: return

        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z

        val rawDistance = sqrt(dx * dx + dy * dy + dz * dz)
        val distanceMeters = rawDistance * _calibrationScale.value
        val widthMeters = abs(dx) * _calibrationScale.value
        val heightMeters = abs(dy) * _calibrationScale.value
        val areaSqMeters = widthMeters * heightMeters

        _measurementResult.value = MeasurementResult(
            distanceMeters = distanceMeters,
            pointA = a,
            pointB = b,
            isEstimated = true,
            widthMeters = widthMeters,
            heightMeters = heightMeters,
            areaSqMeters = areaSqMeters
        )
    }

    /**
     * Formats distance value according to currently selected MeasurementUnit.
     */
    fun formatDistance(meters: Float): String {
        val unit = _currentUnit.value
        val converted = meters / unit.toMetersFactor
        return when (unit) {
            MeasurementUnit.METERS -> String.format("%.2f m", converted)
            MeasurementUnit.CENTIMETERS -> String.format("%.1f cm", converted)
            MeasurementUnit.FEET -> String.format("%.2f ft", converted)
            MeasurementUnit.INCHES -> String.format("%.1f in", converted)
        }
    }

    fun formatArea(sqMeters: Float): String {
        val unit = _currentUnit.value
        val converted = sqMeters / (unit.toMetersFactor * unit.toMetersFactor)
        return when (unit) {
            MeasurementUnit.METERS -> String.format("%.2f m²", converted)
            MeasurementUnit.CENTIMETERS -> String.format("%.0f cm²", converted)
            MeasurementUnit.FEET -> String.format("%.2f sq ft", converted)
            MeasurementUnit.INCHES -> String.format("%.1f sq in", converted)
        }
    }
}

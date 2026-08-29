package com.example.vision.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.vision.model.CardinalDirection
import com.example.vision.model.CompassHeading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sensor fusion manager for hardware Direction & Compass tracking.
 * Uses TYPE_ROTATION_VECTOR or ACCEL+MAG fusion with low-pass angular filtering.
 */
class CompassSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _heading = MutableStateFlow(CompassHeading())
    val heading: StateFlow<CompassHeading> = _heading.asStateFlow()

    private var isListening = false

    // Acceleration and Magnetometer storage for fallback fusion
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Vector smoothing across 360/0 degree wrap-around
    private var smoothedSin = 0f
    private var smoothedCos = 1f
    private val smoothingFactor = 0.18f

    private var smoothedPitch = 0f
    private var smoothedRoll = 0f

    fun startListening() {
        if (isListening || sensorManager == null) return

        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        isListening = true
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var azimuthDeg = 0f
        var pitchDeg = 0f
        var rollDeg = 0f
        var accuracy = event.accuracy
        var magFieldStrength = 45f

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)

            azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
            rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
            val mx = event.values[0]
            val my = event.values[1]
            val mz = event.values[2]
            magFieldStrength = sqrt(mx * mx + my * my + mz * mz)
        }

        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR && lastAccelerometerSet && lastMagnetometerSet) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
                SensorManager.getOrientation(rotationMatrix, orientation)
                azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
        }

        // Normalize azimuth into [0, 360)
        var rawDegrees = (azimuthDeg + 360f) % 360f

        // Angular smoothing using unit vectors to avoid jump at 360/0
        val rawRad = Math.toRadians(rawDegrees.toDouble()).toFloat()
        val currentSin = sin(rawRad)
        val currentCos = cos(rawRad)

        smoothedSin = smoothedSin + smoothingFactor * (currentSin - smoothedSin)
        smoothedCos = smoothedCos + smoothingFactor * (currentCos - smoothedCos)

        val smoothedRad = atan2(smoothedSin, smoothedCos)
        var filteredDegrees = Math.toDegrees(smoothedRad.toDouble()).toFloat()
        filteredDegrees = (filteredDegrees + 360f) % 360f

        smoothedPitch = smoothedPitch + 0.2f * (pitchDeg - smoothedPitch)
        smoothedRoll = smoothedRoll + 0.2f * (rollDeg - smoothedRoll)

        val cardinal = CardinalDirection.fromHeading(filteredDegrees)
        val isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        _heading.value = CompassHeading(
            azimuthDegrees = filteredDegrees,
            pitchDegrees = smoothedPitch,
            rollDegrees = smoothedRoll,
            cardinal = cardinal,
            accuracy = accuracy,
            isCalibrated = isCalibrated,
            magneticFieldStrengthUf = magFieldStrength
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val current = _heading.value
        _heading.value = current.copy(
            accuracy = accuracy,
            isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        )
    }
}

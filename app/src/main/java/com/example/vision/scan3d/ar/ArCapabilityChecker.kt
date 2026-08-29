package com.example.vision.scan3d.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.example.vision.scan3d.model.DeviceCapabilityReport
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session

object ArCapabilityChecker {

    fun checkDeviceCapabilities(context: Context): DeviceCapabilityReport {
        var isArCoreSupported = false
        var isArCoreInstalled = false
        var failureReason: String? = null

        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            when {
                availability.isSupported -> {
                    isArCoreSupported = true
                    isArCoreInstalled = availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
                }
                availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    isArCoreSupported = false
                    failureReason = "Device hardware does not support Google Play Services for AR (ARCore)."
                }
                availability == ArCoreApk.Availability.UNKNOWN_CHECKING ||
                availability == ArCoreApk.Availability.UNKNOWN_ERROR ||
                availability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                    isArCoreSupported = false
                    failureReason = "Unable to verify ARCore capability on this device."
                }
                else -> {
                    isArCoreSupported = availability.isSupported
                }
            }
        } catch (e: Exception) {
            isArCoreSupported = false
            failureReason = "ARCore initialization check failed: ${e.message}"
        }

        // Check camera hardware
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        var isCameraSupported = false
        var isDepthSupported = false
        try {
            val cameraIds = cameraManager?.cameraIdList ?: emptyArray()
            isCameraSupported = cameraIds.isNotEmpty()

            for (id in cameraIds) {
                val chars = cameraManager?.getCameraCharacteristics(id)
                val capabilities = chars?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                if (capabilities != null && capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)) {
                    isDepthSupported = true
                    break
                }
            }
        } catch (e: Exception) {
            // Camera check fallback
        }

        // Check motion sensors (Accelerometer + Gyroscope for 6DOF tracking)
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val isSensorsSupported = hasAccel && hasGyro

        return DeviceCapabilityReport(
            isArCoreSupported = isArCoreSupported,
            isArCoreInstalled = isArCoreInstalled,
            isDepthSupported = isDepthSupported,
            isCameraSupported = isCameraSupported,
            isSensorsSupported = isSensorsSupported,
            failureReason = failureReason
        )
    }

    fun isDepthModeSupported(session: Session): Boolean {
        return try {
            session.isDepthModeSupported(com.google.ar.core.Config.DepthMode.AUTOMATIC)
        } catch (e: Exception) {
            false
        }
    }
}

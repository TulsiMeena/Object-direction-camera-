package com.example.vision.detector

import android.graphics.RectF
import com.example.vision.model.ConfidenceLevel
import com.example.vision.model.RawDetection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Interface for object detection engine to allow modular expansion in future prompts.
 */
interface VisionObjectDetector {
    suspend fun detect(image: InputImage, confidenceLevel: ConfidenceLevel): List<RawDetection>
    fun close()
}

/**
 * Real on-device local object detector powered by Google ML Kit Object Detection API.
 * Configured in STREAM_MODE with MULTIPLE_OBJECTS and CLASSIFICATION enabled for maximum throughput.
 */
class LocalObjectDetector : VisionObjectDetector {

    // Default stream mode detector
    private val detectorOptions = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val detector = ObjectDetection.getClient(detectorOptions)

    override suspend fun detect(image: InputImage, confidenceLevel: ConfidenceLevel): List<RawDetection> {
        val detectedObjects: List<DetectedObject> = try {
            detector.process(image).await()
        } catch (e: Exception) {
            emptyList()
        }

        val results = mutableListOf<RawDetection>()

        for (detected in detectedObjects) {
            val trackingId = detected.trackingId ?: detected.hashCode()
            val labels = detected.labels

            var bestLabel = "OBJECT"
            var bestConfidence = 0.65f

            if (labels.isNotEmpty()) {
                val topLabel = labels.maxByOrNull { it.confidence }
                if (topLabel != null) {
                    val rawText = topLabel.text.trim()
                    bestLabel = mapCategoryName(rawText)
                    bestConfidence = topLabel.confidence
                }
            } else {
                // If the generic category is not strictly tagged, categorize based on shape/aspect or generic object
                bestConfidence = 0.58f
            }

            // Filter out detections below the user's selected threshold
            if (bestConfidence >= confidenceLevel.threshold) {
                results.add(
                    RawDetection(
                        internalId = trackingId,
                        categoryName = bestLabel,
                        confidence = bestConfidence,
                        boundingBox = RectF(detected.boundingBox)
                    )
                )
            }
        }

        return results
    }

    /**
     * Standardizes categories like person, car, motorcycle, bicycle, bus, truck, dog, cat, animal, etc.
     */
    private fun mapCategoryName(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("person") || lower.contains("human") || lower.contains("man") || lower.contains("woman") -> "PERSON"
            lower.contains("car") || lower.contains("automobile") || lower.contains("sedan") || lower.contains("suv") -> "CAR"
            lower.contains("motorcycle") || lower.contains("bike") || lower.contains("scooter") -> "MOTORCYCLE"
            lower.contains("bicycle") || lower.contains("cycle") -> "BICYCLE"
            lower.contains("bus") -> "BUS"
            lower.contains("truck") -> "TRUCK"
            lower.contains("dog") || lower.contains("canine") || lower.contains("puppy") -> "DOG"
            lower.contains("cat") || lower.contains("feline") || lower.contains("kitten") -> "CAT"
            lower.contains("animal") || lower.contains("bird") || lower.contains("pet") || lower.contains("wildlife") -> "ANIMAL"
            lower.contains("fashion") || lower.contains("goods") || lower.contains("clothing") -> "OBJECT"
            lower.contains("food") || lower.contains("dish") || lower.contains("fruit") -> "FOOD"
            lower.contains("plant") || lower.contains("tree") || lower.contains("flower") -> "PLANT"
            lower.contains("electronic") || lower.contains("computer") || lower.contains("phone") -> "ELECTRONIC"
            raw.isNotBlank() -> raw.uppercase()
            else -> "OBJECT"
        }
    }

    override fun close() {
        try {
            detector.close()
        } catch (_: Exception) {}
    }
}

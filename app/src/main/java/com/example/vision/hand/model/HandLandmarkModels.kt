package com.example.vision.hand.model

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Standard 21 Hand Landmarks defined by MediaPipe
 */
enum class HandLandmarkType(val index: Int, val displayName: String) {
    WRIST(0, "Wrist"),
    THUMB_CMC(1, "Thumb CMC"),
    THUMB_MCP(2, "Thumb MCP"),
    THUMB_IP(3, "Thumb IP"),
    THUMB_TIP(4, "Thumb Tip"),
    INDEX_MCP(5, "Index MCP"),
    INDEX_PIP(6, "Index PIP"),
    INDEX_DIP(7, "Index DIP"),
    INDEX_TIP(8, "Index Tip"),
    MIDDLE_MCP(9, "Middle MCP"),
    MIDDLE_PIP(10, "Middle PIP"),
    MIDDLE_DIP(11, "Middle DIP"),
    MIDDLE_TIP(12, "Middle Tip"),
    RING_MCP(13, "Ring MCP"),
    RING_PIP(14, "Ring PIP"),
    RING_DIP(15, "Ring DIP"),
    RING_TIP(16, "Ring Tip"),
    PINKY_MCP(17, "Pinky MCP"),
    PINKY_PIP(18, "Pinky PIP"),
    PINKY_DIP(19, "Pinky DIP"),
    PINKY_TIP(20, "Pinky Tip")
}

data class LandmarkPoint3D(
    val x: Float, // Normalized [0, 1]
    val y: Float, // Normalized [0, 1]
    val z: Float = 0f,
    val screenX: Float = 0f, // Display screen pixels
    val screenY: Float = 0f  // Display screen pixels
)

enum class Handedness {
    RIGHT,
    LEFT,
    UNKNOWN
}

data class HandPose(
    val handedness: Handedness,
    val confidence: Float,
    val landmarks: List<LandmarkPoint3D>, // Exactly 21 landmarks
    val boundingBox: RectF = RectF(),
    val isPinching: Boolean = false,
    val pinchDistance: Float = 1f,
    val indexTipPosition: PointF = PointF(),
    val thumbTipPosition: PointF = PointF(),
    val wristPosition: PointF = PointF()
) {
    val isValid: Boolean get() = landmarks.size == 21
}

// Hand bone connections for complete skeleton rendering
val HAND_CONNECTIONS = listOf(
    // Palm base
    Pair(HandLandmarkType.WRIST.index, HandLandmarkType.THUMB_CMC.index),
    Pair(HandLandmarkType.WRIST.index, HandLandmarkType.INDEX_MCP.index),
    Pair(HandLandmarkType.WRIST.index, HandLandmarkType.PINKY_MCP.index),
    Pair(HandLandmarkType.INDEX_MCP.index, HandLandmarkType.MIDDLE_MCP.index),
    Pair(HandLandmarkType.MIDDLE_MCP.index, HandLandmarkType.RING_MCP.index),
    Pair(HandLandmarkType.RING_MCP.index, HandLandmarkType.PINKY_MCP.index),

    // Thumb
    Pair(HandLandmarkType.THUMB_CMC.index, HandLandmarkType.THUMB_MCP.index),
    Pair(HandLandmarkType.THUMB_MCP.index, HandLandmarkType.THUMB_IP.index),
    Pair(HandLandmarkType.THUMB_IP.index, HandLandmarkType.THUMB_TIP.index),

    // Index Finger
    Pair(HandLandmarkType.INDEX_MCP.index, HandLandmarkType.INDEX_PIP.index),
    Pair(HandLandmarkType.INDEX_PIP.index, HandLandmarkType.INDEX_DIP.index),
    Pair(HandLandmarkType.INDEX_DIP.index, HandLandmarkType.INDEX_TIP.index),

    // Middle Finger
    Pair(HandLandmarkType.MIDDLE_MCP.index, HandLandmarkType.MIDDLE_PIP.index),
    Pair(HandLandmarkType.MIDDLE_PIP.index, HandLandmarkType.MIDDLE_DIP.index),
    Pair(HandLandmarkType.MIDDLE_DIP.index, HandLandmarkType.MIDDLE_TIP.index),

    // Ring Finger
    Pair(HandLandmarkType.RING_MCP.index, HandLandmarkType.RING_PIP.index),
    Pair(HandLandmarkType.RING_PIP.index, HandLandmarkType.RING_DIP.index),
    Pair(HandLandmarkType.RING_DIP.index, HandLandmarkType.RING_TIP.index),

    // Pinky Finger
    Pair(HandLandmarkType.PINKY_MCP.index, HandLandmarkType.PINKY_PIP.index),
    Pair(HandLandmarkType.PINKY_PIP.index, HandLandmarkType.PINKY_DIP.index),
    Pair(HandLandmarkType.PINKY_DIP.index, HandLandmarkType.PINKY_TIP.index)
)

data class DrawingPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class DrawingStroke(
    val id: Long = System.currentTimeMillis(),
    val points: List<DrawingPoint>,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

enum class DrawingTool {
    BRUSH,
    ERASER
}

enum class BrushSize(val strokeWidthPx: Float, val label: String) {
    SMALL(6f, "Small"),
    MEDIUM(14f, "Medium"),
    LARGE(26f, "Large")
}

enum class DrawingHandPreference(val label: String) {
    AUTO("Auto"),
    RIGHT("Right"),
    LEFT("Left")
}

enum class SmoothingLevel(val factor: Float, val label: String) {
    LOW(0.2f, "Low"),
    MEDIUM(0.5f, "Medium"),
    HIGH(0.8f, "High")
}

val DRAWING_PALETTE = listOf(
    0xFF00E5FF.toInt(), // Cyan
    0xFFFFB300.toInt(), // Neon Amber / Yellow
    0xFFFF007F.toInt(), // Hot Pink
    0xFF10B981.toInt(), // Neon Green
    0xFFA855F7.toInt(), // Electric Purple
    0xFFFFFFFF.toInt(), // Clean White
    0xFFEF4444.toInt(), // Fiery Red
    0xFF38BDF8.toInt()  // Sky Blue
)

data class AirDrawingSettings(
    val isTrackingEnabled: Boolean = true,
    val isAirDrawingActive: Boolean = true,
    val drawingHandPreference: DrawingHandPreference = DrawingHandPreference.AUTO,
    val smoothingLevel: SmoothingLevel = SmoothingLevel.MEDIUM,
    val currentTool: DrawingTool = DrawingTool.BRUSH,
    val brushSize: BrushSize = BrushSize.MEDIUM,
    val brushColor: Int = 0xFF00E5FF.toInt(),
    val showPerformanceOverlay: Boolean = true,
    val showHandSkeleton: Boolean = true
)

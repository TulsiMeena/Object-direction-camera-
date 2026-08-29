package com.example.vision.hand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import com.example.vision.hand.model.DrawingStroke
import com.example.vision.hand.model.DrawingTool
import com.example.vision.hand.model.HAND_CONNECTIONS
import com.example.vision.hand.model.HandLandmarkType
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.model.Handedness

/**
 * Canvas overlay that renders hand skeleton bones, 21 landmarks,
 * target cursor indicator at index fingertip, and smooth continuous air drawing strokes.
 */
@Composable
fun HandLandmarkOverlay(
    handPoses: List<HandPose>,
    strokes: List<DrawingStroke>,
    activeStroke: DrawingStroke?,
    showSkeleton: Boolean,
    currentTool: DrawingTool,
    brushColor: Int,
    brushSizePx: Float,
    isAirDrawingActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("hand_landmark_overlay")
    ) {
        // 1. Draw existing committed strokes
        for (stroke in strokes) {
            drawSmoothStroke(stroke)
        }

        // 2. Draw currently active stroke in progress
        activeStroke?.let {
            drawSmoothStroke(it)
        }

        // 3. Draw Hand Landmark Skeletons if enabled
        if (showSkeleton) {
            for (pose in handPoses) {
                if (!pose.isValid) continue
                drawHandSkeleton(pose)
            }
        }

        // 4. Draw Air Drawing Pointer Cursor
        if (isAirDrawingActive) {
            val activeHand = handPoses.firstOrNull { it.isPinching }
                ?: handPoses.firstOrNull()

            activeHand?.let { hand ->
                if (hand.isValid) {
                    val indexTip = hand.landmarks[HandLandmarkType.INDEX_TIP.index]
                    val thumbTip = hand.landmarks[HandLandmarkType.THUMB_TIP.index]

                    drawAirPointerCursor(
                        indexPos = Offset(indexTip.screenX, indexTip.screenY),
                        thumbPos = Offset(thumbTip.screenX, thumbTip.screenY),
                        isPinching = hand.isPinching,
                        currentTool = currentTool,
                        brushColor = Color(brushColor),
                        brushSizePx = brushSizePx
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSmoothStroke(stroke: DrawingStroke) {
    if (stroke.points.size < 2) return

    val path = Path()
    val first = stroke.points.first()
    path.moveTo(first.x, first.y)

    for (i in 1 until stroke.points.size) {
        val prev = stroke.points[i - 1]
        val curr = stroke.points[i]
        val midX = (prev.x + curr.x) / 2f
        val midY = (prev.y + curr.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }
    val last = stroke.points.last()
    path.lineTo(last.x, last.y)

    drawPath(
        path = path,
        color = Color(stroke.color),
        style = Stroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawHandSkeleton(pose: HandPose) {
    val isRight = pose.handedness == Handedness.RIGHT
    val boneColor = if (isRight) Color(0x9900E5FF) else Color(0x99A855F7)
    val jointColor = if (isRight) Color(0xFF00E5FF) else Color(0xFFA855F7)
    val tipColor = if (pose.isPinching) Color(0xFFFFB300) else Color(0xFFFFFFFF)

    // Draw Bone Connections
    for (connection in HAND_CONNECTIONS) {
        val startIdx = connection.first
        val endIdx = connection.second
        if (startIdx < pose.landmarks.size && endIdx < pose.landmarks.size) {
            val start = pose.landmarks[startIdx]
            val end = pose.landmarks[endIdx]

            drawLine(
                color = boneColor,
                start = Offset(start.screenX, start.screenY),
                end = Offset(end.screenX, end.screenY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }

    // Draw Landmarks
    val tipIndices = setOf(
        HandLandmarkType.THUMB_TIP.index,
        HandLandmarkType.INDEX_TIP.index,
        HandLandmarkType.MIDDLE_TIP.index,
        HandLandmarkType.RING_TIP.index,
        HandLandmarkType.PINKY_TIP.index
    )

    pose.landmarks.forEachIndexed { index, landmark ->
        val center = Offset(landmark.screenX, landmark.screenY)
        val isTip = tipIndices.contains(index)
        val isIndexOrThumbTip = index == HandLandmarkType.INDEX_TIP.index || index == HandLandmarkType.THUMB_TIP.index

        val radius = when {
            isIndexOrThumbTip && pose.isPinching -> 10f
            isTip -> 8f
            index == HandLandmarkType.WRIST.index -> 9f
            else -> 5f
        }

        val color = when {
            isIndexOrThumbTip && pose.isPinching -> tipColor
            isTip -> tipColor
            else -> jointColor
        }

        // Outer glow/ring for fingertips
        if (isTip) {
            drawCircle(
                color = color.copy(alpha = 0.35f),
                radius = radius + 5f,
                center = center
            )
        }

        drawCircle(
            color = color,
            radius = radius,
            center = center
        )
    }
}

private fun DrawScope.drawAirPointerCursor(
    indexPos: Offset,
    thumbPos: Offset,
    isPinching: Boolean,
    currentTool: DrawingTool,
    brushColor: Color,
    brushSizePx: Float
) {
    if (currentTool == DrawingTool.ERASER) {
        // Eraser ring cursor
        val eraserRadius = brushSizePx * 2.5f + 25f
        drawCircle(
            color = Color(0x66EF4444),
            radius = eraserRadius,
            center = indexPos
        )
        drawCircle(
            color = Color(0xFFEF4444),
            radius = eraserRadius,
            center = indexPos,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = indexPos
        )
        return
    }

    // Drawing Pointer
    if (isPinching) {
        // Pinch Active: Glowing aura and connecting pinch spark line
        drawLine(
            color = Color(0xAAFFB300),
            start = indexPos,
            end = thumbPos,
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Outer pulse
        drawCircle(
            color = brushColor.copy(alpha = 0.35f),
            radius = brushSizePx + 16f,
            center = indexPos
        )
        // Mid ring
        drawCircle(
            color = brushColor,
            radius = brushSizePx + 6f,
            center = indexPos,
            style = Stroke(width = 3f)
        )
        // Center solid core
        drawCircle(
            color = Color.White,
            radius = brushSizePx / 2f + 2f,
            center = indexPos
        )
    } else {
        // Hover mode: Precision reticle ring
        drawCircle(
            color = brushColor.copy(alpha = 0.25f),
            radius = 18f,
            center = indexPos
        )
        drawCircle(
            color = brushColor,
            radius = 12f,
            center = indexPos,
            style = Stroke(width = 2.5f)
        )
        drawCircle(
            color = Color.White,
            radius = 3f,
            center = indexPos
        )
    }
}

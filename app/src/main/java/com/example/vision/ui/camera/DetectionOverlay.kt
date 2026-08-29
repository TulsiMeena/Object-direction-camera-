package com.example.vision.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import com.example.vision.model.Direction
import com.example.vision.model.MovementStatus
import com.example.vision.model.TrackedObject
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.BlueMoving
import com.example.vision.ui.theme.CyanGlow
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GreenStationary
import kotlin.math.max
import kotlin.math.min

@Composable
fun DetectionOverlay(
    trackedObjects: List<TrackedObject>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("detection_overlay_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (obj in trackedObjects) {
                drawObjectDetection(obj)
            }
        }
    }
}

private fun DrawScope.drawObjectDetection(obj: TrackedObject) {
    val rect = obj.displayBoundingBox
    if (rect.width() <= 5 || rect.height() <= 5) return

    val isMoving = obj.movementStatus == MovementStatus.MOVING
    val primaryColor = if (isMoving) BlueMoving else CyanPrimary
    val accentColor = if (isMoving) AmberAccent else GreenStationary

    val left = rect.left
    val top = rect.top
    val right = rect.right
    val bottom = rect.bottom
    val width = right - left
    val height = bottom - top

    // 1. Subtle semi-transparent box fill
    drawRect(
        color = primaryColor.copy(alpha = 0.08f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Fill
    )

    // 2. Fine boundary outline
    drawRoundRect(
        color = primaryColor.copy(alpha = 0.45f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.5f)
    )

    // 3. Futuristic corner HUD brackets
    val cornerLen = min(28f, min(width / 3f, height / 3f))
    val bracketStroke = 3.5f

    // Top-Left
    val pathTL = Path().apply {
        moveTo(left, top + cornerLen)
        lineTo(left, top)
        lineTo(left + cornerLen, top)
    }
    drawPath(pathTL, primaryColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Top-Right
    val pathTR = Path().apply {
        moveTo(right - cornerLen, top)
        lineTo(right, top)
        lineTo(right, top + cornerLen)
    }
    drawPath(pathTR, primaryColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Bottom-Left
    val pathBL = Path().apply {
        moveTo(left, bottom - cornerLen)
        lineTo(left, bottom)
        lineTo(left + cornerLen, bottom)
    }
    drawPath(pathBL, primaryColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Bottom-Right
    val pathBR = Path().apply {
        moveTo(right - cornerLen, bottom)
        lineTo(right, bottom)
        lineTo(right, bottom - cornerLen)
    }
    drawPath(pathBR, primaryColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // 4. Center Crosshair
    val centerX = left + width / 2f
    val centerY = top + height / 2f
    val crosshairLen = 6f
    drawLine(
        color = primaryColor.copy(alpha = 0.7f),
        start = Offset(centerX - crosshairLen, centerY),
        end = Offset(centerX + crosshairLen, centerY),
        strokeWidth = 1.5f
    )
    drawLine(
        color = primaryColor.copy(alpha = 0.7f),
        start = Offset(centerX, centerY - crosshairLen),
        end = Offset(centerX, centerY + crosshairLen),
        strokeWidth = 1.5f
    )

    // 5. Draw Motion Direction Arrow indicator if moving
    if (isMoving && obj.direction != Direction.STATIONARY && obj.direction != Direction.UNKNOWN) {
        drawDirectionIndicator(centerX, centerY, obj.direction, accentColor)
    }

    // 6. Draw HUD Badge and Labels using Android Native Canvas for razor-sharp typography
    drawContext.canvas.nativeCanvas.apply {
        val paintTextTitle = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
            setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
        }

        val paintTextDetail = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            textSize = 26f
            isAntiAlias = true
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
        }

        val paintBadgeBg = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E6090D16")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }

        val paintBadgeBorder = android.graphics.Paint().apply {
            color = if (isMoving) android.graphics.Color.parseColor("#38BDF8") else android.graphics.Color.parseColor("#00E5FF")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val confidencePercent = (obj.confidence * 100).toInt()
        val titleText = obj.trackingId
        val line2Text = "Confidence: $confidencePercent%"
        val directionSymbol = when (obj.direction) {
            Direction.LEFT -> "← LEFT"
            Direction.RIGHT -> "RIGHT →"
            Direction.UP -> "↑ UP"
            Direction.DOWN -> "↓ DOWN"
            Direction.STATIONARY -> "STATIONARY"
            Direction.UNKNOWN -> "STATIONARY"
        }
        val line3Text = "Direction: $directionSymbol"
        val line4Text = "Status: ${obj.movementStatus.displayName}"

        val titleWidth = paintTextTitle.measureText(titleText)
        val l2Width = paintTextDetail.measureText(line2Text)
        val l3Width = paintTextDetail.measureText(line3Text)
        val l4Width = paintTextDetail.measureText(line4Text)
        val maxBadgeWidth = max(titleWidth, max(l2Width, max(l3Width, l4Width))) + 28f
        val badgeHeight = 135f

        // Position badge above bounding box if space permits, else inside or below
        val badgeTop = if (top - badgeHeight - 12f >= 16f) {
            top - badgeHeight - 12f
        } else {
            top + 12f
        }
        val badgeLeft = left.coerceAtLeast(16f).coerceAtMost(size.width - maxBadgeWidth - 16f)
        val badgeRect = android.graphics.RectF(badgeLeft, badgeTop, badgeLeft + maxBadgeWidth, badgeTop + badgeHeight)

        // Draw HUD background & border
        drawRoundRect(badgeRect, 10f, 10f, paintBadgeBg)
        drawRoundRect(badgeRect, 10f, 10f, paintBadgeBorder)

        // Render text lines
        drawText(titleText, badgeLeft + 14f, badgeTop + 34f, paintTextTitle)
        drawText(line2Text, badgeLeft + 14f, badgeTop + 66f, paintTextDetail)
        drawText(line3Text, badgeLeft + 14f, badgeTop + 95f, paintTextDetail)
        drawText(line4Text, badgeLeft + 14f, badgeTop + 124f, paintTextDetail)
    }
}

private fun DrawScope.drawDirectionIndicator(
    centerX: Float,
    centerY: Float,
    direction: Direction,
    color: Color
) {
    val arrowLen = 32f
    val endX: Float
    val endY: Float

    when (direction) {
        Direction.LEFT -> {
            endX = centerX - arrowLen
            endY = centerY
        }
        Direction.RIGHT -> {
            endX = centerX + arrowLen
            endY = centerY
        }
        Direction.UP -> {
            endX = centerX
            endY = centerY - arrowLen
        }
        Direction.DOWN -> {
            endX = centerX
            endY = centerY + arrowLen
        }
        else -> return
    }

    // Line
    drawLine(
        color = color,
        start = Offset(centerX, centerY),
        end = Offset(endX, endY),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // Arrow tip
    val headSize = 12f
    val tipPath = Path()
    when (direction) {
        Direction.LEFT -> {
            tipPath.moveTo(endX + headSize, endY - headSize)
            tipPath.lineTo(endX, endY)
            tipPath.lineTo(endX + headSize, endY + headSize)
        }
        Direction.RIGHT -> {
            tipPath.moveTo(endX - headSize, endY - headSize)
            tipPath.lineTo(endX, endY)
            tipPath.lineTo(endX - headSize, endY + headSize)
        }
        Direction.UP -> {
            tipPath.moveTo(endX - headSize, endY + headSize)
            tipPath.lineTo(endX, endY)
            tipPath.lineTo(endX + headSize, endY + headSize)
        }
        Direction.DOWN -> {
            tipPath.moveTo(endX - headSize, endY - headSize)
            tipPath.lineTo(endX, endY)
            tipPath.lineTo(endX + headSize, endY - headSize)
        }
        else -> {}
    }

    drawPath(tipPath, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

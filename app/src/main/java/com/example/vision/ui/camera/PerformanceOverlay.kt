package com.example.vision.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.model.PerformanceMetrics
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GreenStationary
import com.example.vision.ui.theme.HUDBackground
import com.example.vision.ui.theme.RedAlert
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary

@Composable
fun PerformanceOverlay(
    metrics: PerformanceMetrics,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .testTag("performance_overlay")
                .clip(RoundedCornerShape(12.dp))
                .background(HUDBackground)
                .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header with live pulsing dot
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (metrics.cameraFps > 15) GreenStationary else AmberAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PERFORMANCE MONITOR",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Metric Rows
                MetricRow(label = "FPS", value = "${metrics.cameraFps.toInt()}")
                MetricRow(label = "Detection", value = "${metrics.detectionFps} FPS")
                MetricRow(label = "Tracking", value = "${metrics.trackingFps} FPS")
                MetricRow(
                    label = "Inference",
                    value = "${metrics.inferenceTimeMs} ms",
                    highlightColor = if (metrics.inferenceTimeMs < 35) GreenStationary else AmberAccent
                )
                MetricRow(
                    label = "Dropped Frames",
                    value = "${metrics.droppedFrames}",
                    highlightColor = if (metrics.droppedFrames == 0L) TextSecondary else RedAlert
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    highlightColor: Color = TextPrimary
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(170.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = highlightColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

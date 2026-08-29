package com.example.vision.hand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.hand.model.AirDrawingSettings
import com.example.vision.hand.model.BrushSize
import com.example.vision.hand.model.DRAWING_PALETTE
import com.example.vision.hand.model.DrawingHandPreference
import com.example.vision.hand.model.DrawingTool
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.model.Handedness
import com.example.vision.hand.model.SmoothingLevel
import com.example.vision.model.PerformanceMetrics

@Composable
fun HandTrackingTopBar(
    handPoses: List<HandPose>,
    isAirDrawingActive: Boolean,
    isPinching: Boolean,
    onBackClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val handSummary = when {
        handPoses.isEmpty() -> "NO HAND DETECTED"
        handPoses.size >= 2 -> "DUAL HANDS TRACKED"
        handPoses[0].handedness == Handedness.RIGHT -> "RIGHT HAND (${(handPoses[0].confidence * 100).toInt()}%)"
        handPoses[0].handedness == Handedness.LEFT -> "LEFT HAND (${(handPoses[0].confidence * 100).toInt()}%)"
        else -> "HAND TRACKED (${(handPoses[0].confidence * 100).toInt()}%)"
    }

    val isTracking = handPoses.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        FilledTonalIconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .testTag("back_button"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Studio"
            )
        }

        // Live Hand Status Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.65f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isPinching) Color(0xFFFFB300) else if (isTracking) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.testTag("hand_status_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPinching) Color(0xFFFFB300)
                            else if (isTracking) Color(0xFF00E5FF)
                            else Color(0xFFEF4444)
                        )
                )
                Text(
                    text = if (isPinching) "PINCH ACTIVE" else handSummary,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Right Action Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = onFlipCamera,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("flip_camera_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera"
                )
            }

            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("settings_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings"
                )
            }
        }
    }
}

/**
 * Floating Air Drawing Tool Dock.
 * Contains tools (Brush / Eraser), color picker chips, brush sizes, undo/redo, clear, save.
 */
@Composable
fun AirDrawingDock(
    settings: AirDrawingSettings,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (DrawingTool) -> Unit,
    onColorChange: (Int) -> Unit,
    onBrushSizeChange: (BrushSize) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPalette by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color Palette Row (Expandable)
        AnimatedVisibility(
            visible = showColorPalette && settings.currentTool == DrawingTool.BRUSH,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(DRAWING_PALETTE) { colorInt ->
                        val isSelected = settings.brushColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onColorChange(colorInt)
                                }
                                .testTag("color_chip_${Integer.toHexString(colorInt)}")
                        )
                    }
                }
            }
        }

        // Main Drawing Toolbar Dock
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.78f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.testTag("air_drawing_dock")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Brush Tool
                FilledIconButton(
                    onClick = {
                        onToolChange(DrawingTool.BRUSH)
                        showColorPalette = !showColorPalette
                    },
                    modifier = Modifier.size(40.dp).testTag("tool_brush"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (settings.currentTool == DrawingTool.BRUSH) Color(settings.brushColor) else Color.Transparent,
                        contentColor = if (settings.currentTool == DrawingTool.BRUSH) Color.Black else Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Brush, contentDescription = "Brush Tool")
                }

                // 2. Eraser Tool
                FilledIconButton(
                    onClick = {
                        onToolChange(DrawingTool.ERASER)
                        showColorPalette = false
                    },
                    modifier = Modifier.size(40.dp).testTag("tool_eraser"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (settings.currentTool == DrawingTool.ERASER) Color(0xFFEF4444) else Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = "Eraser Tool")
                }

                // Size toggle (Cycle: Small -> Medium -> Large)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            val nextSize = when (settings.brushSize) {
                                BrushSize.SMALL -> BrushSize.MEDIUM
                                BrushSize.MEDIUM -> BrushSize.LARGE
                                BrushSize.LARGE -> BrushSize.SMALL
                            }
                            onBrushSizeChange(nextSize)
                        }
                        .testTag("brush_size_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    val dotRadius = when (settings.brushSize) {
                        BrushSize.SMALL -> 4.dp
                        BrushSize.MEDIUM -> 8.dp
                        BrushSize.LARGE -> 14.dp
                    }
                    Box(
                        modifier = Modifier
                            .size(dotRadius)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                // 3. Undo
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(40.dp).testTag("action_undo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo Stroke",
                        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }

                // 4. Redo
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(40.dp).testTag("action_redo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo Stroke",
                        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }

                // 5. Clear
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(40.dp).testTag("action_clear")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Canvas",
                        tint = Color(0xFFFF6E6E)
                    )
                }

                // 6. Save
                FilledIconButton(
                    onClick = onSave,
                    modifier = Modifier.size(40.dp).testTag("action_save"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save Drawing")
                }
            }
        }
    }
}

/**
 * Settings Sheet for Hand Tracking & Air Drawing preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandTrackingSettingsSheet(
    settings: AirDrawingSettings,
    onSettingsChanged: (AirDrawingSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141A22),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hand Tracking & Air Drawing Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )

            // Tracking Master Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Hand Landmark Tracking", fontWeight = FontWeight.SemiBold)
                    Text("21-landmark skeletal detection", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = settings.isTrackingEnabled,
                    onCheckedChange = { onSettingsChanged(settings.copy(isTrackingEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF005B66)
                    )
                )
            }

            // Show Hand Skeleton Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Show 21-Joint Skeleton", fontWeight = FontWeight.SemiBold)
                    Text("Draw bone lines and joint landmarks", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = settings.showHandSkeleton,
                    onCheckedChange = { onSettingsChanged(settings.copy(showHandSkeleton = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF005B66)
                    )
                )
            }

            // Air Drawing Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Air Drawing Gesture Engine", fontWeight = FontWeight.SemiBold)
                    Text("Pinch thumb + index finger to draw in mid-air", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = settings.isAirDrawingActive,
                    onCheckedChange = { onSettingsChanged(settings.copy(isAirDrawingActive = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFB300),
                        checkedTrackColor = Color(0xFF664400)
                    )
                )
            }

            // Drawing Hand Selection
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Preferred Drawing Hand", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawingHandPreference.entries.forEach { pref ->
                        val isSelected = settings.drawingHandPreference == pref
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSettingsChanged(settings.copy(drawingHandPreference = pref)) }
                        ) {
                            Text(
                                text = pref.label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Smoothing Level Selection
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Temporal Smoothing & Jitter Reduction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmoothingLevel.entries.forEach { level ->
                        val isSelected = settings.smoothingLevel == level
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFFFB300).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSettingsChanged(settings.copy(smoothingLevel = level)) }
                        ) {
                            Text(
                                text = level.label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) Color(0xFFFFB300) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Performance Overlay Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Performance & FPS Overlay", fontWeight = FontWeight.SemiBold)
                    Text("Display live camera FPS, latency & frame stats", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = settings.showPerformanceOverlay,
                    onCheckedChange = { onSettingsChanged(settings.copy(showPerformanceOverlay = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF005B66)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

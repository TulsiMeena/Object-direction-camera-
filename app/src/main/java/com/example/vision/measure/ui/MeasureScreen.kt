package com.example.vision.measure.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vision.model.MeasurementMode
import com.example.vision.model.MeasurementPoint3D
import com.example.vision.model.MeasurementUnit
import com.example.vision.ui.ActiveVisionMode
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanDark
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GlassCard
import com.example.vision.ui.theme.GreenStationary
import com.example.vision.ui.theme.HUDBackground
import com.example.vision.ui.theme.IndigoDark
import com.example.vision.ui.theme.IndigoSurface
import com.example.vision.ui.theme.RedAlert
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary
import com.example.vision.ui.theme.TextTertiary

@Composable
fun MeasureScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val measurementResult by viewModel.measurementResult.collectAsStateWithLifecycle()
    val currentMode by viewModel.measureMode.collectAsStateWithLifecycle()
    val currentUnit by viewModel.measureUnit.collectAsStateWithLifecycle()
    val pointA by viewModel.measureEngine.pointA.collectAsStateWithLifecycle()
    val pointB by viewModel.measureEngine.pointB.collectAsStateWithLifecycle()
    val calibrationScale by viewModel.measureEngine.calibrationScale.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showCalibrateDialog by remember { mutableStateOf(false) }
    var showEstimateInfoDialog by remember { mutableStateOf(false) }

    var viewW by remember { mutableStateOf(1080f) }
    var viewH by remember { mutableStateOf(1920f) }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            previewViewInstance?.let { pView ->
                viewModel.cameraManager.startCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = pView,
                    useFrontCamera = settings.isFrontCamera
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setVisionMode(ActiveVisionMode.MEASURE)
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.cameraManager.stopCamera()
            viewModel.resetMeasurement()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IndigoDark)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    viewW = size.width.toFloat()
                    viewH = size.height.toFloat()
                    viewModel.updatePreviewDimensions(viewW, viewH)
                }
            }
    ) {
        if (hasCameraPermission) {
            // Live Camera Feed
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { pView ->
                        previewViewInstance = pView
                        viewModel.cameraManager.startCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = pView,
                            useFrontCamera = settings.isFrontCamera
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Touch interaction layer to place measurement points
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            viewModel.placeMeasurementPoint(offset.x, offset.y, viewW, viewH)
                        }
                    }
            )

            // Measurement Overlay Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ptA = pointA
                val ptB = pointB

                // Draw connecting line if both points placed
                if (ptA != null && ptB != null) {
                    val start = Offset(ptA.screenX, ptA.screenY)
                    val end = Offset(ptB.screenX, ptB.screenY)

                    // Glow line
                    drawLine(
                        color = CyanPrimary.copy(alpha = 0.4f),
                        start = start,
                        end = end,
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                    // Core dashed measuring tape line
                    drawLine(
                        color = CyanPrimary,
                        start = start,
                        end = end,
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                    )

                    // Draw center midpoint tick
                    val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                    drawCircle(
                        color = AmberAccent,
                        radius = 5f,
                        center = mid
                    )
                }

                // Draw Point A Marker
                if (ptA != null) {
                    drawMeasurementPointMarker(
                        center = Offset(ptA.screenX, ptA.screenY),
                        label = "POINT A",
                        color = CyanPrimary
                    )
                }

                // Draw Point B Marker
                if (ptB != null) {
                    drawMeasurementPointMarker(
                        center = Offset(ptB.screenX, ptB.screenY),
                        label = "POINT B",
                        color = AmberAccent
                    )
                }

                // Crosshair in center to help guide aiming
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(centerX - 14f, centerY),
                    end = Offset(centerX + 14f, centerY),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(centerX, centerY - 14f),
                    end = Offset(centerX, centerY + 14f),
                    strokeWidth = 1.5f
                )
            }

            // Top HUD Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                IndigoDark.copy(alpha = 0.85f),
                                IndigoDark.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .testTag("measure_back_button")
                            .clip(CircleShape)
                            .background(GlassCard)
                            .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyanPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "MEASURE",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (pointA == null) "TAP SCREEN TO PLACE POINT A" else if (pointB == null) "TAP TO PLACE POINT B" else "MEASUREMENT READY",
                            color = if (pointB != null) GreenStationary else AmberAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.resetMeasurement() },
                            modifier = Modifier
                                .testTag("measure_reset_button")
                                .clip(CircleShape)
                                .background(GlassCard)
                                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = { showCalibrateDialog = true },
                            modifier = Modifier
                                .testTag("measure_calibrate_button")
                                .clip(CircleShape)
                                .background(GlassCard)
                                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Calibrate",
                                tint = CyanPrimary
                            )
                        }
                    }
                }
            }

            // Measurement Mode & Unit Switcher
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Unit selector chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HUDBackground)
                        .border(1.dp, CyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    MeasurementUnit.values().forEach { unit ->
                        val isSelected = currentUnit == unit
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) CyanPrimary else Color.Transparent)
                                .clickable { viewModel.setMeasurementUnit(unit) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = unit.symbol,
                                color = if (isSelected) Color.Black else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Bottom Results HUD Card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                IndigoDark.copy(alpha = 0.85f),
                                IndigoDark
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HUDBackground)
                        .border(1.5.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DISTANCE",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // ESTIMATED BADGE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AmberAccent.copy(alpha = 0.2f))
                                    .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { showEstimateInfoDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Estimated",
                                    color = AmberAccent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (measurementResult != null) {
                            Text(
                                text = "CALIBRATED: ${(calibrationScale * 100).toInt()}%",
                                color = TextTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val result = measurementResult
                    if (result != null) {
                        Text(
                            text = viewModel.measureEngine.formatDistance(result.distanceMeters),
                            color = CyanPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Width (Est.)", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(text = viewModel.measureEngine.formatDistance(result.widthMeters), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(text = "Height (Est.)", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(text = viewModel.measureEngine.formatDistance(result.heightMeters), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(text = "Area (Est.)", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(text = viewModel.measureEngine.formatArea(result.areaSqMeters), color = GreenStationary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "—",
                            color = TextSecondary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Tap on Point A and Point B in the camera view to measure distance.",
                            color = TextTertiary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // Calibrate Dialog
        if (showCalibrateDialog) {
            AlertDialog(
                onDismissRequest = { showCalibrateDialog = false },
                containerColor = IndigoSurface,
                title = {
                    Text(
                        text = "CALIBRATE SCALE",
                        color = CyanPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Adjust the measurement scale factor if measuring known reference objects.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scale: ${(calibrationScale * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = calibrationScale,
                            onValueChange = { viewModel.measureEngine.setCalibrationScale(it) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPrimary,
                                activeTrackColor = CyanPrimary,
                                inactiveTrackColor = IndigoDark
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showCalibrateDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.measureEngine.setCalibrationScale(1.0f)
                        showCalibrateDialog = false
                    }) {
                        Text("Reset (100%)", color = TextSecondary)
                    }
                }
            )
        }

        // Estimate Info Dialog
        if (showEstimateInfoDialog) {
            AlertDialog(
                onDismissRequest = { showEstimateInfoDialog = false },
                containerColor = IndigoSurface,
                icon = {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AmberAccent)
                },
                title = {
                    Text("Measurement Estimation", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Camera-based distances are perspective approximations computed via camera angle, raycast projection, and sensor fusion. For precise construction or legal requirements, certified physical instruments must be used.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showEstimateInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Understood", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeasurementPointMarker(
    center: Offset,
    label: String,
    color: Color
) {
    // Outer pulsing ring
    drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = 24f,
        center = center
    )
    // Inner ring
    drawCircle(
        color = color,
        radius = 12f,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
    )
    // Center point
    drawCircle(
        color = color,
        radius = 4f,
        center = center
    )
}

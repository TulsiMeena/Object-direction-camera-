package com.example.vision.scan3d.ui

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.vision.scan3d.ar.ArCapabilityChecker
import com.example.vision.scan3d.ar.ArCoreScanManager
import com.example.vision.scan3d.model.DetectedPlane
import com.example.vision.scan3d.model.DeviceCapabilityReport
import com.example.vision.scan3d.model.PointMeasurement
import com.example.vision.scan3d.model.RoomDimensions
import com.example.vision.scan3d.model.ScanPerformanceMetrics
import com.example.vision.scan3d.model.ScanStatus
import com.example.vision.scan3d.model.TrackingStatus
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanDark
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GreenStationary
import com.example.vision.ui.theme.IndigoBorder
import com.example.vision.ui.theme.IndigoCard
import com.example.vision.ui.theme.IndigoDark
import com.example.vision.ui.theme.IndigoSurface
import com.example.vision.ui.theme.RedAlert
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary
import com.example.vision.ui.theme.TextTertiary
import java.util.Locale

@Composable
fun HouseScanScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Capability check
    var capabilityReport by remember {
        mutableStateOf(ArCapabilityChecker.checkDeviceCapabilities(context))
    }

    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // If ARCore is not supported on this device, show honest diagnostics view
    if (!capabilityReport.isArCoreSupported) {
        BackHandler { onNavigateBack() }
        CapabilityErrorView(
            report = capabilityReport,
            onNavigateBack = onNavigateBack,
            onRetry = {
                capabilityReport = ArCapabilityChecker.checkDeviceCapabilities(context)
            },
            modifier = modifier
        )
        return
    }

    // ARCore Scan Manager
    val scanManager = remember { ArCoreScanManager(context, scope) }

    val scanStatus by scanManager.scanStatus.collectAsState()
    val trackingStatus by scanManager.trackingStatus.collectAsState()
    val trackingMessage by scanManager.trackingMessage.collectAsState()
    val detectedPlanes by scanManager.detectedPlanes.collectAsState()
    val pointCount by scanManager.pointCount.collectAsState()
    val scanCoverage by scanManager.scanCoverage.collectAsState()
    val selectedPlane by scanManager.selectedPlane.collectAsState()
    val currentMeasurement by scanManager.currentMeasurement.collectAsState()
    val pendingPointA by scanManager.pendingMeasurementPointA.collectAsState()
    val roomDimensions by scanManager.roomDimensions.collectAsState()
    val performanceMetrics by scanManager.performanceMetrics.collectAsState()
    val generatedModel by scanManager.generatedModel.collectAsState()

    var showDiagnostics by remember { mutableStateOf(false) }

    // Manage AR session lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> scanManager.resumeSession()
                Lifecycle.Event.ON_PAUSE -> scanManager.pauseSession()
                Lifecycle.Event.ON_DESTROY -> scanManager.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        scanManager.initializeSession()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scanManager.destroy()
        }
    }

    // If 3D model is generated and scan is finished, display the 3D Model Viewer
    if (scanStatus == ScanStatus.FINISHED && generatedModel != null) {
        BackHandler {
            scanManager.clearScan()
        }
        HouseModelViewer3D(
            model = generatedModel!!,
            onNavigateBack = onNavigateBack,
            onRescan = { scanManager.clearScan() },
            modifier = modifier
        )
        return
    }

    BackHandler {
        scanManager.destroy()
        onNavigateBack()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("house_scan_screen"),
        color = IndigoDark
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Live AR OpenGL Surface View
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        preserveEGLContextOnPause = true
                        setEGLContextClientVersion(2)
                        setRenderer(scanManager)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        setOnTouchListener { _, event ->
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                scanManager.handleScreenTap(event.x, event.y)
                            }
                            true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ar_surface_view")
            )

            // Top Header & Real-time Scan Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 44.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scanManager.destroy()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(IndigoSurface.copy(alpha = 0.85f))
                            .border(1.dp, IndigoBorder, CircleShape)
                            .testTag("scan_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Scan Header Label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(IndigoSurface.copy(alpha = 0.85f))
                            .border(1.dp, IndigoBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (scanStatus) {
                                            ScanStatus.SCANNING -> GreenStationary
                                            ScanStatus.PAUSED -> AmberAccent
                                            else -> TextTertiary
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3D HOUSE SCAN",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { showDiagnostics = !showDiagnostics },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (showDiagnostics) CyanPrimary else IndigoSurface.copy(alpha = 0.85f))
                            .border(1.dp, IndigoBorder, CircleShape)
                            .testTag("toggle_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Telemetry",
                            tint = if (showDiagnostics) Color.Black else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-Time Scan Status HUD
                ScanStatusHud(
                    scanStatus = scanStatus,
                    trackingStatus = trackingStatus,
                    trackingMessage = trackingMessage,
                    planeCount = detectedPlanes.size,
                    pointCount = pointCount,
                    coveragePercent = scanCoverage
                )

                // Optional Diagnostics Telemetry
                AnimatedVisibility(
                    visible = showDiagnostics,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    DiagnosticsCard(metrics = performanceMetrics)
                }

                // Measurement HUD (Point A -> Point B)
                if (pendingPointA != null || currentMeasurement != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MeasurementCallout(
                        pendingPointA = pendingPointA != null,
                        measurement = currentMeasurement,
                        onClear = { scanManager.clearMeasurement() }
                    )
                }

                // Selected Surface HUD
                selectedPlane?.let { plane ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SurfaceSelectionCallout(
                        plane = plane,
                        onDismiss = { scanManager.selectPlane(null) }
                    )
                }
            }

            // Bottom Floating Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                // Real-Time Estimated Room Dimensions Pill
                roomDimensions?.let { dims ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, IndigoBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = IndigoCard.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EST. ROOM: ${String.format(Locale.US, "%.1f", dims.estimatedWidthM)}m × ${String.format(Locale.US, "%.1f", dims.estimatedLengthM)}m (${String.format(Locale.US, "%.1f", dims.floorAreaSqM)} m²)",
                                color = CyanPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Estimated",
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Scan Action Controls: Start / Pause / Resume / Clear / Finish
                ScanControlButtons(
                    status = scanStatus,
                    onStart = { scanManager.startScan() },
                    onPause = { scanManager.pauseScan() },
                    onResume = { scanManager.resumeScan() },
                    onClear = { scanManager.clearScan() },
                    onFinish = { scanManager.finishScanAndGenerateModel() }
                )
            }
        }
    }
}

@Composable
private fun ScanStatusHud(
    scanStatus: ScanStatus,
    trackingStatus: TrackingStatus,
    trackingMessage: String,
    planeCount: Int,
    pointCount: Int,
    coveragePercent: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, IndigoBorder, RoundedCornerShape(18.dp))
            .testTag("scan_status_hud"),
        colors = CardDefaults.cardColors(containerColor = IndigoCard.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3D SCAN",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (trackingStatus) {
                                TrackingStatus.ACTIVE, TrackingStatus.GOOD -> GreenStationary.copy(alpha = 0.15f)
                                TrackingStatus.POOR -> AmberAccent.copy(alpha = 0.15f)
                                else -> RedAlert.copy(alpha = 0.15f)
                            }
                        )
                        .border(
                            1.dp,
                            when (trackingStatus) {
                                TrackingStatus.ACTIVE, TrackingStatus.GOOD -> GreenStationary
                                TrackingStatus.POOR -> AmberAccent
                                else -> RedAlert
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TRACKING: ${trackingStatus.name}",
                        color = when (trackingStatus) {
                            TrackingStatus.ACTIVE, TrackingStatus.GOOD -> GreenStationary
                            TrackingStatus.POOR -> AmberAccent
                            else -> RedAlert
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = trackingMessage,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Real HUD Metrics Matrix: Planes, Points, Coverage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HudMetricBlock(label = "PLANES", value = planeCount.toString())
                HudMetricBlock(label = "POINTS", value = pointCount.toString())
                HudMetricBlock(label = "COVERAGE", value = "$coveragePercent%")
            }
        }
    }
}

@Composable
private fun HudMetricBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = CyanPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DiagnosticsCard(metrics: ScanPerformanceMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, IndigoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = IndigoSurface.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "CAMERA FPS: ${metrics.cameraFps}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(text = "AR TRACK FPS: ${metrics.arTrackingFps}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Column {
                Text(text = "RENDER FPS: ${metrics.renderFps}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(text = "RAM: ${metrics.memoryUsageMb} MB", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Column {
                Text(text = "THERMAL: ${metrics.thermalState}", color = GreenStationary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(text = "MESH: ${metrics.meshProcessingMs}ms", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MeasurementCallout(
    pendingPointA: Boolean,
    measurement: PointMeasurement?,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, CyanPrimary.copy(alpha = 0.8f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = IndigoCard.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    if (pendingPointA) {
                        Text(
                            text = "Point A Set → Tap surface for Point B",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (measurement != null) {
                        Text(
                            text = "Distance: ${String.format(Locale.US, "%.2f", measurement.distanceMeters)} m",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "AR 3D Euclidean Distance (Estimated)",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Measurement",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SurfaceSelectionCallout(
    plane: DetectedPlane,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, AmberAccent.copy(alpha = 0.8f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = IndigoCard.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${plane.type.label} Selected",
                    color = AmberAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Area: ${String.format(Locale.US, "%.2f", plane.areaSquareMeters)} m² • Span: ${String.format(Locale.US, "%.1f", plane.extentX)}m × ${String.format(Locale.US, "%.1f", plane.extentZ)}m",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Dismiss",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ScanControlButtons(
    status: ScanStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            ScanStatus.IDLE -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START SCAN",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
            ScanStatus.SCANNING -> {
                // Pause Button
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pause_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndigoBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PAUSE",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Finish / Generate 3D Model Button
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .testTag("finish_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenStationary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GENERATE 3D MODEL",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            ScanStatus.PAUSED -> {
                // Clear Button
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(IndigoSurface)
                        .border(1.dp, IndigoBorder, RoundedCornerShape(14.dp))
                        .testTag("clear_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Scan",
                        tint = RedAlert,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Resume Button
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("resume_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESUME",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Finish Button
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp)
                        .testTag("finish_scan_paused_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenStationary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GENERATE 3D",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            ScanStatus.FINISHED -> {
                // Handled in full-screen model viewer
            }
        }
    }
}

package com.example.vision.ui.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vision.camera.CameraState
import com.example.vision.model.ConfidenceLevel
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanDark
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GlassCard
import com.example.vision.ui.theme.HUDBackground
import com.example.vision.ui.theme.IndigoDark
import com.example.vision.ui.theme.IndigoSurface
import com.example.vision.ui.theme.RedAlert
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val trackedObjects by viewModel.trackedObjects.collectAsStateWithLifecycle()
    val performanceMetrics by viewModel.performanceMetrics.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted && previewViewInstance != null) {
            viewModel.cameraManager.startCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewViewInstance!!,
                useFrontCamera = settings.isFrontCamera
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Stop camera when leaving the screen
    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.cameraManager.stopCamera()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IndigoDark)
    ) {
        if (hasCameraPermission) {
            // Live CameraX Preview
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview_view")
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            viewModel.updatePreviewDimensions(
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                        }
                    }
            )

            // Dynamic Bounding Boxes & HUD Overlay
            DetectionOverlay(
                trackedObjects = trackedObjects,
                modifier = Modifier.fillMaxSize()
            )

            // Top HUD Control Bar
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
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .testTag("back_button")
                            .clip(CircleShape)
                            .background(GlassCard)
                            .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Studio",
                            tint = CyanPrimary
                        )
                    }

                    // Center Mode Title
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "OBJECT & DIRECTION",
                            color = CyanPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        val trackedCount = trackedObjects.size
                        Text(
                            text = if (trackedCount > 0) "$trackedCount ACTIVE OBJECTS" else "SCANNING ENVIRONMENT",
                            color = if (trackedCount > 0) AmberAccent else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Action Buttons (Torch, Flip Camera, Performance Toggle, Settings)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Torch Button (only on back camera)
                        if (!settings.isFrontCamera) {
                            IconButton(
                                onClick = { viewModel.toggleTorch() },
                                modifier = Modifier
                                    .testTag("torch_button")
                                    .clip(CircleShape)
                                    .background(if (settings.isFlashlightOn) AmberAccent.copy(alpha = 0.25f) else GlassCard)
                                    .border(
                                        1.dp,
                                        if (settings.isFlashlightOn) AmberAccent else CyanPrimary.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (settings.isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flashlight",
                                    tint = if (settings.isFlashlightOn) AmberAccent else TextPrimary
                                )
                            }
                        }

                        // Flip Camera Button
                        IconButton(
                            onClick = {
                                previewViewInstance?.let { pView ->
                                    viewModel.toggleCameraFacing(lifecycleOwner, pView)
                                }
                            },
                            modifier = Modifier
                                .testTag("flip_camera_button")
                                .clip(CircleShape)
                                .background(GlassCard)
                                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = CyanPrimary
                            )
                        }

                        // Performance Overlay Toggle Button
                        IconButton(
                            onClick = { viewModel.togglePerformanceOverlay() },
                            modifier = Modifier
                                .testTag("toggle_perf_button")
                                .clip(CircleShape)
                                .background(if (settings.showPerformanceOverlay) CyanPrimary.copy(alpha = 0.25f) else GlassCard)
                                .border(
                                    1.dp,
                                    if (settings.showPerformanceOverlay) CyanPrimary else CyanPrimary.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Toggle Performance Monitor",
                                tint = if (settings.showPerformanceOverlay) CyanPrimary else TextSecondary
                            )
                        }

                        // Settings Button
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .testTag("settings_button")
                                .clip(CircleShape)
                                .background(GlassCard)
                                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Detection Settings",
                                tint = TextPrimary
                            )
                        }
                    }
                }
            }

            // Real-Time Performance Monitor Overlay (Top-Left under Header)
            PerformanceOverlay(
                metrics = performanceMetrics,
                isVisible = settings.showPerformanceOverlay,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 100.dp, start = 16.dp)
            )

            // Bottom Tracking Status Pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                IndigoDark.copy(alpha = 0.8f),
                                IndigoDark
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HUDBackground)
                        .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DETECTION ENGINE",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Confidence: ${settings.confidenceLevel.label}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("quick_settings_button"),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.horizontalGradient(listOf(CyanPrimary, CyanDark))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Adjust",
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Camera Error Dialog Banner if camera failed
            if (cameraState is CameraState.Error) {
                val errorMsg = (cameraState as CameraState.Error).message
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(IndigoSurface)
                        .border(1.dp, RedAlert, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Error",
                            tint = RedAlert,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Unavailable",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMsg,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                previewViewInstance?.let { pView ->
                                    viewModel.cameraManager.startCamera(lifecycleOwner, pView, settings.isFrontCamera)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                        ) {
                            Text("Retry Camera", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Camera Permission Denied UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.12f))
                            .border(2.dp, CyanPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Camera Permission Required",
                            tint = CyanPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Camera permission is required.",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Advanced Vision Studio uses on-device camera processing to detect objects, compute trajectory vectors, and analyze direction in real time.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = Brush.horizontalGradient(listOf(CyanPrimary, CyanDark))
                            ),
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Text("Open Settings", color = CyanPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Settings Bottom Sheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = IndigoSurface,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETECTION SETTINGS",
                            color = CyanPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = { showSettingsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Detection Confidence",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Set minimum confidence score required to display bounding box and track object.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ConfidenceLevel.values().forEach { level ->
                            val isSelected = settings.confidenceLevel == level
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setConfidenceLevel(level) },
                                label = {
                                    Text(
                                        text = level.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanPrimary,
                                    selectedLabelColor = Color.Black,
                                    containerColor = GlassCard,
                                    labelColor = TextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) CyanPrimary else CyanPrimary.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("confidence_chip_${level.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Performance Overlay Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassCard)
                            .border(1.dp, CyanPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.togglePerformanceOverlay() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Performance Overlay",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Display real FPS, detection speed, tracking, and inference ms",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = if (settings.showPerformanceOverlay) "ON" else "OFF",
                            color = if (settings.showPerformanceOverlay) CyanPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

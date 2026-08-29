package com.example.vision.hand.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vision.camera.CameraState
import com.example.vision.hand.model.DrawingTool
import com.example.vision.ui.ActiveVisionMode
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.camera.PerformanceOverlay
import kotlinx.coroutines.launch

@Composable
fun HandTrackingScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State bindings
    val handPoses by viewModel.handPoses.collectAsStateWithLifecycle()
    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val activeStroke by viewModel.activeStroke.collectAsStateWithLifecycle()
    val airDrawingSettings by viewModel.airDrawingSettings.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isPinching by viewModel.isAirDrawingPinching.collectAsStateWithLifecycle()
    val performanceMetrics by viewModel.handPerformanceMetrics.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGestureTip by remember { mutableStateOf(true) }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }
    var screenWidthPx by remember { mutableStateOf(1080) }
    var screenHeightPx by remember { mutableStateOf(2400) }

    // Ensure hand tracking mode is active on entry
    LaunchedEffect(Unit) {
        viewModel.setVisionMode(ActiveVisionMode.HAND_TRACKING_AIR_DRAW)
    }

    // Camera Permission check
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
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.cameraManager.stopCamera()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("hand_tracking_screen")
    ) {
        if (hasCameraPermission) {
            // 1. CameraX Preview Layer
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
                            useFrontCamera = true // Front camera default for interactive air gestures
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("hand_camera_preview")
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            screenWidthPx = size.width
                            screenHeightPx = size.height
                            viewModel.updatePreviewDimensions(
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                        }
                    }
            )

            // 2. Hand Skeleton & Air Drawing Canvas Overlay
            HandLandmarkOverlay(
                handPoses = handPoses,
                strokes = strokes,
                activeStroke = activeStroke,
                showSkeleton = airDrawingSettings.showHandSkeleton,
                currentTool = airDrawingSettings.currentTool,
                brushColor = airDrawingSettings.brushColor,
                brushSizePx = airDrawingSettings.brushSize.strokeWidthPx,
                isAirDrawingActive = airDrawingSettings.isAirDrawingActive,
                modifier = Modifier.fillMaxSize()
            )

            // 3. Top Bar Navigation & Status HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(top = 36.dp, bottom = 12.dp)
            ) {
                HandTrackingTopBar(
                    handPoses = handPoses,
                    isAirDrawingActive = airDrawingSettings.isAirDrawingActive,
                    isPinching = isPinching,
                    onBackClick = onNavigateBack,
                    onFlipCamera = {
                        previewViewInstance?.let { pView ->
                            viewModel.toggleCameraFacing(lifecycleOwner, pView)
                        }
                    },
                    onOpenSettings = { showSettingsSheet = true }
                )
            }

            // 4. Live Pinch Status Pill (Center Top)
            AnimatedVisibility(
                visible = isPinching,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFB300).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                    modifier = Modifier.testTag("drawing_indicator_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (airDrawingSettings.currentTool == DrawingTool.ERASER) "ERASING" else "AIR DRAWING",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // 5. Initial Gesture Tip Banner (Dismissible)
            AnimatedVisibility(
                visible = showGestureTip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 24.dp, end = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "How to Air Draw",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Pinch Thumb + Index finger together to draw. Release pinch to pause stroke.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss tip",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { showGestureTip = false }
                        )
                    }
                }
            }

            // 6. Live Performance Overlay
            if (airDrawingSettings.showPerformanceOverlay) {
                PerformanceOverlay(
                    metrics = performanceMetrics,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 104.dp)
                )
            }

            // 7. Bottom Air Drawing Dock
            AirDrawingDock(
                settings = airDrawingSettings,
                canUndo = canUndo,
                canRedo = canRedo,
                onToolChange = { tool -> viewModel.setDrawingTool(tool) },
                onColorChange = { color -> viewModel.setBrushColor(color) },
                onBrushSizeChange = { size -> viewModel.setBrushSize(size) },
                onUndo = { viewModel.undoStroke() },
                onRedo = { viewModel.redoStroke() },
                onClear = {
                    viewModel.clearCanvas()
                    Toast.makeText(context, "Canvas cleared", Toast.LENGTH_SHORT).show()
                },
                onSave = {
                    viewModel.saveDrawing(
                        width = screenWidthPx,
                        height = screenHeightPx
                    ) { uri ->
                        if (uri != null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Drawing saved to Pictures / AdvancedVision")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Failed to save drawing")
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )

            // Settings Sheet
            if (showSettingsSheet) {
                HandTrackingSettingsSheet(
                    settings = airDrawingSettings,
                    onSettingsChanged = { updated ->
                        viewModel.updateAirDrawingSettings(updated)
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }
        } else {
            // Permission Denied View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141A22),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color(0xFFFF6E6E),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Hand tracking and air drawing require camera access to track 21 skeletal landmarks on-device.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().testTag("grant_permission_button")
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open App Settings", color = Color.White)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
        )
    }
}

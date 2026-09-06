package com.example.vision.person.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vision.model.MovementStatus
import com.example.vision.model.TrackedObject
import com.example.vision.ui.ActiveVisionMode
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.camera.DetectionOverlay
import com.example.vision.ui.camera.PerformanceOverlay
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanDark
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GlassCard
import com.example.vision.ui.theme.GreenStationary
import com.example.vision.ui.theme.HUDBackground
import com.example.vision.ui.theme.IndigoDark
import com.example.vision.ui.theme.IndigoSurface
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary
import com.example.vision.ui.theme.TextTertiary

@Composable
fun PersonTrackingScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val personObjects by viewModel.personTrackedObjects.collectAsStateWithLifecycle()
    val totalSessionCount by viewModel.totalSessionPedestrians.collectAsStateWithLifecycle()
    val metrics by viewModel.performanceMetrics.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
        viewModel.setVisionMode(ActiveVisionMode.PERSON_TRACKING)
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

            // Real-Time GPU Detection Overlay for Persons
            DetectionOverlay(
                trackedObjects = personObjects,
                modifier = Modifier.fillMaxSize()
            )

            // Performance Overlay
            if (settings.showPerformanceOverlay) {
                PerformanceOverlay(
                    metrics = metrics,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 100.dp, end = 16.dp)
                )
            }

            // Top Header Bar
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
                            .testTag("person_back_button")
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
                            text = "PERSON TRACKING",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${personObjects.size} IN FRAME • $totalSessionCount TOTAL SEEN",
                            color = GreenStationary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = {
                            previewViewInstance?.let { pView ->
                                viewModel.toggleCameraFacing(lifecycleOwner, pView)
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GlassCard)
                            .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip",
                            tint = CyanPrimary
                        )
                    }
                }
            }

            // Privacy Assurance Banner (Top Subheader)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HUDBackground.copy(alpha = 0.9f))
                    .border(1.dp, GreenStationary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = GreenStationary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ANONYMOUS TRACKING • NO FACIAL RECOGNITION",
                        color = GreenStationary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom Pedestrian Flow Dashboard Card
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
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HUDBackground)
                        .border(1.dp, CyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PEDESTRIAN TELEMETRY",
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "STATUS: ACTIVE",
                            color = GreenStationary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current In Frame", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${personObjects.size}", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                        }
                        Column {
                            Text("Total Session Seen", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("$totalSessionCount", color = CyanPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                        }
                        Column {
                            Text("Moving / Walking", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            val movingCount = personObjects.count { it.movementStatus == MovementStatus.MOVING }
                            Text("$movingCount", color = AmberAccent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

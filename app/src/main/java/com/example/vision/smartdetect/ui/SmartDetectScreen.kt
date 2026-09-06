package com.example.vision.smartdetect.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.vision.model.ConfidenceLevel
import com.example.vision.model.SmartDetectCategory
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
fun SmartDetectScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val detectedObjects by viewModel.smartDetectedObjects.collectAsStateWithLifecycle()
    val metrics by viewModel.performanceMetrics.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.smartCategoryFilter.collectAsStateWithLifecycle()

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
        viewModel.setVisionMode(ActiveVisionMode.SMART_DETECT)
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

            // GPU Accelerated Vision Overlay
            DetectionOverlay(
                trackedObjects = detectedObjects,
                modifier = Modifier.fillMaxSize()
            )

            // Performance HUD
            if (settings.showPerformanceOverlay) {
                PerformanceOverlay(
                    metrics = metrics,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 150.dp, end = 16.dp)
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
                            .testTag("smart_detect_back_button")
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
                            text = "SMART DETECT",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${detectedObjects.size} OBJECTS IN FRAME",
                            color = if (detectedObjects.isNotEmpty()) GreenStationary else AmberAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.toggleTorch() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(GlassCard)
                                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (settings.isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flashlight",
                                tint = if (settings.isFlashlightOn) AmberAccent else TextSecondary
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
            }

            // Category Filter Scrolling Bar (Top)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 96.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SmartDetectCategory.values()) { category ->
                        val isSelected = category == selectedCategory
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) CyanPrimary else HUDBackground)
                                .border(1.dp, if (isSelected) CyanPrimary else CyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                .clickable { viewModel.setSmartCategoryFilter(category) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = category.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = category.displayName,
                                color = if (isSelected) Color.Black else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Bottom Category Breakdown Card
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
                        Text(
                            text = "ACTIVE DETECTIONS",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // Confidence level selector
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ConfidenceLevel.values().forEach { level ->
                                val isSelected = settings.confidenceLevel == level
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) CyanPrimary else Color.Transparent)
                                        .clickable { viewModel.setConfidenceLevel(level) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = level.name.take(3),
                                        color = if (isSelected) Color.Black else TextTertiary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (detectedObjects.isEmpty()) {
                        Text(
                            text = "Scanning surroundings... Point camera at objects, people, or vehicles.",
                            color = TextTertiary,
                            fontSize = 11.5.sp
                        )
                    } else {
                        val grouped = detectedObjects.groupBy { it.label }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(grouped.toList()) { (label, list) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(IndigoSurface)
                                        .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(CyanPrimary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${list.size}",
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

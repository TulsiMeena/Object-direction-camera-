package com.example.vision.tracking.ui

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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vision.model.Direction
import com.example.vision.model.LockedTarget
import com.example.vision.model.MovementStatus
import com.example.vision.model.TrackedObject
import com.example.vision.ui.ActiveVisionMode
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.camera.PerformanceOverlay
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
fun ObjectTrackingScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activeObjects by viewModel.trackedObjects.collectAsStateWithLifecycle()
    val lockedTarget by viewModel.lockedTarget.collectAsStateWithLifecycle()
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
        viewModel.setVisionMode(ActiveVisionMode.OBJECT_TRACKING)
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.cameraManager.stopCamera()
            viewModel.unlockTarget()
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

            // Tap gesture to lock onto any object
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeObjects) {
                        detectTapGestures { offset ->
                            val tapped = activeObjects.find { it.displayBoundingBox.contains(offset.x, offset.y) }
                            if (tapped != null) {
                                viewModel.lockOnObject(tapped)
                            } else {
                                // Find closest object within 120px
                                val closest = activeObjects.minByOrNull {
                                    val dx = it.displayBoundingBox.centerX() - offset.x
                                    val dy = it.displayBoundingBox.centerY() - offset.y
                                    Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                }
                                if (closest != null) {
                                    viewModel.lockOnObject(closest)
                                }
                            }
                        }
                    }
            )

            // Canvas for rendering Tracking HUD & Reticles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val target = lockedTarget

                if (target == null) {
                    // Draw selection bounding boxes for all candidate objects
                    for (obj in activeObjects) {
                        val box = obj.displayBoundingBox
                        val isMoving = obj.movementStatus == MovementStatus.MOVING
                        val color = if (isMoving) AmberAccent else CyanPrimary

                        // Subtle candidate box
                        drawRoundRect(
                            color = color.copy(alpha = 0.6f),
                            topLeft = Offset(box.left, box.top),
                            size = Size(box.width(), box.height()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                            style = Stroke(width = 2f)
                        )

                        // Center lock prompt crosshair
                        val cx = box.centerX()
                        val cy = box.centerY()
                        drawCircle(
                            color = color.copy(alpha = 0.8f),
                            radius = 6f,
                            center = Offset(cx, cy)
                        )
                    }
                } else {
                    // Draw LOCKED TARGET High-Tech Reticle
                    val box = target.boundingBox
                    val isRecovering = target.isRecovering
                    val reticleColor = if (isRecovering) AmberAccent else CyanPrimary

                    // Trajectory Path Trail
                    if (target.trajectory.size > 1) {
                        val path = Path().apply {
                            moveTo(target.trajectory.first().x, target.trajectory.first().y)
                            for (pt in target.trajectory.drop(1)) {
                                lineTo(pt.x, pt.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = CyanPrimary.copy(alpha = 0.5f),
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                    }

                    // Pulsing Outer Reticle Box
                    drawRoundRect(
                        color = reticleColor.copy(alpha = 0.2f),
                        topLeft = Offset(box.left - 6f, box.top - 6f),
                        size = Size(box.width() + 12f, box.height() + 12f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                    )

                    // Target Corner Brackets
                    val cornerLen = 22f
                    val strokeW = 4f

                    // Top-Left
                    drawLine(reticleColor, Offset(box.left, box.top), Offset(box.left + cornerLen, box.top), strokeW)
                    drawLine(reticleColor, Offset(box.left, box.top), Offset(box.left, box.top + cornerLen), strokeW)

                    // Top-Right
                    drawLine(reticleColor, Offset(box.right, box.top), Offset(box.right - cornerLen, box.top), strokeW)
                    drawLine(reticleColor, Offset(box.right, box.top), Offset(box.right, box.top + cornerLen), strokeW)

                    // Bottom-Left
                    drawLine(reticleColor, Offset(box.left, box.bottom), Offset(box.left + cornerLen, box.bottom), strokeW)
                    drawLine(reticleColor, Offset(box.left, box.bottom), Offset(box.left, box.bottom - cornerLen), strokeW)

                    // Bottom-Right
                    drawLine(reticleColor, Offset(box.right, box.bottom), Offset(box.right - cornerLen, box.bottom), strokeW)
                    drawLine(reticleColor, Offset(box.right, box.bottom), Offset(box.right, box.bottom - cornerLen), strokeW)

                    // Center Target Reticle Cross
                    val cx = box.centerX()
                    val cy = box.centerY()
                    drawCircle(
                        color = reticleColor,
                        radius = 18f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = reticleColor,
                        radius = 4f,
                        center = Offset(cx, cy)
                    )
                }
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
                            .testTag("tracking_back_button")
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
                            text = "OBJECT TRACKING",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (lockedTarget != null) "TARGET LOCKED" else "TAP OBJECT TO LOCK",
                            color = if (lockedTarget != null) GreenStationary else AmberAccent,
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

            // Bottom Target Lock Telemetry Card
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
                val target = lockedTarget
                if (target != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(HUDBackground)
                            .border(1.5.dp, if (target.isRecovering) AmberAccent else CyanPrimary, RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (target.isRecovering) AmberAccent else CyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = target.targetId,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = { viewModel.unlockTarget() },
                                colors = ButtonDefaults.buttonColors(containerColor = RedAlert.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("unlock_target_button")
                            ) {
                                Text("Unlock", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (target.isRecovering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AmberAccent.copy(alpha = 0.2f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "TARGET OCCLUDED — PREDICTIVE RECOVERY ACTIVE...",
                                    color = AmberAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Direction", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                                Text("${target.direction.displayName} ${target.direction.symbol}", color = CyanPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Status", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                                Text(target.movementStatus.displayName, color = if (target.movementStatus == MovementStatus.MOVING) AmberAccent else GreenStationary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Confidence", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                                Text("${(target.confidence * 100).toInt()}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Speed", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                                Text("${target.speedPxPerSec.toInt()} px/s", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(HUDBackground)
                            .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AVAILABLE TARGETS (${activeObjects.size})",
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (activeObjects.isEmpty()) "Scanning for objects... Move camera towards vehicles, people, or items." else "Tap any highlighted box on screen to lock tracking identity.",
                            color = TextTertiary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

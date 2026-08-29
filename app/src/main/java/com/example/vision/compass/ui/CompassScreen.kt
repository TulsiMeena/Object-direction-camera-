package com.example.vision.compass.ui

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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.vision.model.CardinalDirection
import com.example.vision.model.CompassHeading
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val heading by viewModel.compassHeading.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showCalibrateModal by remember { mutableStateOf(false) }
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
        viewModel.setVisionMode(ActiveVisionMode.DIRECTION_COMPASS)
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
    ) {
        if (hasCameraPermission) {
            // Live Camera Background Feed
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

            // AR Floating Horizon Cardinal Overlay + Compass Graphic Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // Draw AR Cardinal Direction markers along the horizon line
                val horizonY = canvasH * 0.32f
                val azimuth = heading.azimuthDegrees

                // Render Floating AR Cardinal points on camera
                val cardinalPoints = listOf(
                    Pair(0f, "N"), Pair(45f, "NE"), Pair(90f, "E"), Pair(135f, "SE"),
                    Pair(180f, "S"), Pair(225f, "SW"), Pair(270f, "W"), Pair(315f, "NW")
                )

                // FOV ~ 60 degrees across screen width
                val pxPerDegree = canvasW / 60f

                for ((bearing, label) in cardinalPoints) {
                    var diff = (bearing - azimuth)
                    while (diff > 180f) diff -= 360f
                    while (diff < -180f) diff += 360f

                    if (diff in -35f..35f) {
                        val screenX = (canvasW / 2f) + (diff * pxPerDegree)
                        val isNorth = label == "N"

                        // Draw AR marker tick
                        drawLine(
                            color = if (isNorth) RedAlert else CyanPrimary,
                            start = Offset(screenX, horizonY - 12f),
                            end = Offset(screenX, horizonY + 12f),
                            strokeWidth = if (isNorth) 3f else 2f
                        )

                        // Draw Native Text
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = if (isNorth) android.graphics.Color.parseColor("#EF4444") else android.graphics.Color.WHITE
                                textSize = if (isNorth) 36f else 28f
                                isFakeBoldText = true
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                            }
                            drawText(label, screenX, horizonY - 20f, paint)
                            drawText("${bearing.toInt()}°", screenX, horizonY + 34f, paint.apply {
                                textSize = 20f
                                color = android.graphics.Color.parseColor("#94A3B8")
                            })
                        }
                    }
                }

                // Draw 360° Circular Compass Dial in Center-Lower screen
                val dialCenterX = canvasW / 2f
                val dialCenterY = canvasH * 0.58f
                val dialRadius = canvasW * 0.32f

                // Outer decorative dial ring
                drawCircle(
                    color = HUDBackground.copy(alpha = 0.85f),
                    radius = dialRadius,
                    center = Offset(dialCenterX, dialCenterY)
                )
                drawCircle(
                    color = CyanPrimary.copy(alpha = 0.4f),
                    radius = dialRadius,
                    center = Offset(dialCenterX, dialCenterY),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = CyanPrimary.copy(alpha = 0.15f),
                    radius = dialRadius * 0.72f,
                    center = Offset(dialCenterX, dialCenterY),
                    style = Stroke(width = 1f)
                )

                // Rotate compass rose by -azimuth so North points to actual magnetic North
                rotate(degrees = -azimuth, pivot = Offset(dialCenterX, dialCenterY)) {
                    // Draw 360 tick marks
                    for (deg in 0 until 360 step 15) {
                        val rad = Math.toRadians(deg.toDouble()).toFloat()
                        val isMajor = deg % 90 == 0
                        val isMedium = deg % 45 == 0
                        val tickLen = if (isMajor) 18f else if (isMedium) 12f else 6f

                        val startR = dialRadius - tickLen
                        val startX = dialCenterX + startR * sin(rad)
                        val startY = dialCenterY - startR * cos(rad)

                        val endX = dialCenterX + dialRadius * sin(rad)
                        val endY = dialCenterY - dialRadius * cos(rad)

                        drawLine(
                            color = if (deg == 0) RedAlert else if (isMajor) CyanPrimary else TextTertiary,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = if (isMajor) 2.5f else 1.5f
                        )
                    }

                    // North Needle Arrow (Red)
                    val needlePathNorth = Path().apply {
                        moveTo(dialCenterX, dialCenterY - dialRadius * 0.85f)
                        lineTo(dialCenterX - 14f, dialCenterY)
                        lineTo(dialCenterX + 14f, dialCenterY)
                        close()
                    }
                    drawPath(needlePathNorth, RedAlert)

                    // South Needle Arrow (White/Silver)
                    val needlePathSouth = Path().apply {
                        moveTo(dialCenterX, dialCenterY + dialRadius * 0.85f)
                        lineTo(dialCenterX - 14f, dialCenterY)
                        lineTo(dialCenterX + 14f, dialCenterY)
                        close()
                    }
                    drawPath(needlePathSouth, Color.White.copy(alpha = 0.85f))
                }

                // Center Pivot Hub
                drawCircle(
                    color = CyanPrimary,
                    radius = 8f,
                    center = Offset(dialCenterX, dialCenterY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 4f,
                    center = Offset(dialCenterX, dialCenterY)
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
                            .testTag("compass_back_button")
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
                            text = "DIRECTION / COMPASS",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SENSOR FUSION ACTIVE",
                            color = GreenStationary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = { showCalibrateModal = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GlassCard)
                            .border(1.dp, if (heading.isCalibrated) CyanPrimary.copy(alpha = 0.3f) else AmberAccent, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Calibrate",
                            tint = if (heading.isCalibrated) CyanPrimary else AmberAccent
                        )
                    }
                }
            }

            // Digital Heading HUD Display Card (Top Center)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HUDBackground)
                    .border(1.dp, CyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${heading.cardinal.symbol}",
                        color = if (heading.cardinal == CardinalDirection.N) RedAlert else CyanPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "↑",
                        color = CyanPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%03d°", heading.azimuthDegrees.toInt()),
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Heading: ${heading.azimuthDegrees.toInt()}°",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Magnetic Interference Alert Banner if accuracy is low
            if (!heading.isCalibrated) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(IndigoSurface.copy(alpha = 0.95f))
                        .border(1.dp, AmberAccent, RoundedCornerShape(14.dp))
                        .clickable { showCalibrateModal = true }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Magnetic Interference Detected", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Tap to calibrate compass sensors", color = AmberAccent, fontSize = 10.5.sp)
                        }
                    }
                }
            }

            // Bottom Telemetry Bar (Pitch, Roll, Magnetic Field)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "PITCH", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "${heading.pitchDegrees.toInt()}°", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "ROLL", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "${heading.rollDegrees.toInt()}°", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "MAG FIELD", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "${heading.magneticFieldStrengthUf.toInt()} µT", color = GreenStationary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "ACCURACY", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (heading.isCalibrated) "HIGH" else "CALIBRATE",
                            color = if (heading.isCalibrated) GreenStationary else AmberAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Calibration Modal Dialog
        if (showCalibrateModal) {
            AlertDialog(
                onDismissRequest = { showCalibrateModal = false },
                containerColor = IndigoSurface,
                title = {
                    Text(
                        text = "COMPASS CALIBRATION",
                        color = CyanPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "To calibrate the magnetometer and ensure precise heading accuracy:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.15f))
                                .border(1.5.dp, CyanPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "♾️", fontSize = 42.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Wave your device smoothly in a figure-8 motion for 5-10 seconds.",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showCalibrateModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

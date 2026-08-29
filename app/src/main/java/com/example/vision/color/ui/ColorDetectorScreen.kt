package com.example.vision.color.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.vision.model.ColorSwatch
import com.example.vision.model.SampledColor
import com.example.vision.ui.ActiveVisionMode
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanDark
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GlassCard
import com.example.vision.ui.theme.GreenStationary
import com.example.vision.ui.theme.HUDBackground
import com.example.vision.ui.theme.IndigoBorder
import com.example.vision.ui.theme.IndigoDark
import com.example.vision.ui.theme.IndigoSurface
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary
import com.example.vision.ui.theme.TextTertiary

@Composable
fun ColorDetectorScreen(
    viewModel: VisionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sampledColor by viewModel.sampledColor.collectAsStateWithLifecycle()
    val paletteHistory by viewModel.colorPaletteHistory.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var normX by remember { mutableFloatStateOf(0.5f) }
    var normY by remember { mutableFloatStateOf(0.5f) }

    var viewW by remember { mutableFloatStateOf(1080f) }
    var viewH by remember { mutableFloatStateOf(1920f) }
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
        viewModel.setVisionMode(ActiveVisionMode.COLOR_DETECTOR)
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

            // Touch / Drag layer for targeting reticle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            normX = (offset.x / viewW).coerceIn(0.05f, 0.95f)
                            normY = (offset.y / viewH).coerceIn(0.05f, 0.95f)
                            viewModel.updateColorSamplePoint(normX, normY)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newX = (normX * viewW + dragAmount.x) / viewW
                            val newY = (normY * viewH + dragAmount.y) / viewH
                            normX = newX.coerceIn(0.05f, 0.95f)
                            normY = newY.coerceIn(0.05f, 0.95f)
                            viewModel.updateColorSamplePoint(normX, normY)
                        }
                    }
            )

            // Dynamic Targeting Reticle Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val targetPixelX = normX * size.width
                val targetPixelY = normY * size.height
                val targetOffset = Offset(targetPixelX, targetPixelY)

                val currentColor = Color(sampledColor.red, sampledColor.green, sampledColor.blue)

                // Outer pulsing sampling ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    radius = 36f,
                    center = targetOffset
                )
                // Color preview ring
                drawCircle(
                    color = currentColor,
                    radius = 26f,
                    center = targetOffset
                )
                // High contrast white boundary
                drawCircle(
                    color = Color.White,
                    radius = 26f,
                    center = targetOffset,
                    style = Stroke(width = 3.5f)
                )
                // Crosshair tick marks
                val tickLen = 14f
                val gap = 32f
                drawLine(
                    color = Color.White,
                    start = Offset(targetPixelX - gap - tickLen, targetPixelY),
                    end = Offset(targetPixelX - gap, targetPixelY),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(targetPixelX + gap, targetPixelY),
                    end = Offset(targetPixelX + gap + tickLen, targetPixelY),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(targetPixelX, targetPixelY - gap - tickLen),
                    end = Offset(targetPixelX, targetPixelY - gap),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(targetPixelX, targetPixelY + gap),
                    end = Offset(targetPixelX, targetPixelY + gap + tickLen),
                    strokeWidth = 2f
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
                            .testTag("color_back_button")
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
                            text = "COLOR DETECTOR",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "TAP OR DRAG TARGET ON SCREEN",
                            color = TextSecondary,
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
                            contentDescription = "Flip Camera",
                            tint = CyanPrimary
                        )
                    }
                }
            }

            // Bottom Color Inspection Card & Saved Palette
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(HUDBackground)
                        .border(1.5.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    // Header with Color Name & Approximate Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "COLOR",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AmberAccent.copy(alpha = 0.2f))
                                    .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Approximate",
                                    color = AmberAccent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Copy HEX button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassCard)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("HEX Color", sampledColor.hex))
                                    Toast.makeText(context, "Copied ${sampledColor.hex}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy HEX", tint = CyanPrimary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Copy HEX", color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Color Swatch + Name + HEX
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Large Color Swatch Box
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(sampledColor.red, sampledColor.green, sampledColor.blue))
                                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Name: ${sampledColor.name}",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "RGB: ${sampledColor.red}, ${sampledColor.green}, ${sampledColor.blue}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "HEX: ${sampledColor.hex}",
                                color = CyanPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // RGB Component Visual Level Bars
                    ColorComponentBar(label = "R", value = sampledColor.red, color = Color(0xFFFF4D4D))
                    Spacer(modifier = Modifier.height(6.dp))
                    ColorComponentBar(label = "G", value = sampledColor.green, color = Color(0xFF00E676))
                    Spacer(modifier = Modifier.height(6.dp))
                    ColorComponentBar(label = "B", value = sampledColor.blue, color = Color(0xFF2979FF))

                    Spacer(modifier = Modifier.height(14.dp))

                    // Save to Palette button & History row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.saveCurrentColorSwatch() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_color_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Swatch", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (paletteHistory.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearColorHistory() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Saved Swatches List
                    if (paletteHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "SAVED PALETTE (${paletteHistory.size})",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(paletteHistory, key = { it.id }) { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(swatch.color.red, swatch.color.green, swatch.color.blue))
                                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("HEX Color", swatch.color.hex))
                                            Toast.makeText(context, "${swatch.color.name} (${swatch.color.hex}) copied!", Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorComponentBar(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(18.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(IndigoDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value / 255f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp)
        )
    }
}

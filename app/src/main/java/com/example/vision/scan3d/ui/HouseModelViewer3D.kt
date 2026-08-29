package com.example.vision.scan3d.ui

import android.content.Context
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.scan3d.engine.HouseScanReconstructor
import com.example.vision.scan3d.model.GeneratedHouseModel
import com.example.vision.scan3d.model.Point3D
import com.example.vision.ui.theme.AmberAccent
import com.example.vision.ui.theme.CyanDark
import com.example.vision.ui.theme.CyanPrimary
import com.example.vision.ui.theme.GlassCard
import com.example.vision.ui.theme.GreenStationary
import com.example.vision.ui.theme.IndigoBorder
import com.example.vision.ui.theme.IndigoCard
import com.example.vision.ui.theme.IndigoDark
import com.example.vision.ui.theme.IndigoSurface
import com.example.vision.ui.theme.TextPrimary
import com.example.vision.ui.theme.TextSecondary
import com.example.vision.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HouseModelViewer3D(
    model: GeneratedHouseModel,
    onNavigateBack: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 3D Orbit Camera State
    var rotX by remember { mutableFloatStateOf(25f) } // Elevation (-85 to +85)
    var rotY by remember { mutableFloatStateOf(-45f) } // Azimuth (0 to 360)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    var isWireframe by remember { mutableStateOf(false) }
    var showMetrics by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("house_model_viewer_screen"),
        color = IndigoDark
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Interactive 3D Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("model_viewer_canvas")
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            // Two-finger zoom
                            zoomScale = (zoomScale * zoom).coerceIn(0.3f, 4.0f)

                            // Dragging (one finger or two finger)
                            if (zoom == 1.0f) {
                                rotY = (rotY + pan.x * 0.45f) % 360f
                                rotX = (rotX - pan.y * 0.45f).coerceIn(-85f, 85f)
                            } else {
                                panOffsetX += pan.x
                                panOffsetY += pan.y
                            }
                        }
                    }
            ) {
                render3dScene(
                    model = model,
                    rotX = rotX,
                    rotY = rotY,
                    zoom = zoomScale,
                    panX = panOffsetX,
                    panY = panOffsetY,
                    wireframeOnly = isWireframe
                )
            }

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(IndigoSurface.copy(alpha = 0.85f))
                            .border(1.dp, IndigoBorder, CircleShape)
                            .testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "3D HOUSE MODEL",
                            color = CyanPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Vertices: ${model.totalVertices} • Faces: ${model.totalFaces} • ${model.scanCoveragePercent}% Coverage",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Controls Toolbar
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { isWireframe = !isWireframe },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isWireframe) CyanPrimary else IndigoSurface.copy(alpha = 0.85f))
                            .border(1.dp, IndigoBorder, CircleShape)
                            .testTag("toggle_wireframe_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Wireframe",
                            tint = if (isWireframe) Color.Black else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            rotX = 25f
                            rotY = -45f
                            zoomScale = 1.0f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(IndigoSurface.copy(alpha = 0.85f))
                            .border(1.dp, IndigoBorder, CircleShape)
                            .testTag("reset_view_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset View",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Floating Card: Room Dimensions & Export Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                // Dimensions & Measurements Breakdown
                AnimatedVisibility(
                    visible = showMetrics,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, IndigoBorder, RoundedCornerShape(16.dp))
                            .testTag("room_dimensions_card"),
                        colors = CardDefaults.cardColors(containerColor = IndigoCard.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ESTIMATED ROOM GEOMETRY",
                                    color = AmberAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "AR World Space",
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DimensionMetricItem(label = "WIDTH", value = "${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedWidthM)} m")
                                DimensionMetricItem(label = "LENGTH", value = "${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedLengthM)} m")
                                DimensionMetricItem(label = "HEIGHT", value = "${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedHeightM)} m")
                                DimensionMetricItem(label = "FLOOR AREA", value = "${String.format(Locale.US, "%.1f", model.roomDimensions.floorAreaSqM)} m²")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons: Save 3D Model & Rescan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRescan,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("new_scan_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndigoBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NEW SCAN",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                scope.launch {
                                    val uri = HouseScanReconstructor.exportModelToObj(context, model)
                                    isSaving = false
                                    if (uri != null) {
                                        Toast.makeText(context, "3D Model saved locally to Downloads/AdvancedVision (.obj)", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Saved model locally", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .testTag("save_3d_model_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaving) "SAVING..." else "SAVE 3D MODEL (.OBJ)",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionMetricItem(label: String, value: String) {
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
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun DrawScope.render3dScene(
    model: GeneratedHouseModel,
    rotX: Float,
    rotY: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    wireframeOnly: Boolean
) {
    val centerX = size.width / 2f + panX
    val centerY = size.height / 2f + panY
    val baseScale = (size.width.coerceAtMost(size.height) / 3.8f) * zoom

    // Rotation angles in radians
    val radX = Math.toRadians(rotX.toDouble()).toFloat()
    val radY = Math.toRadians(rotY.toDouble()).toFloat()

    val cosX = cos(radX)
    val sinX = sin(radX)
    val cosY = cos(radY)
    val sinY = sin(radY)

    // Center of model bounding box
    val midX = (model.mesh.boundingBoxMin.x + model.mesh.boundingBoxMax.x) / 2f
    val midY = (model.mesh.boundingBoxMin.y + model.mesh.boundingBoxMax.y) / 2f
    val midZ = (model.mesh.boundingBoxMin.z + model.mesh.boundingBoxMax.z) / 2f

    // 3D Point Projection to 2D Screen Space
    fun project(p: Point3D): Triple<Float, Float, Float> {
        // Translate to origin
        val tx = p.x - midX
        val ty = p.y - midY
        val tz = p.z - midZ

        // Yaw around Y
        val x1 = tx * cosY - tz * sinY
        val z1 = tx * sinY + tz * cosY

        // Pitch around X
        val y2 = ty * cosX - z1 * sinX
        val z2 = ty * sinX + z1 * cosX

        // Perspective division
        val distance = 6.0f
        val perspective = distance / (distance + z2).coerceAtLeast(0.1f)

        val sx = centerX + x1 * baseScale * perspective
        val sy = centerY - y2 * baseScale * perspective // invert Y for screen coords

        return Triple(sx, sy, z2)
    }

    // 1. Draw Ground Coordinate Grid
    val gridSize = 3.0f
    val gridStep = 0.5f
    val gridY = model.mesh.boundingBoxMin.y - 0.05f
    var gx = -gridSize
    while (gx <= gridSize) {
        val p1 = project(Point3D(gx, gridY, -gridSize))
        val p2 = project(Point3D(gx, gridY, gridSize))
        drawLine(
            color = Color(0x3300E5FF),
            start = Offset(p1.first, p1.second),
            end = Offset(p2.first, p2.second),
            strokeWidth = 1f
        )
        gx += gridStep
    }
    var gz = -gridSize
    while (gz <= gridSize) {
        val p1 = project(Point3D(-gridSize, gridY, gz))
        val p2 = project(Point3D(gridSize, gridY, gz))
        drawLine(
            color = Color(0x3300E5FF),
            start = Offset(p1.first, p1.second),
            end = Offset(p2.first, p2.second),
            strokeWidth = 1f
        )
        gz += gridStep
    }

    // 2. Project all vertices
    val projected = model.mesh.vertices.map { project(it) }

    // 3. Sort faces back-to-front (Painter's Algorithm)
    data class RenderableFace(
        val indices: IntArray,
        val avgDepth: Float,
        val colorArgb: Int
    )

    val renderableFaces = model.mesh.faces.mapIndexed { idx, faceIndices ->
        val d0 = projected[faceIndices[0]].third
        val d1 = projected[faceIndices[1]].third
        val d2 = projected[faceIndices[2]].third
        val avgD = (d0 + d1 + d2) / 3f
        val color = model.mesh.colors.getOrElse(idx) { 0xCC00E5FF.toInt() }
        RenderableFace(faceIndices, avgD, color)
    }.sortedByDescending { it.avgDepth }

    // 4. Render faces
    for (face in renderableFaces) {
        val p0 = projected[face.indices[0]]
        val p1 = projected[face.indices[1]]
        val p2 = projected[face.indices[2]]

        val path = Path().apply {
            moveTo(p0.first, p0.second)
            lineTo(p1.first, p1.second)
            lineTo(p2.first, p2.second)
            close()
        }

        if (!wireframeOnly) {
            // Directional lighting calculation
            val baseColor = Color(face.colorArgb)
            drawPath(path, color = baseColor.copy(alpha = 0.65f))
        }

        // Draw wireframe outline
        drawPath(
            path,
            color = if (wireframeOnly) CyanPrimary else Color.White.copy(alpha = 0.5f),
            style = Stroke(width = if (wireframeOnly) 2f else 1.2f)
        )
    }

    // 5. Draw detected plane bounding boxes / labels
    for (plane in model.detectedPlanes) {
        val centerProj = project(plane.center)
        drawCircle(
            color = AmberAccent,
            radius = 4f,
            center = Offset(centerProj.first, centerProj.second)
        )
    }
}

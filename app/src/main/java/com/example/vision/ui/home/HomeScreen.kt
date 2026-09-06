package com.example.vision.ui.home

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.model.VisionFeature
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

val STUDIO_FEATURES = listOf(
    VisionFeature(
        id = "object_direction",
        icon = "🚗",
        title = "Object & Direction Camera",
        description = "Real-time multi-object detection, persistent tracking IDs, and temporal direction analysis.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "hand_tracking",
        icon = "✋",
        title = "Hand Tracking + Air Drawing",
        description = "21-landmark skeletal tracking, continuous pinch air drawing, eraser, and gallery export.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "house_scan_3d",
        icon = "🏠",
        title = "3D House Scan",
        description = "ARCore spatial plane detection, live room geometry reconstruction, and 3D OBJ export.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "measure",
        icon = "📏",
        title = "Measure",
        description = "Point-to-point 3D distance, height/width bounding, and estimated area calculation.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "object_tracking",
        icon = "🎯",
        title = "Object Tracking",
        description = "Selectable target lock with velocity extrapolation and predictive recovery.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "smart_detect",
        icon = "🔍",
        title = "Smart Detect",
        description = "Multi-category visual categorization HUD with live confidence filters.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "color_detector",
        icon = "🎨",
        title = "Color Detector",
        description = "Real-time pixel color sampling with RGB, HEX, HSV, and saved swatches palette.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "direction_compass",
        icon = "🧭",
        title = "Direction / Compass",
        description = "Sensor-fusion compass heading dial with AR horizon cardinal indicators.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "person_tracking",
        icon = "🧍",
        title = "Person Tracking",
        description = "Anonymous human detection and pedestrian session flow telemetry.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "animal_detection",
        icon = "🐕",
        title = "Animal Detection",
        description = "Specialized classification for pets and wildlife (dogs, cats, birds).",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "perf_monitor",
        icon = "⚡",
        title = "Performance Monitor",
        description = "Real-time camera FPS, inference latency, memory heap, and pipeline telemetry.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "settings",
        icon = "⚙️",
        title = "Settings",
        description = "Studio hardware capability diagnostics, confidence defaults, and privacy.",
        isEnabled = true,
        badge = "ACTIVE"
    ),
    VisionFeature(
        id = "object_voice",
        icon = "🔊",
        title = "Object Voice",
        description = "Low-latency spatial audio narration for detected visual objects.",
        isEnabled = false,
        badge = "COMING SOON"
    ),
    VisionFeature(
        id = "object_trail",
        icon = "🗺️",
        title = "Object Trail",
        description = "Historical trajectory path plotting with heatmap motion trails.",
        isEnabled = false,
        badge = "COMING SOON"
    ),
    VisionFeature(
        id = "smart_capture",
        icon = "📸",
        title = "Smart Capture",
        description = "Automatic high-clarity capture triggered by target motion triggers.",
        isEnabled = false,
        badge = "COMING SOON"
    )
)

@Composable
fun HomeScreen(
    onOpenObjectCamera: () -> Unit = {},
    onOpenHandTracking: () -> Unit = {},
    onOpenHouseScan: () -> Unit = {},
    onOpenMeasure: () -> Unit = {},
    onOpenObjectTracking: () -> Unit = {},
    onOpenSmartDetect: () -> Unit = {},
    onOpenColorDetector: () -> Unit = {},
    onOpenCompass: () -> Unit = {},
    onOpenPersonTracking: () -> Unit = {},
    onOpenAnimalDetection: () -> Unit = {},
    onOpenPerformanceMonitor: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        color = IndigoDark
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Section
            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioHeader()
            }

            // Featured Engine Card 1: Object & Direction Camera (Enabled)
            item(span = { GridItemSpan(maxLineSpan) }) {
                FeaturedEngineCard(onOpenObjectCamera = onOpenObjectCamera)
            }

            // Featured Engine Card 2: Hand Tracking + Air Drawing (Enabled)
            item(span = { GridItemSpan(maxLineSpan) }) {
                FeaturedHandTrackingCard(onOpenHandTracking = onOpenHandTracking)
            }

            // Featured Engine Card 3: 3D House Scan (Enabled)
            item(span = { GridItemSpan(maxLineSpan) }) {
                FeaturedHouseScanCard(onOpenHouseScan = onOpenHouseScan)
            }

            // Section Divider / Title for Available Studio Engines
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME VISION TOOLS",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "12 Active Engines",
                        color = GreenStationary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Remaining Modules in Grid
            items(STUDIO_FEATURES.drop(3), key = { it.id }) { feature ->
                val onClick: (() -> Unit)? = when (feature.id) {
                    "measure" -> onOpenMeasure
                    "object_tracking" -> onOpenObjectTracking
                    "smart_detect" -> onOpenSmartDetect
                    "color_detector" -> onOpenColorDetector
                    "direction_compass" -> onOpenCompass
                    "person_tracking" -> onOpenPersonTracking
                    "animal_detection" -> onOpenAnimalDetection
                    "perf_monitor" -> onOpenPerformanceMonitor
                    "settings" -> onOpenSettings
                    else -> null
                }
                StudioModuleCard(feature = feature, onClick = onClick)
            }
        }
    }
}

@Composable
private fun StudioHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(CyanPrimary, CyanDark))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Studio Logo",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Advanced Vision Studio",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "On-Device Neural Computer Vision",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GreenStationary.copy(alpha = 0.15f))
                    .border(1.dp, GreenStationary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ALL ENGINES READY",
                    color = GreenStationary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun FeaturedEngineCard(
    onOpenObjectCamera: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(CyanPrimary, CyanDark, AmberAccent)),
                RoundedCornerShape(20.dp)
            )
            .clickable { onOpenObjectCamera() }
            .testTag("feature_object_direction_camera"),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CyanPrimary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🚗",
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Object & Direction Camera",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time AI Detection & Tracking",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenStationary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Detects people, vehicles, animals and objects in real time. Assigns persistent tracking IDs and calculates image-plane direction and moving/stationary states using temporal smoothing.",
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenObjectCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_camera_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAUNCH OBJECT & DIRECTION CAMERA",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedHandTrackingCard(
    onOpenHandTracking: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(AmberAccent, Color(0xFFA855F7), CyanPrimary)),
                RoundedCornerShape(20.dp)
            )
            .clickable { onOpenHandTracking() }
            .testTag("feature_hand_tracking"),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AmberAccent.copy(alpha = 0.15f))
                            .border(1.dp, AmberAccent, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✋",
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Hand Tracking + Air Drawing",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "21-Joint Pose & Mid-Air Canvas",
                            color = AmberAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenStationary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "READY",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tracks 21 3D hand joints with dual-hand support. Pinch your thumb and index finger to air draw continuous curves, erase with fingertip radius, and export drawings to your gallery.",
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenHandTracking,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_hand_tracking_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAUNCH HAND TRACKING & AIR DRAW",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedHouseScanCard(
    onOpenHouseScan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(CyanPrimary, GreenStationary, AmberAccent)),
                RoundedCornerShape(20.dp)
            )
            .clickable { onOpenHouseScan() }
            .testTag("feature_house_scan_3d"),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CyanPrimary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🏠",
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "3D House Scan",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ARCore Spatial Plane & 3D Model Reconstruction",
                            color = GreenStationary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenStationary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Real-time room scanning using AR plane tracking and depth points. Computes room dimensions (width, length, height, area), measures distances in 3D, and generates interactive 3D house meshes exportable to Wavefront OBJ.",
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenHouseScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_house_scan_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenStationary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAUNCH 3D HOUSE SCAN",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioModuleCard(
    feature: VisionFeature,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (feature.isEnabled) IndigoBorder else IndigoBorder.copy(alpha = 0.3f),
                RoundedCornerShape(14.dp)
            )
            .then(
                if (feature.isEnabled && onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .testTag("module_${feature.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (feature.isEnabled) IndigoSurface.copy(alpha = 0.85f) else IndigoSurface.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feature.icon,
                    fontSize = 26.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (feature.isEnabled) GreenStationary.copy(alpha = 0.2f) else IndigoBorder.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = feature.badge,
                        color = if (feature.isEnabled) GreenStationary else TextTertiary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = feature.title,
                color = if (feature.isEnabled) TextPrimary else TextPrimary.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = feature.description,
                color = TextTertiary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}

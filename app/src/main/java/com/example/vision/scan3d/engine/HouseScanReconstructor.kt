package com.example.vision.scan3d.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.vision.scan3d.model.DetectedPlane
import com.example.vision.scan3d.model.GeneratedHouseModel
import com.example.vision.scan3d.model.PlaneType
import com.example.vision.scan3d.model.Point3D
import com.example.vision.scan3d.model.RoomDimensions
import com.example.vision.scan3d.model.SpatialMesh
import com.example.vision.scan3d.model.SpatialPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

object HouseScanReconstructor {

    /**
     * Calculates realistic room dimensions and spatial mesh from live AR session data.
     */
    fun reconstructRoom(
        detectedPlanes: List<DetectedPlane>,
        pointCloud: List<SpatialPoint>,
        cameraPositions: List<Point3D>
    ): GeneratedHouseModel {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 1. Filter and classify planes
        val floorPlanes = detectedPlanes.filter { it.type == PlaneType.FLOOR || it.type == PlaneType.HORIZONTAL_UPWARD }
        val ceilingPlanes = detectedPlanes.filter { it.type == PlaneType.CEILING || it.type == PlaneType.HORIZONTAL_DOWNWARD }
        val wallPlanes = detectedPlanes.filter { it.type == PlaneType.WALL || it.type == PlaneType.VERTICAL }

        // 2. Determine room spatial bounds from planes and point cloud
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        // Aggregate points from planes
        var hasGeometricData = false
        for (plane in detectedPlanes) {
            for (p in plane.polygonPoints) {
                minX = min(minX, p.x)
                maxX = max(maxX, p.x)
                minY = min(minY, p.y)
                maxY = max(maxY, p.y)
                minZ = min(minZ, p.z)
                maxZ = max(maxZ, p.z)
                hasGeometricData = true
            }
        }

        // Also incorporate sampled point cloud
        val sampledPoints = if (pointCloud.size > 2000) pointCloud.filterIndexed { idx, _ -> idx % 3 == 0 } else pointCloud
        for (sp in sampledPoints) {
            minX = min(minX, sp.point.x)
            maxX = max(maxX, sp.point.x)
            minY = min(minY, sp.point.y)
            maxY = max(maxY, sp.point.y)
            minZ = min(minZ, sp.point.z)
            maxZ = max(maxZ, sp.point.z)
            hasGeometricData = true
        }

        if (!hasGeometricData) {
            minX = -1.5f; maxX = 1.5f
            minY = -1.0f; maxY = 1.4f
            minZ = -1.5f; maxZ = 1.5f
        }

        // Room dimensions estimation
        val width = max(0.5f, maxX - minX)
        val length = max(0.5f, maxZ - minZ)
        var height = max(0.8f, maxY - minY)
        if (height < 1.8f && wallPlanes.isNotEmpty()) {
            height = 2.4f // Standard ceiling baseline when only lower wall tracked
        }

        val floorArea = floorPlanes.sumOf { it.areaSquareMeters.toDouble() }.toFloat()
            .let { if (it > 0.2f) it else width * length }

        val volume = floorArea * height
        val wallArea = 2 * (width + length) * height

        val roomDimensions = RoomDimensions(
            estimatedWidthM = width,
            estimatedLengthM = length,
            estimatedHeightM = height,
            floorAreaSqM = floorArea,
            estimatedVolumeCuM = volume,
            wallSurfaceAreaSqM = wallArea,
            isEstimated = true
        )

        // 3. Construct 3D Mesh (Vertices, Normals, Faces, Colors)
        val vertices = mutableListOf<Point3D>()
        val normals = mutableListOf<Point3D>()
        val faces = mutableListOf<IntArray>()
        val colors = mutableListOf<Int>()

        // Helper to add a triangle face
        fun addTriangle(p1: Point3D, p2: Point3D, p3: Point3D, normal: Point3D, colorArgb: Int) {
            val baseIdx = vertices.size
            vertices.add(p1)
            vertices.add(p2)
            vertices.add(p3)
            normals.add(normal)
            normals.add(normal)
            normals.add(normal)
            faces.add(intArrayOf(baseIdx, baseIdx + 1, baseIdx + 2))
            colors.add(colorArgb)
        }

        // Add detected planes geometry
        for (plane in detectedPlanes) {
            val poly = plane.polygonPoints
            if (poly.size >= 3) {
                val color = when (plane.type) {
                    PlaneType.FLOOR, PlaneType.HORIZONTAL_UPWARD -> 0xCC10B981.toInt() // Emerald Green
                    PlaneType.CEILING, PlaneType.HORIZONTAL_DOWNWARD -> 0xCCF59E0B.toInt() // Amber
                    PlaneType.WALL, PlaneType.VERTICAL -> 0xCC00E5FF.toInt() // Cyan
                    else -> 0xCC8B5CF6.toInt() // Indigo/Purple
                }

                val normal = plane.normal
                val center = plane.center

                // Triangulate plane polygon using fan triangulation from center
                for (i in 0 until poly.size) {
                    val nextIdx = (i + 1) % poly.size
                    addTriangle(center, poly[i], poly[nextIdx], normal, color)
                }
            }
        }

        // If no explicit planes were detected, build bounding room box from points
        if (vertices.isEmpty()) {
            val f0 = Point3D(minX, minY, minZ)
            val f1 = Point3D(maxX, minY, minZ)
            val f2 = Point3D(maxX, minY, maxZ)
            val f3 = Point3D(minX, minY, maxZ)

            val c0 = Point3D(minX, maxY, minZ)
            val c1 = Point3D(maxX, maxY, minZ)
            val c2 = Point3D(maxX, maxY, maxZ)
            val c3 = Point3D(minX, maxY, maxZ)

            val floorColor = 0xCC10B981.toInt()
            val wallColor = 0xCC00E5FF.toInt()
            val upNormal = Point3D(0f, 1f, 0f)

            // Floor
            addTriangle(f0, f1, f2, upNormal, floorColor)
            addTriangle(f0, f2, f3, upNormal, floorColor)

            // Walls
            addTriangle(f0, c0, c1, Point3D(0f, 0f, 1f), wallColor)
            addTriangle(f0, c1, f1, Point3D(0f, 0f, 1f), wallColor)

            addTriangle(f1, c1, c2, Point3D(-1f, 0f, 0f), wallColor)
            addTriangle(f1, c2, f2, Point3D(-1f, 0f, 0f), wallColor)

            addTriangle(f2, c2, c3, Point3D(0f, 0f, -1f), wallColor)
            addTriangle(f2, c3, f3, Point3D(0f, 0f, -1f), wallColor)

            addTriangle(f3, c3, c0, Point3D(1f, 0f, 0f), wallColor)
            addTriangle(f3, c0, f0, Point3D(1f, 0f, 0f), wallColor)
        }

        val spatialMesh = SpatialMesh(
            vertices = vertices,
            normals = normals,
            faces = faces,
            colors = colors,
            boundingBoxMin = Point3D(minX, minY, minZ),
            boundingBoxMax = Point3D(maxX, maxY, maxZ)
        )

        // Calculate real scan coverage
        val coverage = calculateScanCoverage(cameraPositions, detectedPlanes, pointCloud.size)

        return GeneratedHouseModel(
            id = id,
            timestamp = timestamp,
            roomDimensions = roomDimensions,
            detectedPlanes = detectedPlanes,
            mesh = spatialMesh,
            totalVertices = vertices.size,
            totalFaces = faces.size,
            totalPlanes = detectedPlanes.size,
            scanCoveragePercent = coverage,
            pointCloudCount = pointCloud.size
        )
    }

    /**
     * Computes realistic spatial coverage percentage (0% - 100%) based on angular exploration,
     * plane detections, and point count.
     */
    fun calculateScanCoverage(
        cameraPositions: List<Point3D>,
        detectedPlanes: List<DetectedPlane>,
        pointCount: Int
    ): Int {
        if (cameraPositions.isEmpty() && detectedPlanes.isEmpty()) return 0

        // 1. Angular coverage: divide 360-degrees around center into 12 sectors of 30°
        val sectors = BooleanArray(12)
        val center = if (detectedPlanes.isNotEmpty()) {
            val avgX = detectedPlanes.map { it.center.x }.average().toFloat()
            val avgZ = detectedPlanes.map { it.center.z }.average().toFloat()
            Point3D(avgX, 0f, avgZ)
        } else {
            Point3D(0f, 0f, 0f)
        }

        for (pos in cameraPositions) {
            val dx = pos.x - center.x
            val dz = pos.z - center.z
            var angleRad = atan2(dz.toDouble(), dx.toDouble())
            if (angleRad < 0) angleRad += 2 * Math.PI
            val sector = ((angleRad / (2 * Math.PI)) * 12).toInt().coerceIn(0, 11)
            sectors[sector] = true
        }

        val angularRatio = sectors.count { it }.toFloat() / 12f // 0.0 to 1.0 (weight 50%)

        // 2. Plane coverage: floors + walls found (weight 30%)
        val planeScore = (detectedPlanes.size.toFloat() / 6f).coerceIn(0f, 1f)

        // 3. Point density: up to 1000 points (weight 20%)
        val pointScore = (pointCount.toFloat() / 1000f).coerceIn(0f, 1f)

        val totalCoverage = (angularRatio * 50f + planeScore * 30f + pointScore * 20f).toInt().coerceIn(0, 100)
        return max(if (detectedPlanes.isNotEmpty() || pointCount > 50) 8 else 0, totalCoverage)
    }

    /**
     * Exports the reconstructed 3D house model as a standard Wavefront OBJ file and saves to local storage.
     */
    suspend fun exportModelToObj(
        context: Context,
        model: GeneratedHouseModel
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "RoomScan_${dateFormat.format(Date(model.timestamp))}.obj"
            val sb = StringBuilder()

            // Header metadata
            sb.appendLine("# Advanced Vision Studio - 3D House Scan")
            sb.appendLine("# Generated: ${Date(model.timestamp)}")
            sb.appendLine("# Room Dimensions: Width=${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedWidthM)}m, Length=${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedLengthM)}m, Height=${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedHeightM)}m")
            sb.appendLine("# Floor Area: ${String.format(Locale.US, "%.2f", model.roomDimensions.floorAreaSqM)} m²")
            sb.appendLine("# Estimated Volume: ${String.format(Locale.US, "%.2f", model.roomDimensions.estimatedVolumeCuM)} m³")
            sb.appendLine("# Planes Detected: ${model.totalPlanes}, Points: ${model.pointCloudCount}")
            sb.appendLine()

            // Vertices
            for (v in model.mesh.vertices) {
                sb.appendLine("v ${String.format(Locale.US, "%.4f", v.x)} ${String.format(Locale.US, "%.4f", v.y)} ${String.format(Locale.US, "%.4f", v.z)}")
            }

            // Normals
            for (n in model.mesh.normals) {
                sb.appendLine("vn ${String.format(Locale.US, "%.4f", n.x)} ${String.format(Locale.US, "%.4f", n.y)} ${String.format(Locale.US, "%.4f", n.z)}")
            }

            // Faces (OBJ indices are 1-based)
            sb.appendLine()
            sb.appendLine("g RoomGeometry")
            for (f in model.mesh.faces) {
                val idx1 = f[0] + 1
                val idx2 = f[1] + 1
                val idx3 = f[2] + 1
                sb.appendLine("f $idx1//$idx1 $idx2//$idx2 $idx3//$idx3")
            }

            val objData = sb.toString().toByteArray(Charsets.UTF_8)

            // Save to Downloads or Pictures/AdvancedVision
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AdvancedVision")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(objData)
                    }
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AdvancedVision")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(objData) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

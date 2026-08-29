package com.example

import com.example.vision.scan3d.engine.HouseScanReconstructor
import com.example.vision.scan3d.model.DetectedPlane
import com.example.vision.scan3d.model.PlaneType
import com.example.vision.scan3d.model.Point3D
import com.example.vision.scan3d.model.SpatialPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseScanUnitTests {

    @Test
    fun `test point3D distance calculation`() {
        val p1 = Point3D(0f, 0f, 0f)
        val p2 = Point3D(3f, 4f, 0f)
        val dist = p1.distanceTo(p2)
        assertEquals(5.0f, dist, 0.001f)
    }

    @Test
    fun `test scan coverage calculation`() {
        val positions = listOf(
            Point3D(0f, 0f, 0f),
            Point3D(1f, 0f, 0f),
            Point3D(0f, 0f, 1f),
            Point3D(-1f, 0f, 0f),
            Point3D(0f, 0f, -1f)
        )
        val planes = listOf(
            DetectedPlane(
                id = "plane_floor_0",
                type = PlaneType.FLOOR,
                center = Point3D(0f, 0f, 0f),
                normal = Point3D(0f, 1f, 0f),
                polygonPoints = listOf(
                    Point3D(-2f, 0f, -2.5f),
                    Point3D(2f, 0f, -2.5f),
                    Point3D(2f, 0f, 2.5f),
                    Point3D(-2f, 0f, 2.5f)
                ),
                areaSquareMeters = 20.0f,
                extentX = 4f,
                extentZ = 5f
            )
        )

        val coverage = HouseScanReconstructor.calculateScanCoverage(
            cameraPositions = positions,
            detectedPlanes = planes,
            pointCount = 200
        )
        assertTrue(coverage > 10)
    }

    @Test
    fun `test room reconstruction generates vertices and dimensions`() {
        val planes = listOf(
            DetectedPlane(
                id = "plane_floor_0",
                type = PlaneType.FLOOR,
                center = Point3D(0f, -1.2f, 0f),
                normal = Point3D(0f, 1f, 0f),
                polygonPoints = listOf(
                    Point3D(-2.5f, -1.2f, -3.0f),
                    Point3D(2.5f, -1.2f, -3.0f),
                    Point3D(2.5f, -1.2f, 3.0f),
                    Point3D(-2.5f, -1.2f, 3.0f)
                ),
                areaSquareMeters = 30.0f,
                extentX = 5f,
                extentZ = 6f
            )
        )
        val points = listOf(
            SpatialPoint(Point3D(-2.5f, -1.2f, -3.0f)),
            SpatialPoint(Point3D(2.5f, 1.4f, 3.0f))
        )

        val model = HouseScanReconstructor.reconstructRoom(
            detectedPlanes = planes,
            pointCloud = points,
            cameraPositions = listOf(Point3D(0f, 0f, 0f))
        )

        assertTrue(model.totalVertices > 0)
        assertTrue(model.totalFaces > 0)
        assertTrue(model.roomDimensions.estimatedWidthM > 0f)
        assertTrue(model.roomDimensions.estimatedLengthM > 0f)
        assertTrue(model.roomDimensions.estimatedHeightM > 0f)
    }
}

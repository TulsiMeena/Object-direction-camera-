package com.example

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import com.example.vision.hand.drawing.AirDrawingEngine
import com.example.vision.hand.model.BrushSize
import com.example.vision.hand.model.DrawingTool
import com.example.vision.hand.model.HandLandmarkType
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.model.Handedness
import com.example.vision.hand.model.LandmarkPoint3D
import com.example.vision.hand.model.SmoothingLevel
import com.example.vision.hand.smoothing.LandmarkSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HandTrackingUnitTests {

    private fun createSample21Landmarks(
        wristX: Float = 0.5f,
        wristY: Float = 0.8f,
        indexTipX: Float = 0.5f,
        indexTipY: Float = 0.3f,
        thumbTipX: Float = 0.5f,
        thumbTipY: Float = 0.3f
    ): List<LandmarkPoint3D> {
        val list = mutableListOf<LandmarkPoint3D>()
        for (i in 0 until 21) {
            when (i) {
                HandLandmarkType.WRIST.index -> list.add(LandmarkPoint3D(wristX, wristY, 0f, wristX * 1080, wristY * 2400))
                HandLandmarkType.MIDDLE_MCP.index -> list.add(LandmarkPoint3D(0.5f, 0.5f, 0f, 540f, 1200f))
                HandLandmarkType.INDEX_TIP.index -> list.add(LandmarkPoint3D(indexTipX, indexTipY, 0f, indexTipX * 1080, indexTipY * 2400))
                HandLandmarkType.THUMB_TIP.index -> list.add(LandmarkPoint3D(thumbTipX, thumbTipY, 0f, thumbTipX * 1080, thumbTipY * 2400))
                else -> list.add(LandmarkPoint3D(0.5f, 0.5f, 0f, 540f, 1200f))
            }
        }
        return list
    }

    @Test
    fun `test landmark smoother computes pinch hysteresis`() {
        val smoother = LandmarkSmoother()

        // 1. Initial wide open fingers (Thumb far from index)
        val openLandmarks = createSample21Landmarks(thumbTipX = 0.2f, thumbTipY = 0.5f, indexTipX = 0.6f, indexTipY = 0.3f)
        val openPose = HandPose(
            handedness = Handedness.RIGHT,
            confidence = 0.95f,
            landmarks = openLandmarks
        )

        val smoothed1 = smoother.smooth(listOf(openPose), SmoothingLevel.MEDIUM)
        assertEquals(1, smoothed1.size)
        assertFalse("Wide hand should not be pinching", smoothed1[0].isPinching)

        // 2. Pinch fingers together (Thumb close to index)
        val pinchLandmarks = createSample21Landmarks(thumbTipX = 0.50f, thumbTipY = 0.30f, indexTipX = 0.51f, indexTipY = 0.31f)
        val pinchPose = HandPose(
            handedness = Handedness.RIGHT,
            confidence = 0.95f,
            landmarks = pinchLandmarks
        )

        val smoothed2 = smoother.smooth(listOf(pinchPose), SmoothingLevel.MEDIUM)
        assertEquals(1, smoothed2.size)
        assertTrue("Close thumb and index must engage pinch", smoothed2[0].isPinching)
    }

    @Test
    fun `test air drawing engine stroke generation and undo redo`() {
        val engine = AirDrawingEngine()
        assertFalse(engine.canUndo.value)
        assertFalse(engine.canRedo.value)

        // Move finger while pinching -> Creates stroke
        engine.onFingerMoved(
            point = PointF(100f, 100f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )
        engine.onFingerMoved(
            point = PointF(120f, 120f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )
        engine.onFingerMoved(
            point = PointF(140f, 140f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )

        assertNotNull(engine.activeStroke.value)
        assertTrue(engine.activeStroke.value!!.points.size >= 3)

        // Release pinch -> Finishes active stroke into strokes list
        engine.onFingerMoved(
            point = PointF(150f, 150f),
            isPinching = false,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )

        assertNull(engine.activeStroke.value)
        assertEquals(1, engine.strokes.value.size)
        assertTrue(engine.canUndo.value)
        assertFalse(engine.canRedo.value)

        // Test Undo
        engine.undo()
        assertEquals(0, engine.strokes.value.size)
        assertTrue(engine.canRedo.value)

        // Test Redo
        engine.redo()
        assertEquals(1, engine.strokes.value.size)
        assertTrue(engine.canUndo.value)

        // Test Clear
        engine.clear()
        assertEquals(0, engine.strokes.value.size)
        assertFalse(engine.canUndo.value)
        assertFalse(engine.canRedo.value)
    }

    @Test
    fun `test air drawing engine fast jump creates new stroke without jump line`() {
        val engine = AirDrawingEngine()

        engine.onFingerMoved(
            point = PointF(50f, 50f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )
        engine.onFingerMoved(
            point = PointF(60f, 60f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )

        // Sudden jump of 500px (e.g. camera glitch or teleport)
        engine.onFingerMoved(
            point = PointF(600f, 600f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.CYAN,
            brushSize = BrushSize.MEDIUM
        )

        // Old stroke should be saved and new active stroke should start fresh at 600,600
        assertEquals(1, engine.strokes.value.size)
        assertNotNull(engine.activeStroke.value)
        assertEquals(600f, engine.activeStroke.value!!.points.first().x, 0.1f)
    }

    @Test
    fun `test air drawing bitmap export`() {
        val engine = AirDrawingEngine()

        engine.onFingerMoved(
            point = PointF(20f, 20f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.RED,
            brushSize = BrushSize.SMALL
        )
        engine.onFingerMoved(
            point = PointF(80f, 80f),
            isPinching = true,
            currentTool = DrawingTool.BRUSH,
            brushColor = Color.RED,
            brushSize = BrushSize.SMALL
        )
        engine.finishActiveStroke()

        val bitmap = engine.exportBitmap(200, 200)
        assertNotNull(bitmap)
        assertEquals(200, bitmap.width)
        assertEquals(200, bitmap.height)
    }
}

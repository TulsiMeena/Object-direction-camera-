package com.example

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.vision.model.ConfidenceLevel
import com.example.vision.model.Direction
import com.example.vision.model.MovementStatus
import com.example.vision.model.RawDetection
import com.example.vision.model.TimestampedPoint
import com.example.vision.tracking.DirectionAnalyzer
import com.example.vision.tracking.ObjectTracker
import com.example.vision.util.CoordinateTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Advanced Vision Studio", appName)
    }

    @Test
    fun `test temporal direction right analysis`() {
        val analyzer = DirectionAnalyzer()
        val baseTime = 1000L
        val history = listOf(
            TimestampedPoint(PointF(100f, 200f), baseTime),
            TimestampedPoint(PointF(130f, 200f), baseTime + 100),
            TimestampedPoint(PointF(160f, 200f), baseTime + 200),
            TimestampedPoint(PointF(190f, 200f), baseTime + 300)
        )

        val result = analyzer.analyze(
            history = history,
            previousDirection = Direction.STATIONARY,
            previousStatus = MovementStatus.STATIONARY
        )

        assertEquals(Direction.RIGHT, result.direction)
        assertEquals(MovementStatus.MOVING, result.movementStatus)
        assertTrue(result.speedPxPerSec > 0)
    }

    @Test
    fun `test stationary hysteresis analysis`() {
        val analyzer = DirectionAnalyzer()
        val baseTime = 1000L
        val history = listOf(
            TimestampedPoint(PointF(100f, 200f), baseTime),
            TimestampedPoint(PointF(101f, 201f), baseTime + 100),
            TimestampedPoint(PointF(102f, 200f), baseTime + 200)
        )

        val result = analyzer.analyze(
            history = history,
            previousDirection = Direction.STATIONARY,
            previousStatus = MovementStatus.STATIONARY
        )

        assertEquals(Direction.STATIONARY, result.direction)
        assertEquals(MovementStatus.STATIONARY, result.movementStatus)
    }

    @Test
    fun `test tracker assigns formatted tracking IDs`() {
        val tracker = ObjectTracker()
        val transformer = CoordinateTransformer()
        transformer.update(
            viewWidth = 1080f,
            viewHeight = 1920f,
            imageWidth = 720,
            imageHeight = 1280,
            rotationDegrees = 90,
            isFrontCamera = false
        )

        val raw = listOf(
            RawDetection(
                internalId = 1,
                categoryName = "CAR",
                confidence = 0.92f,
                boundingBox = RectF(100f, 100f, 300f, 300f)
            )
        )

        val tracked = tracker.update(raw, transformer, now = 1000L)
        assertEquals(1, tracked.size)
        assertEquals("CAR #01", tracked[0].trackingId)
        assertEquals("CAR", tracked[0].label)
    }
}

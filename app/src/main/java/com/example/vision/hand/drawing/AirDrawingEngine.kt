package com.example.vision.hand.drawing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.vision.hand.model.BrushSize
import com.example.vision.hand.model.DrawingPoint
import com.example.vision.hand.model.DrawingStroke
import com.example.vision.hand.model.DrawingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

/**
 * High-performance state engine for Air Drawing.
 * Manages active strokes, continuous Bezier smoothing, undo/redo stacks, eraser, and bitmap export.
 */
class AirDrawingEngine {

    private val _strokes = MutableStateFlow<List<DrawingStroke>>(emptyList())
    val strokes: StateFlow<List<DrawingStroke>> = _strokes.asStateFlow()

    private val _activeStroke = MutableStateFlow<DrawingStroke?>(null)
    val activeStroke: StateFlow<DrawingStroke?> = _activeStroke.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isDrawingActive = MutableStateFlow(false)
    val isDrawingActive: StateFlow<Boolean> = _isDrawingActive.asStateFlow()

    private val redoStack = mutableListOf<DrawingStroke>()

    // Maximum distance gap in pixels before considering movement a discontinuous jump
    private val maxPointGapPx = 90f
    // Minimum distance in pixels to register a new point (avoids redundant overlapping points)
    private val minPointDistancePx = 4f

    /**
     * Updates pointer position and stroke generation.
     * @param point Current screen coordinate of the drawing fingertip.
     * @param isPinching True if thumb and index finger are pinching together.
     * @param currentTool BRUSH or ERASER.
     * @param brushColor Current ARGB color.
     * @param brushSize Selected brush size.
     */
    fun onFingerMoved(
        point: PointF?,
        isPinching: Boolean,
        currentTool: DrawingTool,
        brushColor: Int,
        brushSize: BrushSize
    ) {
        if (point == null) {
            // Hand lost -> safely finalize current stroke
            finishActiveStroke()
            _isDrawingActive.value = false
            return
        }

        if (!isPinching) {
            // Pinch released -> finalize active stroke
            finishActiveStroke()
            _isDrawingActive.value = false
            return
        }

        // Pinch is ACTIVE!
        _isDrawingActive.value = true

        if (currentTool == DrawingTool.ERASER) {
            eraseNear(point.x, point.y, brushSize.strokeWidthPx * 2.5f + 25f)
            return
        }

        val current = _activeStroke.value
        val newPoint = DrawingPoint(x = point.x, y = point.y)

        if (current == null) {
            // Start a brand new stroke
            _activeStroke.value = DrawingStroke(
                id = System.currentTimeMillis(),
                points = listOf(newPoint),
                color = brushColor,
                strokeWidth = brushSize.strokeWidthPx,
                isEraser = false
            )
            // Clear redo stack on new user action
            redoStack.clear()
            _canRedo.value = false
        } else {
            val lastPoint = current.points.lastOrNull()
            if (lastPoint != null) {
                val dist = hypot(newPoint.x - lastPoint.x, newPoint.y - lastPoint.y)

                if (dist > maxPointGapPx) {
                    // Hand moved too fast / jumped -> end old stroke and start fresh to avoid jump lines
                    finishActiveStroke()
                    _activeStroke.value = DrawingStroke(
                        id = System.currentTimeMillis(),
                        points = listOf(newPoint),
                        color = brushColor,
                        strokeWidth = brushSize.strokeWidthPx,
                        isEraser = false
                    )
                } else if (dist >= minPointDistancePx) {
                    // Add point to stroke
                    val updatedPoints = current.points + newPoint
                    _activeStroke.value = current.copy(points = updatedPoints)
                }
            }
        }
    }

    fun finishActiveStroke() {
        val current = _activeStroke.value ?: return
        if (current.points.size >= 2) {
            val updated = _strokes.value + current
            _strokes.value = updated
            _canUndo.value = true
        }
        _activeStroke.value = null
    }

    /**
     * Erases strokes intersecting or near the specified coordinate.
     */
    private fun eraseNear(x: Float, y: Float, radiusPx: Float) {
        val currentStrokes = _strokes.value
        if (currentStrokes.isEmpty()) return

        val radiusSq = radiusPx * radiusPx
        val filtered = currentStrokes.filterNot { stroke ->
            stroke.points.any { pt ->
                val dx = pt.x - x
                val dy = pt.y - y
                (dx * dx + dy * dy) <= radiusSq
            }
        }

        if (filtered.size != currentStrokes.size) {
            _strokes.value = filtered
            _canUndo.value = filtered.isNotEmpty()
            redoStack.clear()
            _canRedo.value = false
        }
    }

    fun undo() {
        val current = _strokes.value
        if (current.isNotEmpty()) {
            val last = current.last()
            val remaining = current.dropLast(1)
            _strokes.value = remaining
            redoStack.add(last)
            _canUndo.value = remaining.isNotEmpty()
            _canRedo.value = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val strokeToRestore = redoStack.removeAt(redoStack.size - 1)
            val updated = _strokes.value + strokeToRestore
            _strokes.value = updated
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    fun clear() {
        finishActiveStroke()
        _strokes.value = emptyList()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _isDrawingActive.value = false
    }

    /**
     * Renders all strokes onto an Android Bitmap.
     */
    fun exportBitmap(
        width: Int,
        height: Int,
        backgroundColor: Int = android.graphics.Color.TRANSPARENT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(100), height.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val allStrokes = _strokes.value + listOfNotNull(_activeStroke.value)

        for (stroke in allStrokes) {
            if (stroke.points.size < 2) continue

            paint.color = stroke.color
            paint.strokeWidth = stroke.strokeWidth

            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x, first.y)

            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                path.quadTo(prev.x, prev.y, midX, midY)
            }
            val last = stroke.points.last()
            path.lineTo(last.x, last.y)

            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    /**
     * Saves air drawing to device MediaStore / Pictures.
     */
    suspend fun saveToGallery(
        context: Context,
        width: Int,
        height: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val bitmap = exportBitmap(width, height, backgroundColor = android.graphics.Color.BLACK)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "AirDraw_$timeStamp.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AdvancedVision")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                return@withContext uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return@withContext null
            }
        }
        null
    }
}

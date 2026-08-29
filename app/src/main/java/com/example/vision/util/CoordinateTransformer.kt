package com.example.vision.util

import android.graphics.Matrix
import android.graphics.RectF

/**
 * Transforms bounding boxes and points from CameraX image sensor space
 * to the preview UI screen pixel coordinates.
 */
class CoordinateTransformer {

    private val matrix = Matrix()
    private var lastViewWidth = 0f
    private var lastViewHeight = 0f
    private var lastImageWidth = 0
    private var lastImageHeight = 0
    private var lastRotationDegrees = 0
    private var lastIsFrontCamera = false

    /**
     * Updates transformation matrix for the given camera frame and preview view dimensions.
     */
    fun update(
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        isFrontCamera: Boolean
    ) {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return

        if (viewWidth == lastViewWidth &&
            viewHeight == lastViewHeight &&
            imageWidth == lastImageWidth &&
            imageHeight == lastImageHeight &&
            rotationDegrees == lastRotationDegrees &&
            isFrontCamera == lastIsFrontCamera
        ) {
            return
        }

        lastViewWidth = viewWidth
        lastViewHeight = viewHeight
        lastImageWidth = imageWidth
        lastImageHeight = imageHeight
        lastRotationDegrees = rotationDegrees
        lastIsFrontCamera = isFrontCamera

        matrix.reset()

        // 1. Invert front camera horizontally if needed
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, imageWidth / 2f, imageHeight / 2f)
        }

        // 2. Rotate around image center
        matrix.postRotate(rotationDegrees.toFloat(), imageWidth / 2f, imageHeight / 2f)

        // 3. Compute rotated image dimensions
        val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
        val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth else imageHeight

        // 4. Translate so (0,0) is at top-left after rotation
        val postRotateBounds = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
        matrix.mapRect(postRotateBounds)
        matrix.postTranslate(-postRotateBounds.left, -postRotateBounds.top)

        // 5. Scale to fill screen preview with aspect ratio preservation (FILL_CENTER / CROP)
        val scaleX = viewWidth / rotatedWidth.toFloat()
        val scaleY = viewHeight / rotatedHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        matrix.postScale(scale, scale)

        // 6. Center the crop in view
        val scaledWidth = rotatedWidth * scale
        val scaledHeight = rotatedHeight * scale
        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f

        matrix.postTranslate(offsetX, offsetY)
    }

    /**
     * Maps a bounding box in sensor coordinates to screen pixel coordinates.
     */
    fun transformRect(sensorRect: RectF): RectF {
        val dest = RectF()
        matrix.mapRect(dest, sensorRect)
        return dest
    }

    /**
     * Maps a point from sensor coordinates to screen pixel coordinates.
     */
    fun transformPoint(x: Float, y: Float): FloatArray {
        val points = floatArrayOf(x, y)
        matrix.mapPoints(points)
        return points
    }
}

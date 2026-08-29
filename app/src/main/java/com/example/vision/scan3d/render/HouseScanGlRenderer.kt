package com.example.vision.scan3d.render

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.example.vision.scan3d.model.DetectedPlane
import com.example.vision.scan3d.model.PlaneType
import com.example.vision.scan3d.model.Point3D
import com.example.vision.scan3d.model.PointMeasurement
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class HouseScanGlRenderer {

    var cameraTextureId: Int = -1
        private set

    private var cameraProgram: Int = 0
    private var colorProgram: Int = 0

    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    // Quad geometry for camera background
    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f))
            position(0)
        }

    private val quadTexCoords: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f))
            position(0)
        }

    private val transformedTexCoords: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun initializeGl(context: Context) {
        // Create external texture for AR camera
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        // Compile camera background shader
        val cameraVs = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
               gl_Position = a_Position;
               v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val cameraFs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES s_Texture;
            void main() {
                gl_FragColor = texture2D(s_Texture, v_TexCoord);
            }
        """.trimIndent()

        cameraProgram = createProgram(cameraVs, cameraFs)

        // Compile standard 3D color shader
        val colorVs = """
            uniform mat4 u_ModelViewProjection;
            attribute vec4 a_Position;
            void main() {
               gl_Position = u_ModelViewProjection * a_Position;
               gl_PointSize = 12.0;
            }
        """.trimIndent()

        val colorFs = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """.trimIndent()

        colorProgram = createProgram(colorVs, colorFs)
    }

    fun updateViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    fun render(
        frame: Frame,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        planes: List<DetectedPlane>,
        selectedPlane: DetectedPlane?,
        measurement: PointMeasurement?,
        pendingPointA: Point3D?
    ) {
        // 1. Render camera background
        renderCameraBackground(frame)

        // 2. Render 3D overlays (Planes, Measurements, Highlights)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        GLES20.glUseProgram(colorProgram)
        val mvpHandle = GLES20.glGetUniformLocation(colorProgram, "u_ModelViewProjection")
        val colorHandle = GLES20.glGetUniformLocation(colorProgram, "u_Color")
        val posHandle = GLES20.glGetAttribLocation(colorProgram, "a_Position")
        GLES20.glEnableVertexAttribArray(posHandle)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // Render detected planes
        for (plane in planes) {
            val isSelected = plane.id == selectedPlane?.id
            val poly = plane.polygonPoints
            if (poly.size < 3) continue

            // Build buffer
            val vertexCount = poly.size + 1 // center + polygon
            val buffer = ByteBuffer.allocateDirect(vertexCount * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()

            buffer.put(plane.center.x)
            buffer.put(plane.center.y)
            buffer.put(plane.center.z)

            for (pt in poly) {
                buffer.put(pt.x)
                buffer.put(pt.y)
                buffer.put(pt.z)
            }
            buffer.position(0)

            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)

            // Fill color
            val (r, g, b) = when (plane.type) {
                PlaneType.FLOOR -> Triple(0.06f, 0.72f, 0.51f) // Emerald Green
                PlaneType.CEILING -> Triple(0.96f, 0.62f, 0.04f) // Amber
                PlaneType.WALL -> Triple(0.0f, 0.89f, 1.0f) // Cyan
                else -> Triple(0.54f, 0.36f, 0.96f)
            }

            val fillAlpha = if (isSelected) 0.45f else 0.22f
            GLES20.glUniform4f(colorHandle, r, g, b, fillAlpha)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount)

            // Outline wireframe
            val outlineBuffer = ByteBuffer.allocateDirect(poly.size * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (pt in poly) {
                outlineBuffer.put(pt.x)
                outlineBuffer.put(pt.y)
                outlineBuffer.put(pt.z)
            }
            outlineBuffer.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, outlineBuffer)

            val lineAlpha = if (isSelected) 1.0f else 0.85f
            val lineR = if (isSelected) 1.0f else r
            val lineG = if (isSelected) 1.0f else g
            val lineB = if (isSelected) 0.2f else b

            GLES20.glLineWidth(if (isSelected) 6.0f else 3.0f)
            GLES20.glUniform4f(colorHandle, lineR, lineG, lineB, lineAlpha)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, poly.size)
        }

        // Render pending measurement Point A
        pendingPointA?.let { pA ->
            val pointBuffer = ByteBuffer.allocateDirect(3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(floatArrayOf(pA.x, pA.y, pA.z))
                    position(0)
                }
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, pointBuffer)
            GLES20.glUniform4f(colorHandle, 1.0f, 0.84f, 0.0f, 1.0f) // Yellow dot
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        }

        // Render completed measurement line (Point A -> Point B)
        measurement?.let { m ->
            val lineBuffer = ByteBuffer.allocateDirect(2 * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(m.pointA.x, m.pointA.y, m.pointA.z, m.pointB.x, m.pointB.y, m.pointB.z))
                position(0)
            }
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, lineBuffer)

            // Draw line
            GLES20.glLineWidth(5.0f)
            GLES20.glUniform4f(colorHandle, 0.0f, 1.0f, 0.8f, 1.0f) // Bright Cyan Line
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)

            // Draw endpoint markers
            GLES20.glUniform4f(colorHandle, 1.0f, 0.2f, 0.4f, 1.0f) // Pink Endpoints
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 2)
        }

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun renderCameraBackground(frame: Frame) {
        if (cameraTextureId == -1) return

        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadTexCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                transformedTexCoords
            )
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(cameraProgram)
        val posHandle = GLES20.glGetAttribLocation(cameraProgram, "a_Position")
        val texHandle = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord")
        val samplerHandle = GLES20.glGetUniformLocation(cameraProgram, "s_Texture")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, transformedTexCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
        GLES20.glDepthMask(true)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
}

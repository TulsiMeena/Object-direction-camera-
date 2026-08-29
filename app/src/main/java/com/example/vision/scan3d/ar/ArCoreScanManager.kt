package com.example.vision.scan3d.ar

import android.content.Context
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.example.vision.scan3d.engine.HouseScanReconstructor
import com.example.vision.scan3d.model.DetectedPlane
import com.example.vision.scan3d.model.GeneratedHouseModel
import com.example.vision.scan3d.model.PlaneType
import com.example.vision.scan3d.model.Point3D
import com.example.vision.scan3d.model.PointMeasurement
import com.example.vision.scan3d.model.RoomDimensions
import com.example.vision.scan3d.model.ScanPerformanceMetrics
import com.example.vision.scan3d.model.ScanStatus
import com.example.vision.scan3d.model.SpatialPoint
import com.example.vision.scan3d.model.TrackingStatus
import com.example.vision.scan3d.render.HouseScanGlRenderer
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.PointCloud
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ArCoreScanManager(
    private val context: Context,
    private val scope: CoroutineScope
) : GLSurfaceView.Renderer {

    private var session: Session? = null
    private val isSessionConfigured = AtomicBoolean(false)
    private val glRenderer = HouseScanGlRenderer()

    // State flows
    private val _scanStatus = MutableStateFlow(ScanStatus.IDLE)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _trackingStatus = MutableStateFlow(TrackingStatus.INITIALIZING)
    val trackingStatus: StateFlow<TrackingStatus> = _trackingStatus.asStateFlow()

    private val _trackingMessage = MutableStateFlow("Initializing AR Tracking...")
    val trackingMessage: StateFlow<String> = _trackingMessage.asStateFlow()

    private val _detectedPlanes = MutableStateFlow<List<DetectedPlane>>(emptyList())
    val detectedPlanes: StateFlow<List<DetectedPlane>> = _detectedPlanes.asStateFlow()

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()

    private val _scanCoverage = MutableStateFlow(0)
    val scanCoverage: StateFlow<Int> = _scanCoverage.asStateFlow()

    private val _selectedPlane = MutableStateFlow<DetectedPlane?>(null)
    val selectedPlane: StateFlow<DetectedPlane?> = _selectedPlane.asStateFlow()

    private val _currentMeasurement = MutableStateFlow<PointMeasurement?>(null)
    val currentMeasurement: StateFlow<PointMeasurement?> = _currentMeasurement.asStateFlow()

    private val _pendingMeasurementPointA = MutableStateFlow<Point3D?>(null)
    val pendingMeasurementPointA: StateFlow<Point3D?> = _pendingMeasurementPointA.asStateFlow()

    private val _roomDimensions = MutableStateFlow<RoomDimensions?>(null)
    val roomDimensions: StateFlow<RoomDimensions?> = _roomDimensions.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(ScanPerformanceMetrics())
    val performanceMetrics: StateFlow<ScanPerformanceMetrics> = _performanceMetrics.asStateFlow()

    private val _generatedModel = MutableStateFlow<GeneratedHouseModel?>(null)
    val generatedModel: StateFlow<GeneratedHouseModel?> = _generatedModel.asStateFlow()

    // Internal geometry accumulators
    private val accumulatedPoints = mutableListOf<SpatialPoint>()
    private val cameraTrajectory = mutableListOf<Point3D>()
    private val MAX_ACCUMULATED_POINTS = 15000

    // FPS & Timing calculation
    private var frameCount = 0
    private var lastFpsCalculationTime = SystemClock.uptimeMillis()
    private var currentFps = 30
    private var lastFrameTimestamp = 0L
    private var droppedFramesCount = 0

    // Tap handling queue
    private var pendingTapX: Float? = null
    private var pendingTapY: Float? = null
    private val tapLock = Any()

    private var reconstructionJob: Job? = null

    fun initializeSession(): Boolean {
        try {
            if (session != null) return true

            val newSession = Session(context)
            val config = Config(newSession).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }
            }
            newSession.configure(config)
            session = newSession
            isSessionConfigured.set(true)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            _trackingMessage.value = "Failed to start ARCore: ${e.message}"
            return false
        }
    }

    fun startScan() {
        if (_scanStatus.value == ScanStatus.IDLE || _scanStatus.value == ScanStatus.PAUSED) {
            _scanStatus.value = ScanStatus.SCANNING
            _trackingMessage.value = "Move your phone slowly around the room."
        }
    }

    fun pauseScan() {
        if (_scanStatus.value == ScanStatus.SCANNING) {
            _scanStatus.value = ScanStatus.PAUSED
            _trackingMessage.value = "Scan paused. Tap Resume to continue."
        }
    }

    fun resumeScan() {
        if (_scanStatus.value == ScanStatus.PAUSED) {
            _scanStatus.value = ScanStatus.SCANNING
            _trackingMessage.value = "Scanning active. Move phone slowly."
        }
    }

    fun clearScan() {
        synchronized(accumulatedPoints) {
            accumulatedPoints.clear()
            cameraTrajectory.clear()
        }
        _detectedPlanes.value = emptyList()
        _pointCount.value = 0
        _scanCoverage.value = 0
        _selectedPlane.value = null
        _currentMeasurement.value = null
        _pendingMeasurementPointA.value = null
        _roomDimensions.value = null
        _generatedModel.value = null
        _scanStatus.value = ScanStatus.IDLE
        _trackingMessage.value = "Scan cleared. Tap Start Scan when ready."
    }

    fun finishScanAndGenerateModel() {
        _scanStatus.value = ScanStatus.FINISHED
        reconstructionJob?.cancel()
        reconstructionJob = scope.launch(Dispatchers.Default) {
            val startTime = SystemClock.uptimeMillis()
            val planes = _detectedPlanes.value
            val points = synchronized(accumulatedPoints) { accumulatedPoints.toList() }
            val traj = synchronized(cameraTrajectory) { cameraTrajectory.toList() }

            val model = HouseScanReconstructor.reconstructRoom(planes, points, traj)
            val duration = SystemClock.uptimeMillis() - startTime

            _generatedModel.value = model
            _roomDimensions.value = model.roomDimensions
            _scanCoverage.value = model.scanCoveragePercent

            val current = _performanceMetrics.value
            _performanceMetrics.value = current.copy(meshProcessingMs = duration)
        }
    }

    fun handleScreenTap(x: Float, y: Float) {
        synchronized(tapLock) {
            pendingTapX = x
            pendingTapY = y
        }
    }

    fun clearMeasurement() {
        _currentMeasurement.value = null
        _pendingMeasurementPointA.value = null
    }

    fun selectPlane(plane: DetectedPlane?) {
        _selectedPlane.value = plane
    }

    fun resumeSession() {
        try {
            session?.resume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseSession() {
        try {
            session?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        try {
            session?.pause()
            session?.close()
            session = null
            isSessionConfigured.set(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // GLSurfaceView.Renderer implementation
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.04f, 0.08f, 1.0f)
        glRenderer.initializeGl(context)
        session?.setCameraTextureName(glRenderer.cameraTextureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(0, width, height)
        glRenderer.updateViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val currentSession = session ?: return

        try {
            val frame = currentSession.update()
            val camera = frame.camera

            // Measure FPS & Frame timing
            frameCount++
            val now = SystemClock.uptimeMillis()
            if (now - lastFpsCalculationTime >= 1000) {
                currentFps = (frameCount * 1000 / (now - lastFpsCalculationTime)).toInt()
                frameCount = 0
                lastFpsCalculationTime = now
            }

            // Tracking state assessment
            val trackingState = camera.trackingState
            when (trackingState) {
                TrackingState.TRACKING -> {
                    _trackingStatus.value = TrackingStatus.ACTIVE
                    if (_scanStatus.value == ScanStatus.SCANNING) {
                        _trackingMessage.value = "Move your phone slowly around the room."
                    }
                }
                TrackingState.PAUSED -> {
                    val failure = camera.trackingFailureReason
                    _trackingStatus.value = TrackingStatus.POOR
                    _trackingMessage.value = when (failure) {
                        TrackingFailureReason.EXCESSIVE_MOTION -> "Tracking Lost — Move Slowly"
                        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Low Visual Features — Point at textured surfaces"
                        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Low Lighting — Move to a brighter area"
                        TrackingFailureReason.BAD_STATE -> "Tracking Initializing..."
                        else -> "Tracking Paused"
                    }
                }
                TrackingState.STOPPED -> {
                    _trackingStatus.value = TrackingStatus.LOST
                    _trackingMessage.value = "Tracking Lost"
                }
            }

            // Only integrate geometry if tracking is valid and status is SCANNING
            if (trackingState == TrackingState.TRACKING && _scanStatus.value == ScanStatus.SCANNING) {
                processActiveFrame(frame, currentSession)
            }

            // Process pending hit test / tap
            processPendingTap(frame)

            // Render camera stream and AR overlays
            val projMatrix = FloatArray(16)
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)
            val viewMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)

            glRenderer.render(
                frame = frame,
                viewMatrix = viewMatrix,
                projMatrix = projMatrix,
                planes = _detectedPlanes.value,
                selectedPlane = _selectedPlane.value,
                measurement = _currentMeasurement.value,
                pendingPointA = _pendingMeasurementPointA.value
            )

            // Update telemetry
            val runtime = Runtime.getRuntime()
            val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            _performanceMetrics.value = _performanceMetrics.value.copy(
                cameraFps = currentFps,
                arTrackingFps = if (trackingState == TrackingState.TRACKING) currentFps else 0,
                renderFps = currentFps,
                memoryUsageMb = usedMem
            )

        } catch (e: CameraNotAvailableException) {
            _trackingMessage.value = "Camera unavailable: ${e.message}"
        } catch (e: SessionPausedException) {
            // Normal during pause
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processActiveFrame(frame: Frame, session: Session) {
        val cameraPose = frame.camera.pose
        val camPos = Point3D(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())
        synchronized(cameraTrajectory) {
            if (cameraTrajectory.isEmpty() || cameraTrajectory.last().distanceTo(camPos) > 0.05f) {
                cameraTrajectory.add(camPos)
            }
        }

        // 1. Process detected planes
        val allPlanes = session.getAllTrackables(Plane::class.java)
        val planeList = mutableListOf<DetectedPlane>()

        for (p in allPlanes) {
            if (p.subsumedBy != null || p.trackingState != TrackingState.TRACKING) continue

            val type = when (p.type) {
                Plane.Type.HORIZONTAL_UPWARD_FACING -> PlaneType.FLOOR
                Plane.Type.HORIZONTAL_DOWNWARD_FACING -> PlaneType.CEILING
                Plane.Type.VERTICAL -> PlaneType.WALL
                else -> PlaneType.UNKNOWN
            }

            val centerPose = p.centerPose
            val center = Point3D(centerPose.tx(), centerPose.ty(), centerPose.tz())
            val polygon = extractPlanePolygonWorldCoordinates(p)
            val area = calculatePolygonArea(polygon)

            // Extract normal vector from plane pose
            val normalArray = FloatArray(3)
            centerPose.getTransformedAxis(1, 1.0f, normalArray, 0)
            val normal = Point3D(normalArray[0], normalArray[1], normalArray[2])

            val isSelected = _selectedPlane.value?.id == p.hashCode().toString()

            planeList.add(
                DetectedPlane(
                    id = p.hashCode().toString(),
                    type = type,
                    center = center,
                    normal = normal,
                    polygonPoints = polygon,
                    areaSquareMeters = area,
                    extentX = p.extentX,
                    extentZ = p.extentZ,
                    isSelected = isSelected
                )
            )
        }

        _detectedPlanes.value = planeList

        // 2. Extract and downsample point cloud
        try {
            val pointCloud: PointCloud = frame.acquirePointCloud()
            val pointsBuffer: FloatBuffer = pointCloud.points
            val count = pointCloud.points.remaining() / 4

            synchronized(accumulatedPoints) {
                // Downsample to avoid excessive memory & keep UI ultra-responsive
                val step = if (count > 500) 4 else 2
                for (i in 0 until count step step) {
                    val x = pointsBuffer.get(i * 4)
                    val y = pointsBuffer.get(i * 4 + 1)
                    val z = pointsBuffer.get(i * 4 + 2)
                    val conf = pointsBuffer.get(i * 4 + 3)

                    if (conf > 0.3f) {
                        val pt = SpatialPoint(Point3D(x, y, z), conf)
                        if (accumulatedPoints.size >= MAX_ACCUMULATED_POINTS) {
                            accumulatedPoints.removeAt(0)
                        }
                        accumulatedPoints.add(pt)
                    }
                }
            }
            pointCloud.release()
        } catch (e: Exception) {
            // Point cloud read error ignore
        }

        val totalPoints = synchronized(accumulatedPoints) { accumulatedPoints.size }
        _pointCount.value = totalPoints

        // Calculate real scan coverage
        val coverage = HouseScanReconstructor.calculateScanCoverage(
            cameraPositions = synchronized(cameraTrajectory) { cameraTrajectory.toList() },
            detectedPlanes = planeList,
            pointCount = totalPoints
        )
        _scanCoverage.value = coverage

        // Update estimated room dimensions in real-time
        if (planeList.isNotEmpty() && reconstructionJob?.isActive != true) {
            val tempModel = HouseScanReconstructor.reconstructRoom(
                planeList,
                synchronized(accumulatedPoints) { accumulatedPoints.take(500) },
                synchronized(cameraTrajectory) { cameraTrajectory.takeLast(10) }
            )
            _roomDimensions.value = tempModel.roomDimensions
        }
    }

    private fun processPendingTap(frame: Frame) {
        val tapX: Float
        val tapY: Float
        synchronized(tapLock) {
            tapX = pendingTapX ?: return
            tapY = pendingTapY ?: return
            pendingTapX = null
            pendingTapY = null
        }

        val hitResults: List<HitResult> = frame.hitTest(tapX, tapY)
        for (hit in hitResults) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val hitPose = hit.hitPose
                val hitPoint = Point3D(hitPose.tx(), hitPose.ty(), hitPose.tz())
                val planeId = trackable.hashCode().toString()
                val targetPlane = _detectedPlanes.value.find { it.id == planeId }

                // Check if user is measuring (Point A -> Point B)
                val currentPointA = _pendingMeasurementPointA.value
                if (currentPointA == null) {
                    _pendingMeasurementPointA.value = hitPoint
                    _selectedPlane.value = targetPlane
                } else {
                    val dist = currentPointA.distanceTo(hitPoint)
                    _currentMeasurement.value = PointMeasurement(
                        pointA = currentPointA,
                        pointB = hitPoint,
                        distanceMeters = dist,
                        isEstimated = true
                    )
                    _pendingMeasurementPointA.value = null
                }
                break
            }
        }
    }

    private fun extractPlanePolygonWorldCoordinates(plane: Plane): List<Point3D> {
        val polygonBuffer = plane.polygon ?: return emptyList()
        val numPoints = polygonBuffer.remaining() / 2
        val list = mutableListOf<Point3D>()
        val centerPose = plane.centerPose

        for (i in 0 until numPoints) {
            val localX = polygonBuffer.get(i * 2)
            val localZ = polygonBuffer.get(i * 2 + 1)
            val localPose = Pose.makeTranslation(localX, 0f, localZ)
            val worldPose = centerPose.compose(localPose)
            list.add(Point3D(worldPose.tx(), worldPose.ty(), worldPose.tz()))
        }
        return list
    }

    private fun calculatePolygonArea(polygon: List<Point3D>): Float {
        if (polygon.size < 3) return 0f
        var area = 0.0
        for (i in polygon.indices) {
            val j = (i + 1) % polygon.size
            area += (polygon[i].x * polygon[j].z - polygon[j].x * polygon[i].z).toDouble()
        }
        return abs(area.toFloat() * 0.5f)
    }
}

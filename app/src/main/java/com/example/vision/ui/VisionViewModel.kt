package com.example.vision.ui

import android.app.Application
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.vision.camera.CameraManager
import com.example.vision.camera.CameraState
import com.example.vision.camera.ObjectAndDirectionFrameProcessor
import com.example.vision.color.ColorDetectorProcessor
import com.example.vision.compass.CompassSensorManager
import com.example.vision.detector.LocalObjectDetector
import com.example.vision.hand.drawing.AirDrawingEngine
import com.example.vision.hand.model.AirDrawingSettings
import com.example.vision.hand.model.BrushSize
import com.example.vision.hand.model.DrawingStroke
import com.example.vision.hand.model.DrawingTool
import com.example.vision.hand.model.HandPose
import com.example.vision.hand.processor.HandTrackingFrameProcessor
import com.example.vision.measure.MeasureEngine
import com.example.vision.model.ColorSwatch
import com.example.vision.model.CompassHeading
import com.example.vision.model.ConfidenceLevel
import com.example.vision.model.LockedTarget
import com.example.vision.model.MeasurementMode
import com.example.vision.model.MeasurementResult
import com.example.vision.model.MeasurementUnit
import com.example.vision.model.PerformanceMetrics
import com.example.vision.model.SampledColor
import com.example.vision.model.SmartDetectCategory
import com.example.vision.model.TrackedObject
import com.example.vision.model.VisionSettings
import com.example.vision.tracking.TargetLockEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class ActiveVisionMode {
    OBJECT_AND_DIRECTION,
    OBJECT_TRACKING,
    SMART_DETECT,
    MEASURE,
    COLOR_DETECTOR,
    DIRECTION_COMPASS,
    PERSON_TRACKING,
    ANIMAL_DETECTION,
    HAND_TRACKING_AIR_DRAW,
    PERFORMANCE_MONITOR,
    SETTINGS
}

class VisionViewModel(application: Application) : AndroidViewModel(application) {

    private val objectDetector = LocalObjectDetector()
    val objectFrameProcessor = ObjectAndDirectionFrameProcessor(objectDetector)
    val handFrameProcessor = HandTrackingFrameProcessor(application.applicationContext)
    val colorFrameProcessor = ColorDetectorProcessor()

    val compassSensorManager = CompassSensorManager(application.applicationContext)
    val measureEngine = MeasureEngine()
    val targetLockEngine = TargetLockEngine()

    val cameraManager = CameraManager(application.applicationContext, objectFrameProcessor)

    private val _activeMode = MutableStateFlow(ActiveVisionMode.OBJECT_AND_DIRECTION)
    val activeMode: StateFlow<ActiveVisionMode> = _activeMode.asStateFlow()

    // Vision / Object settings
    private val _settings = MutableStateFlow(VisionSettings())
    val settings: StateFlow<VisionSettings> = _settings.asStateFlow()

    // Smart Detect category filter
    private val _smartCategoryFilter = MutableStateFlow(SmartDetectCategory.ALL)
    val smartCategoryFilter: StateFlow<SmartDetectCategory> = _smartCategoryFilter.asStateFlow()

    // Color Swatch history
    private val _colorPaletteHistory = MutableStateFlow<List<ColorSwatch>>(emptyList())
    val colorPaletteHistory: StateFlow<List<ColorSwatch>> = _colorPaletteHistory.asStateFlow()

    // Session Pedestrian Tracking Stats
    private val _totalSessionPedestrians = MutableStateFlow(0)
    val totalSessionPedestrians: StateFlow<Int> = _totalSessionPedestrians.asStateFlow()
    private val seenPersonIds = mutableSetOf<String>()

    // Object tracking flows
    val trackedObjects: StateFlow<List<TrackedObject>> = objectFrameProcessor.trackedObjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Person-only tracked objects flow
    val personTrackedObjects: StateFlow<List<TrackedObject>> = trackedObjects
        .map { list -> list.filter { it.label == "PERSON" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Animal-only tracked objects flow
    val animalTrackedObjects: StateFlow<List<TrackedObject>> = trackedObjects
        .map { list ->
            list.filter {
                val l = it.label
                l == "DOG" || l == "CAT" || l == "ANIMAL" || l == "BIRD" || l == "PET" || l == "WILDLIFE"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Smart detect filtered objects flow
    val smartDetectedObjects: StateFlow<List<TrackedObject>> = combine(
        trackedObjects,
        _smartCategoryFilter
    ) { objects, filter ->
        when (filter) {
            SmartDetectCategory.ALL -> objects
            SmartDetectCategory.VEHICLES -> objects.filter { it.label in listOf("CAR", "MOTORCYCLE", "BICYCLE", "BUS", "TRUCK") }
            SmartDetectCategory.PEOPLE -> objects.filter { it.label == "PERSON" }
            SmartDetectCategory.ANIMALS -> objects.filter { it.label in listOf("DOG", "CAT", "ANIMAL", "BIRD", "PET", "WILDLIFE") }
            SmartDetectCategory.ELECTRONICS -> objects.filter { it.label in listOf("ELECTRONIC", "COMPUTER", "PHONE") }
            SmartDetectCategory.FURNITURE -> objects.filter { it.label in listOf("FURNITURE", "CHAIR", "TABLE", "SOFA", "BED") }
            SmartDetectCategory.OBJECTS -> objects.filter { it.label !in listOf("PERSON", "CAR", "DOG", "CAT") }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Locked target for Object Tracking
    val lockedTarget: StateFlow<LockedTarget?> = targetLockEngine.lockedTarget

    // Performance metrics
    val objectPerformanceMetrics: StateFlow<PerformanceMetrics> = objectFrameProcessor.performanceMetrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceMetrics())

    val performanceMetrics: StateFlow<PerformanceMetrics> get() = objectPerformanceMetrics

    // Color Detector flows
    val sampledColor: StateFlow<SampledColor> = colorFrameProcessor.sampledColor

    // Compass flows
    val compassHeading: StateFlow<CompassHeading> = compassSensorManager.heading

    // Measure tool flows
    val measurementResult: StateFlow<MeasurementResult?> = measureEngine.measurementResult
    val measureMode: StateFlow<MeasurementMode> = measureEngine.currentMode
    val measureUnit: StateFlow<MeasurementUnit> = measureEngine.currentUnit

    // Hand tracking & Air Drawing flows
    val handPoses: StateFlow<List<HandPose>> = handFrameProcessor.handPoses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val airDrawingSettings: StateFlow<AirDrawingSettings> = handFrameProcessor.airDrawingSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AirDrawingSettings())

    val strokes: StateFlow<List<DrawingStroke>> = handFrameProcessor.drawingEngine.strokes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStroke: StateFlow<DrawingStroke?> = handFrameProcessor.drawingEngine.activeStroke
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canUndo: StateFlow<Boolean> = handFrameProcessor.drawingEngine.canUndo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canRedo: StateFlow<Boolean> = handFrameProcessor.drawingEngine.canRedo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAirDrawingPinching: StateFlow<Boolean> = handFrameProcessor.drawingEngine.isDrawingActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val handPerformanceMetrics: StateFlow<PerformanceMetrics> = handFrameProcessor.performanceMetrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceMetrics())

    val cameraState: StateFlow<CameraState> = cameraManager.cameraState

    init {
        // Collect tracked objects to update TargetLockEngine and session pedestrian counts
        viewModelScope.launch {
            trackedObjects.collect { list ->
                targetLockEngine.updateWithFrame(list)

                for (obj in list) {
                    if (obj.label == "PERSON" && seenPersonIds.add(obj.trackingId)) {
                        _totalSessionPedestrians.value = seenPersonIds.size
                    }
                }
            }
        }
    }

    fun setVisionMode(mode: ActiveVisionMode) {
        _activeMode.value = mode
        when (mode) {
            ActiveVisionMode.OBJECT_AND_DIRECTION,
            ActiveVisionMode.OBJECT_TRACKING,
            ActiveVisionMode.SMART_DETECT,
            ActiveVisionMode.PERSON_TRACKING,
            ActiveVisionMode.ANIMAL_DETECTION,
            ActiveVisionMode.MEASURE,
            ActiveVisionMode.DIRECTION_COMPASS,
            ActiveVisionMode.PERFORMANCE_MONITOR,
            ActiveVisionMode.SETTINGS -> {
                cameraManager.setFrameProcessor(objectFrameProcessor)
            }
            ActiveVisionMode.COLOR_DETECTOR -> {
                cameraManager.setFrameProcessor(colorFrameProcessor)
            }
            ActiveVisionMode.HAND_TRACKING_AIR_DRAW -> {
                cameraManager.setFrameProcessor(handFrameProcessor)
            }
        }

        if (mode == ActiveVisionMode.DIRECTION_COMPASS || mode == ActiveVisionMode.MEASURE) {
            compassSensorManager.startListening()
        } else {
            compassSensorManager.stopListening()
        }
    }

    // Object detection methods
    fun setConfidenceLevel(level: ConfidenceLevel) {
        _settings.value = _settings.value.copy(confidenceLevel = level)
        objectFrameProcessor.updateSettings(_settings.value)
    }

    fun togglePerformanceOverlay() {
        _settings.value = _settings.value.copy(showPerformanceOverlay = !_settings.value.showPerformanceOverlay)
        objectFrameProcessor.updateSettings(_settings.value)
    }

    // Smart detect filter
    fun setSmartCategoryFilter(filter: SmartDetectCategory) {
        _smartCategoryFilter.value = filter
    }

    // Object Lock methods
    fun lockOnObject(obj: TrackedObject) {
        targetLockEngine.lockOnObject(obj)
    }

    fun unlockTarget() {
        targetLockEngine.unlock()
    }

    // Color Detector methods
    fun updateColorSamplePoint(normX: Float, normY: Float) {
        colorFrameProcessor.setSamplePoint(normX, normY)
    }

    fun saveCurrentColorSwatch() {
        val current = sampledColor.value
        val newSwatch = ColorSwatch(id = UUID.randomUUID().toString(), color = current)
        _colorPaletteHistory.value = (listOf(newSwatch) + _colorPaletteHistory.value).take(20)
    }

    fun clearColorHistory() {
        _colorPaletteHistory.value = emptyList()
    }

    // Measure tool methods
    fun placeMeasurementPoint(screenX: Float, screenY: Float, viewWidth: Float, viewHeight: Float) {
        val heading = compassHeading.value
        measureEngine.placePoint(
            screenX = screenX,
            screenY = screenY,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            pitchDeg = heading.pitchDegrees,
            rollDeg = heading.rollDegrees
        )
    }

    fun resetMeasurement() {
        measureEngine.resetPoints()
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        measureEngine.setUnit(unit)
    }

    fun setMeasurementMode(mode: MeasurementMode) {
        measureEngine.setMode(mode)
    }

    fun adjustCalibrationScale(scaleDelta: Float) {
        val current = measureEngine.calibrationScale.value
        measureEngine.setCalibrationScale(current + scaleDelta)
    }

    // Hand tracking & Air drawing methods
    fun updateAirDrawingSettings(newSettings: AirDrawingSettings) {
        handFrameProcessor.updateAirDrawingSettings(newSettings)
    }

    fun setDrawingTool(tool: DrawingTool) {
        val current = airDrawingSettings.value
        updateAirDrawingSettings(current.copy(currentTool = tool))
    }

    fun setBrushColor(color: Int) {
        val current = airDrawingSettings.value
        updateAirDrawingSettings(current.copy(brushColor = color, currentTool = DrawingTool.BRUSH))
    }

    fun setBrushSize(size: BrushSize) {
        val current = airDrawingSettings.value
        updateAirDrawingSettings(current.copy(brushSize = size))
    }

    fun undoStroke() {
        handFrameProcessor.drawingEngine.undo()
    }

    fun redoStroke() {
        handFrameProcessor.drawingEngine.redo()
    }

    fun clearCanvas() {
        handFrameProcessor.drawingEngine.clear()
    }

    fun saveDrawing(width: Int, height: Int, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = handFrameProcessor.drawingEngine.saveToGallery(
                context = getApplication(),
                width = width,
                height = height
            )
            onResult(uri)
        }
    }

    // Camera control methods
    fun toggleTorch(): Boolean {
        val newState = cameraManager.toggleTorch()
        _settings.value = _settings.value.copy(isFlashlightOn = newState)
        return newState
    }

    fun toggleCameraFacing(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraManager.toggleCamera(lifecycleOwner, previewView)
        val isFront = cameraManager.isFrontFacing()
        _settings.value = _settings.value.copy(
            isFrontCamera = isFront,
            isFlashlightOn = cameraManager.isTorchEnabled()
        )
        objectFrameProcessor.updateSettings(_settings.value)
        handFrameProcessor.updateSettings(_settings.value)
        colorFrameProcessor.updateSettings(_settings.value)
    }

    fun updatePreviewDimensions(width: Float, height: Float) {
        objectFrameProcessor.updateViewDimensions(width, height)
        handFrameProcessor.updateViewDimensions(width, height)
        colorFrameProcessor.updateViewDimensions(width, height)
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.release()
        objectFrameProcessor.release()
        handFrameProcessor.release()
        colorFrameProcessor.release()
        compassSensorManager.stopListening()
    }
}

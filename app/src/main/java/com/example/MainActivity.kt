package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.vision.animal.ui.AnimalDetectionScreen
import com.example.vision.color.ui.ColorDetectorScreen
import com.example.vision.compass.ui.CompassScreen
import com.example.vision.hand.ui.HandTrackingScreen
import com.example.vision.measure.ui.MeasureScreen
import com.example.vision.person.ui.PersonTrackingScreen
import com.example.vision.scan3d.ui.HouseScanScreen
import com.example.vision.settings.ui.SettingsScreen
import com.example.vision.smartdetect.ui.SmartDetectScreen
import com.example.vision.telemetry.ui.PerformanceMonitorScreen
import com.example.vision.tracking.ui.ObjectTrackingScreen
import com.example.vision.ui.VisionViewModel
import com.example.vision.ui.camera.CameraScreen
import com.example.vision.ui.home.HomeScreen
import com.example.vision.ui.theme.AdvancedVisionTheme
import com.example.vision.ui.theme.IndigoDark

enum class VisionScreen {
    HOME,
    OBJECT_CAMERA,
    HAND_TRACKING,
    HOUSE_SCAN,
    MEASURE,
    OBJECT_TRACKING,
    SMART_DETECT,
    COLOR_DETECTOR,
    DIRECTION_COMPASS,
    PERSON_TRACKING,
    ANIMAL_DETECTION,
    PERFORMANCE_MONITOR,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val visionViewModel: VisionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdvancedVisionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = IndigoDark
                ) {
                    VisionStudioApp(viewModel = visionViewModel)
                }
            }
        }
    }
}

@Composable
fun VisionStudioApp(
    viewModel: VisionViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(VisionScreen.HOME) }

    when (currentScreen) {
        VisionScreen.HOME -> {
            HomeScreen(
                onOpenObjectCamera = { currentScreen = VisionScreen.OBJECT_CAMERA },
                onOpenHandTracking = { currentScreen = VisionScreen.HAND_TRACKING },
                onOpenHouseScan = { currentScreen = VisionScreen.HOUSE_SCAN },
                onOpenMeasure = { currentScreen = VisionScreen.MEASURE },
                onOpenObjectTracking = { currentScreen = VisionScreen.OBJECT_TRACKING },
                onOpenSmartDetect = { currentScreen = VisionScreen.SMART_DETECT },
                onOpenColorDetector = { currentScreen = VisionScreen.COLOR_DETECTOR },
                onOpenCompass = { currentScreen = VisionScreen.DIRECTION_COMPASS },
                onOpenPersonTracking = { currentScreen = VisionScreen.PERSON_TRACKING },
                onOpenAnimalDetection = { currentScreen = VisionScreen.ANIMAL_DETECTION },
                onOpenPerformanceMonitor = { currentScreen = VisionScreen.PERFORMANCE_MONITOR },
                onOpenSettings = { currentScreen = VisionScreen.SETTINGS },
                modifier = modifier
            )
        }
        VisionScreen.OBJECT_CAMERA -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            CameraScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.HAND_TRACKING -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            HandTrackingScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.HOUSE_SCAN -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            HouseScanScreen(
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.MEASURE -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            MeasureScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.OBJECT_TRACKING -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            ObjectTrackingScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.SMART_DETECT -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            SmartDetectScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.COLOR_DETECTOR -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            ColorDetectorScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.DIRECTION_COMPASS -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            CompassScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.PERSON_TRACKING -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            PersonTrackingScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.ANIMAL_DETECTION -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            AnimalDetectionScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.PERFORMANCE_MONITOR -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            PerformanceMonitorScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
        VisionScreen.SETTINGS -> {
            BackHandler {
                currentScreen = VisionScreen.HOME
            }
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = VisionScreen.HOME },
                modifier = modifier
            )
        }
    }
}

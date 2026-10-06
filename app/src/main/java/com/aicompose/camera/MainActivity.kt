package com.aicompose.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aicompose.camera.ui.CameraScreen
import com.aicompose.camera.ui.EditScreen
import com.aicompose.camera.ui.theme.AIComposeCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIComposeCameraTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    var screen by remember { mutableStateOf("camera") }
    var editPath by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            "camera" -> CameraScreen(
                onCapture = { p -> editPath = p; screen = "edit" },
                onOpenEditor = { p -> editPath = p; screen = "edit" }
            )
            else -> editPath?.let { path ->
                EditScreen(
                    initialPath = path,
                    onBack = { screen = "camera" }
                )
            }
        }
    }
}

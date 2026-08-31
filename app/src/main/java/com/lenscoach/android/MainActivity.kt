package com.lenscoach.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import com.lenscoach.android.camera.CameraScreen
import com.lenscoach.android.camera.CameraViewModel
import com.lenscoach.android.ui.LensCoachTheme

class MainActivity : ComponentActivity() {
    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            LensCoachTheme {
                Box(Modifier.fillMaxSize()) {
                    CameraScreen(cameraViewModel)
                }
            }
        }
    }
}

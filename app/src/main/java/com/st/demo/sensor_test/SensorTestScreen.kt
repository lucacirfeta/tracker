package com.st.demo.sensor_test

import android.opengl.GLSurfaceView
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.st.demo.sensor_test.model.SensorTestState
import com.st.demo.sensor_test.model.SensorTestViewModel
import com.st.demo.sensor_test.render.SensorRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorTestScreen(
    viewModel: SensorTestViewModel = hiltViewModel(),
    navController: NavController,
    deviceId: String,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Field Tracking") },
                actions = {
                    var isTracking by remember { mutableStateOf(false) }

                    IconButton(onClick = {
                        if (isTracking) {
                            viewModel.stopTracking()
                        } else {
                            viewModel.startTracking(deviceId)
                        }
                        isTracking = !isTracking
                    }) {
                        Icon(
                            imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isTracking) "Stop" else "Start"
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                        viewModel.stopTracking()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            AndroidView(
                factory = { context ->
                    try {
                        GLSurfaceView(context).apply {
                            setEGLContextClientVersion(2)
                            preserveEGLContextOnPause = true
                            // 3. Aggiungi listener per il ciclo di vita della view
                            addOnAttachStateChangeListener(object :
                                View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) {
                                    // Imposta il render mode DOPO che la view è stata attaccata
                                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                }

                                override fun onViewDetachedFromWindow(v: View) {}
                            })

                            // 4. Imposta il renderer SOLO dopo il controllo del contesto
                            val renderer = SensorRenderer(context)
                            setRenderer(renderer)
                            tag = renderer
                        }
                    } catch (e: Exception) {
                        Log.e("RENDER_SENSOR_TEST", "Errore creazione GLSurfaceView", e)
                        throw e
                    }
                },
                update = { glView ->
                    val renderer = glView.tag as? SensorRenderer
                    renderer?.let {
                        glView.queueEvent {
                            it.updateQuaternion(uiState.quaternion)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Sensor Data Display
            SensorDataDisplay(
                state = uiState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun SensorDataDisplay(
    state: SensorTestState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(Color.LightGray, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text("Sensor Data Verification", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Accelerometer (mg): X=${"%.2f".format(state.rawAccel.x)}, Y=${"%.2f".format(state.rawAccel.y)}, Z=${
                "%.2f".format(
                    state.rawAccel.z
                )
            }"
        )
        Text(
            "Gyroscope (dps): X=${"%.2f".format(state.rawGyro.x)}, Y=${"%.2f".format(state.rawGyro.y)}, Z=${
                "%.2f".format(
                    state.rawGyro.z
                )
            }"
        )
        Text(
            "Magnetometer (µT): X=${"%.2f".format(state.rawMag.x)}, Y=${"%.2f".format(state.rawMag.y)}, Z=${
                "%.2f".format(
                    state.rawMag.z
                )
            }"
        )
    }
}
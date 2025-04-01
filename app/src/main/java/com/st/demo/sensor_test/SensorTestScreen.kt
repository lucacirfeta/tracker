package com.st.demo.sensor_test

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.st.demo.sensor_test.model.SensorTestState
import com.st.demo.sensor_test.model.SensorTestViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorTestScreen(
    viewModel: SensorTestViewModel = hiltViewModel(),
    navController: NavController,
    deviceId: String,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startTracking(deviceId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTracking()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Field Tracking") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
            // Field background
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.LightGray)
            }
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
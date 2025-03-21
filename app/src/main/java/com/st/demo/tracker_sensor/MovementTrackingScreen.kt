package com.st.demo.tracker_sensor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.st.demo.tracker_sensor.display.FieldVisualization
import com.st.demo.tracker_sensor.display.InfoPanel
import com.st.demo.tracker_sensor.display.SensorDataDisplay
import com.st.demo.tracker_sensor.display.SwingMetricsUI
import com.st.demo.tracker_sensor.model.TrackingViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementTrackingScreen(
    viewModel: TrackingViewModel = hiltViewModel(),
    navController: NavController,
    deviceId: String,
) {
    val uiState by viewModel.uiState.collectAsState()
    var fieldSize by remember { mutableStateOf(10f to 10f) } // Field dimensions in meters

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
                drawRect(color = Color(0xFF4CAF50)) // Green field
            }

            // Tracking visualization
            FieldVisualization(
                position = uiState.position,
                highlights = uiState.highlights,
                fieldSize = fieldSize,
                modifier = Modifier.fillMaxSize()
            )

            // Sensor Data Display
            SensorDataDisplay(
                state = uiState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )

            SwingMetricsUI(
                metrics = uiState.swingMetrics,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )

            // Info panel
            InfoPanel(
                totalDistance = uiState.totalDistance,
                lastUpdate = uiState.lastUpdate,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
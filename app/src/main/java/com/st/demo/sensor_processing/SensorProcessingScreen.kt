package com.st.demo.sensor_processing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.st.demo.sensor_processing.component.ClimateRow
import com.st.demo.sensor_processing.component.MetricRow
import com.st.demo.sensor_processing.component.QuaternionDisplay
import com.st.demo.sensor_processing.component.VectorDisplay
import com.st.demo.sensor_processing.model.PerformanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorProcessingScreen(
    viewModel: PerformanceViewModel = hiltViewModel(),
    navController: NavController,
    deviceId: String
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Analytics") },
                actions = {
                    IconButton(onClick = { viewModel.resetSession() }) {
                        Icon(Icons.Default.Refresh, "Reset Session")
                    }
                    TrackingControls(viewModel, deviceId)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        viewModel.stopTrackingClicked(deviceId)
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Motion Section
            SensorCard(title = "Movement Analysis") {
                VectorDisplay(
                    label = "Orientation",
                    vector = state.orientation,
                    unit = "°"
                )
                MetricRow("Peak Speed", "%.1f m/s".format(state.swingSpeedPeak))
                MetricRow("Current Speed", "%.1f m/s".format(state.linearAcceleration.length()))
                QuaternionDisplay(quaternion = state.smoothedQuaternion)

            }

            // Impact Section
            SensorCard(title = "Impact Analysis") {
                state.lastImpact.let { impact ->
                    MetricRow("Max Force", "%.1f G".format(impact.force))
                    MetricRow("Shot Type", impact.type)
                    MetricRow("Impact Speed", "%.1f m/s".format(impact.racketSpeed))
                }
            }

            // Environment Section
            SensorCard(title = "Court Conditions") {
                ClimateRow(
                    temperature = state.environment.temperature,
                    humidity = state.environment.humidity
                )
                MetricRow("Air Density", "%.3f kg/m³".format(state.environment.airDensity))
                MetricRow("Altitude", "%.1f m".format(state.environment.altitude))
            }

            // Session Analytics
            SensorCard(title = "Session Summary") {
                ShotDistributionChart(distribution = state.shotDistribution)
                MetricRow("Total Shots", "${state.shotDistribution.values.sum()}")
            }
        }
    }
}

@Composable
private fun SensorCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// New improved ShotDistributionChart
@Composable
private fun ShotDistributionChart(
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val total = distribution.values.sum().toFloat()
    val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0))

    Column(modifier) {
        distribution.entries.forEachIndexed { index, (type, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type,
                    modifier = Modifier.weight(1f),
                    color = colors[index % colors.size]
                )
                LinearProgressIndicator(
                    progress = count / total,
                    color = colors[index % colors.size],
                    modifier = Modifier
                        .weight(2f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Text(
                    text = "$count",
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun TrackingControls(
    viewModel: PerformanceViewModel,
    deviceId: String
) {
    var isTracking by remember { mutableStateOf(false) }

    IconButton(onClick = {
        if (isTracking) {
            viewModel.stopTrackingClicked(deviceId)
        } else {
            viewModel.startTracking(deviceId)
        }
        isTracking = !isTracking
    }) {
        Icon(
            imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking"
        )
    }
}
package com.st.demo.tracker_sensor.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.st.demo.tracker_sensor.model.SwingMetrics

@Composable
fun SwingMetricsUI(
    metrics: SwingMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Swing Analysis", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            MetricRow(
                "Current Peak",
                if (metrics.isActive) "%.1f m/s²".format(metrics.currentPeakAccel)
                else "Idle"
            )
            MetricRow(
                "Current Duration",
                if (metrics.isActive) "${metrics.currentDurationMs} ms"
                else "Idle"
            )

            MetricRow("Last Peak", "%.1f m/s²".format(metrics.lastPeakAccel))
            MetricRow("Last Duration", "${metrics.lastDurationMs} ms")

            // Total swings (always visible)
            MetricRow("Total Swings", "${metrics.totalSwings}")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
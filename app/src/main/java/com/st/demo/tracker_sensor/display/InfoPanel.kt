package com.st.demo.tracker_sensor.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable()
internal fun InfoPanel(
    totalDistance: Float,
    lastUpdate: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Display meters
            Text(
                text = "Distance: ${"%.2f".format(totalDistance)} m",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Display kilometers
            Text(
                text = "Distance: ${"%.3f".format(totalDistance / 1000)} km",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last update: ${
                    SimpleDateFormat("mm:ss", Locale.getDefault()).format(
                        Date(
                            lastUpdate
                        )
                    )
                }",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
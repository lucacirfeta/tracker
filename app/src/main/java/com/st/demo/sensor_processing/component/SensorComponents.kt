package com.st.demo.sensor_processing.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.st.demo.tracker_sensor.utils.Vector3


@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun VectorDisplay(
    label: String,
    vector: Vector3,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "X: ${"%.1f".format(vector.x)}$unit",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Y: ${"%.1f".format(vector.y)}$unit",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Z: ${"%.1f".format(vector.z)}$unit",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ClimateRow(
    temperature: Float,
    humidity: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconLabel(
            icon = Icons.Default.Thermostat,
            value = "%.1f°C".format(temperature),
            contentDescription = "Temperature"
        )
        IconLabel(
            icon = Icons.Default.WaterDrop,
            value = "%.0f%%".format(humidity),
            contentDescription = "Humidity"
        )
    }
}

@Composable
private fun IconLabel(
    icon: ImageVector,
    value: String,
    contentDescription: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = value)
    }
}
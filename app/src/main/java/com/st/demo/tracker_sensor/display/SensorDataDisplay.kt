package com.st.demo.tracker_sensor.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.st.demo.tracker_sensor.model.TrackingState

@Composable
fun SensorDataDisplay(
    state: TrackingState,
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
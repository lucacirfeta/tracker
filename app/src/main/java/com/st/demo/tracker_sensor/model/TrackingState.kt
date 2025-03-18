package com.st.demo.tracker_sensor.model

import com.st.demo.tracker_sensor.utils.Vector2
import com.st.demo.tracker_sensor.utils.Vector3

data class TrackingState(
    val position: Vector2 = Vector2.ZERO,
    val totalDistance: Float = 0f,
    val highlights: List<Vector2> = emptyList(),
    val lastUpdate: Long = 0L,
    // Add raw sensor data fields
    val rawAccel: Vector3 = Vector3.ZERO,
    val rawGyro: Vector3 = Vector3.ZERO,
    val rawMag: Vector3 = Vector3.ZERO
)
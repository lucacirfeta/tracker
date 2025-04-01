package com.st.demo.sensor_test.model

import com.st.demo.common.model.Vector3

data class SensorTestState(
    val lastUpdate: Long = 0L,
    // Add raw sensor data fields
    val rawAccel: Vector3 = Vector3.ZERO,
    val rawGyro: Vector3 = Vector3.ZERO,
    val rawMag: Vector3 = Vector3.ZERO
)
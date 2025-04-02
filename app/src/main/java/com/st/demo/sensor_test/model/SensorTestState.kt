package com.st.demo.sensor_test.model

import com.st.demo.common.model.Vector3
import dev.romainguy.kotlin.math.Quaternion

data class SensorTestState(
    val lastUpdate: Long = 0L,
    val rawAccel: Vector3 = Vector3.ZERO,
    val rawGyro: Vector3 = Vector3.ZERO,
    val rawMag: Vector3 = Vector3.ZERO,
    val quaternion: Quaternion? = null

)
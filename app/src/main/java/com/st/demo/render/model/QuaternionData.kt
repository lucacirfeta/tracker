package com.st.demo.render.model

import com.st.blue_sdk.features.sensor_fusion.Quaternion

data class QuaternionData(
    val rawQuaternion: Quaternion = Quaternion(0L, 0f, 0f, 0f, 1f),
    val offsetQuaternion: Quaternion = Quaternion(0L, 0f, 0f, 0f, 1f)
)
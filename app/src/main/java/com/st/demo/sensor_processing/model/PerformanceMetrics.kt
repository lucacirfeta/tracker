package com.st.demo.sensor_processing.model


import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.demo.tracker_sensor.utils.Vector3

data class PerformanceMetrics(
    // Motion Analysis
    val rawQuaternion: Quaternion? = null,
    val smoothedQuaternion: Quaternion? = null,
    val rawAcceleration: Vector3 = Vector3.ZERO,
    val orientation: Vector3 = Vector3.ZERO,
    val angularVelocity: Vector3 = Vector3.ZERO,
    val linearAcceleration: Vector3 = Vector3.ZERO,
    val swingSpeedPeak: Float = 0f,
    val currentSpeedKmh: Float = 0f,

    // Environmental Context
    val environment: EnvironmentalConditions = EnvironmentalConditions(),

    // Impact Analysis
    val lastImpact: ImpactData = ImpactData(),

    // Session Analytics
    val sessionStart: Long = System.currentTimeMillis(),
    val shotDistribution: Map<String, Int> = mapOf(
        "Forehand" to 0,
        "Backhand" to 0,
        "Serve" to 0
    )
)

data class ImpactData(
    val force: Float = 0f,
    val timing: Long = 0L,
    val type: String = "Undefined",
    val racketSpeed: Float = 0f
)

data class EnvironmentalConditions(
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val pressure: Float = 0f,
    val altitude: Float = 0f,
    val airDensity: Float = 1.225f
)
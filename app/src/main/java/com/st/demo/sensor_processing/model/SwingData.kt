package com.st.demo.sensor_processing.model

data class SwingData(
    val currentSpeedKmh: Float = 0f,    // Real-time speed
    val peakSpeedKmh: Float = 0f,       // Max speed in current swing
    val peakAcceleration: Float = 0f,   // Max acceleration (m/s²)
    val swingDurationMs: Long = 0L,     // Current/last swing duration
    val totalSwings: Int = 0,           // Total completed swings
    val isSwingActive: Boolean = false  // Swing state
)
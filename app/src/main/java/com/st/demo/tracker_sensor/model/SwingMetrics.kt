package com.st.demo.tracker_sensor.model

data class SwingMetrics(
    val currentPeakAccel: Float = 0f,    // Peak during active swing
    val currentDurationMs: Long = 0L,     // Duration of active swing
    val lastPeakAccel: Float = 0f,        // Peak of last completed swing
    val lastDurationMs: Long = 0L,        // Duration of last completed swing
    val totalSwings: Int = 0,             // Total valid swings
    val isActive: Boolean = false         // Swing in progress
)
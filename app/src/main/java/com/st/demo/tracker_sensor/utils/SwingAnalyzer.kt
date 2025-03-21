package com.st.demo.tracker_sensor.utils

import com.st.demo.tracker_sensor.model.SwingMetrics

class SwingAnalyzer(
    private val startThreshold: Float = 25f,    // Minimum acceleration to trigger swing (m/s²)
    private val endThreshold: Float = 5f,       // Acceleration to consider swing ended (m/s²)
    private val minSwingDuration: Long = 500L   // Minimum valid swing duration (milliseconds)
) {
    // Internal state
    private var isSwinging = false
    private var swingStartTime = 0L
    private var currentPeak = 0f
    private var lastPeak = 0f
    private var lastDuration = 0L
    private var totalSwings = 0

    /**
     * Processes new acceleration data to detect swings
     * @param accelMag Linear acceleration magnitude (m/s²)
     * @param timestamp Current time in nanoseconds
     * @return SwingMetrics with real-time and historical data
     */
    fun detectSwing(accelMag: Float, timestamp: Long): SwingMetrics {
        return when {
            // Case 1: Swing starts
            !isSwinging && accelMag > startThreshold -> {
                isSwinging = true
                swingStartTime = timestamp
                currentPeak = accelMag
                SwingMetrics(
                    currentPeakAccel = currentPeak,
                    lastPeakAccel = lastPeak,
                    lastDurationMs = lastDuration,
                    totalSwings = totalSwings,
                    isActive = true
                )
            }

            // Case 2: Swing in progress
            isSwinging -> {
                currentPeak = maxOf(currentPeak, accelMag)
                val currentDuration = timestamp - swingStartTime

                if (accelMag < endThreshold && currentDuration >= minSwingDuration) {
                    // Valid swing completed
                    isSwinging = false
                    lastPeak = currentPeak
                    lastDuration = currentDuration
                    totalSwings++

                    SwingMetrics(
                        lastPeakAccel = lastPeak,
                        lastDurationMs = lastDuration,
                        totalSwings = totalSwings,
                        isActive = false
                    )
                } else {
                    // Swing ongoing
                    SwingMetrics(
                        currentPeakAccel = currentPeak,
                        currentDurationMs = currentDuration,
                        lastPeakAccel = lastPeak,
                        lastDurationMs = lastDuration,
                        totalSwings = totalSwings,
                        isActive = true
                    )
                }
            }

            // Case 3: No swing detected
            else -> {
                SwingMetrics(
                    lastPeakAccel = lastPeak,
                    lastDurationMs = lastDuration,
                    totalSwings = totalSwings,
                )
            }
        }
    }
}
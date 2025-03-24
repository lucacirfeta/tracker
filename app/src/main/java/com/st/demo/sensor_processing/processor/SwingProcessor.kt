package com.st.demo.sensor_processing.processor

import android.util.Log
import com.st.demo.sensor_processing.model.SwingData
import com.st.demo.tracker_sensor.utils.Vector3

class SwingProcessor(
    private val startThreshold: Float = 30f,    // Acceleration to detect swing start (m/s²)
    private val endThreshold: Float = 20f        // Acceleration to detect swing end (m/s²)
) {
    private val motionlessThreshold = 0.5f // m/s²

    @Volatile
    private var internalState = SwingState()

    private data class SwingState(
        // Motion state
        var isSwinging: Boolean = false,
        var swingStartTime: Long = 0L,
        var lastUpdateTime: Long = 0L,
        // Physics integration
        var velocity: Vector3 = Vector3.ZERO,
        var currentSpeedKmh: Float = 0f,
        var peakSpeedKmh: Float = 0f,
        // Swing metrics
        var peakAcceleration: Float = 0f,
        var totalSwings: Int = 0,
        var lastFilteredAccel: Vector3 = Vector3.ZERO
    )

    /**
     * Processes new acceleration data to:
     * - Detect swing start/end
     * - Calculate real-time speed
     * - Track peak values
     */
    fun processSwing(acceleration: Vector3, timestamp: Long): SwingData = synchronized(this) {
        val accelMag = acceleration.length()
        val deltaTime = calculateDeltaTime(timestamp)

        return when {
            // New swing detected
            !internalState.isSwinging && accelMag > startThreshold -> {
                resetSwingState(timestamp)
                integrateMotion(acceleration, deltaTime)
                createMetrics(true)
            }

            // Swing in progress
            internalState.isSwinging -> {
                integrateMotion(acceleration, deltaTime)
                internalState.peakAcceleration = maxOf(internalState.peakAcceleration, accelMag)

                if (accelMag < endThreshold) {
                    endSwing()
                    createMetrics(false)
                } else {
                    createMetrics(true)
                }
            }

            // No active swing
            else -> createMetrics(false)
        }
    }

    private fun resetSwingState(timestamp: Long) {
        internalState.isSwinging = true
        internalState.swingStartTime = timestamp
        internalState.lastUpdateTime = timestamp
        internalState.velocity = Vector3.ZERO
        internalState.currentSpeedKmh = 0f
        internalState.peakSpeedKmh = 0f
        internalState.peakAcceleration = 0f
    }

    private fun integrateMotion(acceleration: Vector3, deltaTime: Float) {
        if (acceleration.length() < motionlessThreshold) {
            internalState.velocity = Vector3.ZERO
        }
        // High-pass filter to remove drift (coefficient = 0.98)
        val alphaHP = 0.98f
        val filteredAccel = acceleration * (1 - alphaHP) + internalState.lastFilteredAccel * alphaHP
        internalState.lastFilteredAccel = filteredAccel
        // Physics integration: v = v₀ + a·Δt
        internalState.velocity += filteredAccel * deltaTime
        internalState.currentSpeedKmh = internalState.velocity.length() * 3.6f // m/s → km/h
        internalState.peakSpeedKmh =
            maxOf(internalState.peakSpeedKmh, internalState.currentSpeedKmh)
    }

    private fun endSwing() {
        internalState.isSwinging = false
        internalState.totalSwings++
    }

    private fun calculateDeltaTime(newTime: Long): Float {
        return if (internalState.lastUpdateTime == 0L) 0f
        else (newTime - internalState.lastUpdateTime).toFloat() / 1_000_000_000f
    }

    private fun createMetrics(isActive: Boolean): SwingData {
        return SwingData(
            currentSpeedKmh = internalState.currentSpeedKmh,
            peakSpeedKmh = internalState.peakSpeedKmh,
            peakAcceleration = internalState.peakAcceleration,
            swingDurationMs = if (isActive) (System.nanoTime() - internalState.swingStartTime) / 1_000_000 else 0,
            totalSwings = internalState.totalSwings,
            isSwingActive = isActive
        )
    }

    fun reset() {
        internalState.velocity = Vector3.ZERO
        internalState.currentSpeedKmh = 0f
        internalState.peakSpeedKmh = 0f
        internalState.isSwinging = false
    }
}
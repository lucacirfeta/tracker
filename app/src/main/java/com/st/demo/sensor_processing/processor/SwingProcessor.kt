package com.st.demo.sensor_processing.processor

import android.util.Log
import com.st.demo.sensor_processing.model.SwingData
import com.st.demo.common.model.Vector3

class SwingProcessor(
    private val startThreshold: Float = 30f,  // m/s²
    private val endThreshold: Float = 20f,     // m/s²
    private val angularThreshold: Float = 100f  // rad/s (adjust based on calibration)
) {
    private val motionlessThreshold = 0.5f // m/s²

    private var lastRealTime = System.nanoTime()


    @Volatile
    private var internalState = SwingState()

    private data class SwingState(
        var isSwinging: Boolean = false,
        var swingStartTime: Long = 0L,
        var lastUpdateTime: Long = 0L,
        var velocity: Vector3 = Vector3.ZERO,
        var currentSpeedKmh: Float = 0f,
        var peakSpeedKmh: Float = 0f,
        var peakAcceleration: Float = 0f,
        var totalSwings: Int = 0,
        var lastFilteredAccel: Vector3 = Vector3.ZERO,
        var currentOrientationZ: Float = 0f // Racket face angle
    )

    fun processSwing(
        acceleration: Vector3,
        orientation: Vector3,
        angularVelocity: Vector3,
        timestamp: Long
    ): SwingData = synchronized(this) {
        val accelMag = acceleration.length()
        val deltaTime = calculateDeltaTime().also {
            internalState.lastUpdateTime = timestamp
        }

        internalState.currentOrientationZ = orientation.z

        return when {
            // New swing detected
            !internalState.isSwinging && accelMag > startThreshold -> {
                resetSwingState(timestamp)
                integrateMotion(acceleration, deltaTime)
                createMetrics(isActive = true, type = "Starting")
            }

            // Swing in progress
            internalState.isSwinging -> {
                integrateMotion(acceleration, deltaTime)
                internalState.peakAcceleration = maxOf(internalState.peakAcceleration, accelMag)

                if (accelMag < endThreshold) {
                    val type = determineSwingType()
                    endSwing()
                    createMetrics(isActive = false, type = type)
                } else {
                    createMetrics(isActive = true, type = "Swinging")
                }
            }

            // No active swing
            else -> createMetrics(isActive = false, type = "Idle")
        }
    }

    private fun determineSwingType(): String {
        return when {
            internalState.currentOrientationZ > angularThreshold -> "Forehand"
            internalState.currentOrientationZ < -angularThreshold -> "Backhand"
            else -> "Neutral"
        }
    }

    private fun integrateMotion(acceleration: Vector3, deltaTime: Float) {
        val validDelta = if (deltaTime < 0.001f) 0.01f else deltaTime  // 10ms floor

        if (!internalState.isSwinging && acceleration.length() < motionlessThreshold) {
            internalState.velocity = Vector3.ZERO
        }

        val alphaHP = 0.8f
        val filteredAccel = acceleration * (1 - alphaHP) + internalState.lastFilteredAccel * alphaHP
        internalState.lastFilteredAccel = filteredAccel
        // Physics integration: v = v₀ + a·Δt
        internalState.velocity += filteredAccel * validDelta
        internalState.currentSpeedKmh = internalState.velocity.length() * 3.6f // m/s → km/h
        internalState.peakSpeedKmh =
            maxOf(internalState.peakSpeedKmh, internalState.currentSpeedKmh)
        Log.d(
            "TRACKERLOG", """
        Raw Accel: ${acceleration.length()} m/s²
        Filtered Accel: ${filteredAccel.length()} m/s²
        Delta Time: $deltaTime s
        Velocity: ${internalState.velocity.length()} m/s
    """.trimIndent()
        )
    }

    private fun calculateDeltaTime(): Float {
        val now = System.nanoTime()
        val delta = (now - lastRealTime).coerceAtLeast(0)
        lastRealTime = now
        return delta / 1_000_000_000f  // Convert ns to seconds
    }

    private fun createMetrics(isActive: Boolean, type: String): SwingData {
        return SwingData(
            currentSpeedKmh = internalState.currentSpeedKmh,
            peakSpeedKmh = internalState.peakSpeedKmh,
            peakAcceleration = internalState.peakAcceleration,
            swingDurationMs = if (isActive)
                (System.nanoTime() - internalState.swingStartTime) / 1_000_000
            else 0,
            totalSwings = internalState.totalSwings,
            isSwingActive = isActive,
            type = type
        )
    }

    private fun resetSwingState(timestamp: Long) {
        internalState.apply {
            isSwinging = true
            swingStartTime = timestamp
            lastUpdateTime = timestamp
            velocity = Vector3.ZERO
            currentSpeedKmh = 0f
            peakSpeedKmh = 0f
            peakAcceleration = 0f
            currentOrientationZ = 0f
        }
    }

    fun reset() {
        internalState = SwingState()
    }

    private fun endSwing() {
        internalState.isSwinging = false
        internalState.totalSwings++
    }
}
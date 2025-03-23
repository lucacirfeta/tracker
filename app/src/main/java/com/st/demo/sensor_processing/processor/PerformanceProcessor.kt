package com.st.demo.sensor_processing.processor

import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.demo.sensor_processing.audio.AudioAnalyzer
import com.st.demo.sensor_processing.model.EnvironmentalConditions
import com.st.demo.sensor_processing.model.ImpactData
import com.st.demo.sensor_processing.model.PerformanceMetrics
import com.st.demo.sensor_processing.utils.SensorFusionHelper
import com.st.demo.tracker_sensor.utils.Vector3
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

class PerformanceProcessor {
    private val fusionHelper = SensorFusionHelper()
    private var swingStartTime = 0L
    private var currentSwingPeak = 0f
    private var environmental = EnvironmentalConditions()
    private var currentGravity = Vector3.ZERO

    // Session Statistics
    private val shotCounts = mutableMapOf(
        "Forehand" to 0,
        "Backhand" to 0,
        "Serve" to 0
    )

    val currentOrientation: Vector3
        get() = fusionHelper.currentOrientation

    fun processFusionData(quaternion: Quaternion) {
        // Add low-pass filter for gravity vector
        val alpha = 0.8f  // Smoothing factor
        val newGravity = fusionHelper.calculateGravityVector(quaternion)
        currentGravity = currentGravity * alpha + newGravity * (1 - alpha)

        fusionHelper.updateOrientation(quaternion)
    }

    fun calculateLinearAcceleration(rawAccel: Vector3): Vector3 {
        return rawAccel - fusionHelper.getGravityVector()
    }

    fun calculateAltitude(pressure: Float): Float {
        // Simplified international barometric formula
        val seaLevelPressure = 1013.25f // hPa
        return 44330f * (1 - (pressure / seaLevelPressure).pow(0.1903f))
    }

    fun processIMU(linearAccel: Vector3, timestamp: Long) {
        val currentSpeed = linearAccel.length()

        // Detect new swing when acceleration exceeds threshold
        if (currentSpeed > 15f) {
            if (swingStartTime == 0L) { // New swing starts
                swingStartTime = timestamp
                currentSwingPeak = 0f
            }
            currentSwingPeak = max(currentSwingPeak, currentSpeed)
        } else {
            swingStartTime = 0L // Reset when motion stops
        }
    }


    fun processAudio(audioData: ShortArray, sampleRate: Int): ImpactData? {
        val (impactForce, zeroCrossingRate) = AudioAnalyzer.analyzeImpact(audioData)

        val shotType = when {
            zeroCrossingRate > 4500 -> "Serve"
            zeroCrossingRate > 3500 -> "Forehand"
            else -> "Backhand"
        }

        return if (impactForce > 2.5f) {
            shotCounts[shotType] = shotCounts[shotType]!! + 1
            ImpactData(
                force = impactForce,
                timing = System.currentTimeMillis() - swingStartTime,
                type = shotType,
                racketSpeed = currentSwingPeak
            )
        } else null
    }

    fun processEnvironment(pressure: Float, temp: Float, humidity: Float) {
        val altitude = calculateAltitude(pressure)
        val p = pressure * 100f  // Convert hPa to Pa
        val T = temp + 273.15f   // Convert to Kelvin

        environmental = environmental.copy(
            pressure = pressure,
            temperature = temp,
            humidity = humidity,
            altitude = altitude,
            airDensity = calculateAirDensity(p, T, humidity)
        )
    }

    private fun calculateAirDensity(pressurePa: Float, tempK: Float, humidity: Float): Float {
        // Using CIPM-2007 approximation
        val R = 287.05f // Specific gas constant for dry air
        val Rv = 461.495f // Specific gas constant for water vapor

        val pv = 0.611f * exp(17.27f * (tempK - 273.15f) / (tempK - 273.15f + 237.3f))
        return (pressurePa - humidity / 100f * pv) / (R * tempK) +
                (humidity / 100f * pv) / (Rv * tempK)
    }

    fun getSessionMetrics() = PerformanceMetrics(
        orientation = currentOrientation,
        swingSpeedPeak = currentSwingPeak,
        shotDistribution = shotCounts.toMap(),
        lastImpact = ImpactData(),
        environment = environmental
    )

    fun resetSession() {
        swingStartTime = 0L
        currentSwingPeak = 0f
        shotCounts.keys.forEach { shotCounts[it] = 0 }
        environmental = EnvironmentalConditions()
    }
}
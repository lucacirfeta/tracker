package com.st.demo.sensor_processing.processor

import android.util.Log
import com.st.demo.sensor_processing.audio.AudioAnalyzer
import com.st.demo.sensor_processing.model.EnvironmentalConditions
import com.st.demo.sensor_processing.model.ImpactData
import com.st.demo.sensor_processing.model.PerformanceMetrics
import com.st.demo.sensor_processing.utils.MadgwickAHRS
import com.st.demo.sensor_processing.utils.SensorFusionHelper
import com.st.demo.tracker_sensor.utils.QuaternionHelperTracker
import com.st.demo.tracker_sensor.utils.Vector3
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

class PerformanceProcessor {
    private val fusionHelper = SensorFusionHelper()
    private val swingProcessor = SwingProcessor()
    private val madgwickFilter = MadgwickAHRS(sampleRate = 50f)

    // To reset
    private var swingStartTime = 0L
    private var currentSwingPeak = 0f
    private var currentSpeedKmh = 0f
    private var filteredSpeedKmh = 0f
    private var environmental = EnvironmentalConditions()
    private var previousTime = 0L


    // Session Statistics
    private val shotCounts = mutableMapOf(
        "Forehand" to 0,
        "Backhand" to 0,
        "Serve" to 0
    )

    val currentOrientation: Vector3
        get() = fusionHelper.currentOrientation


    fun processGyro(gyro: Vector3) {
        val mag = Vector3.ZERO
        val rawAccel = Vector3.ZERO
        madgwickFilter.update(
            gyro.x, gyro.y, gyro.z,
            rawAccel.x, rawAccel.y, rawAccel.z,
            mag.x, mag.y, mag.z
        )
    }

    fun processMagn(mag: Vector3) {
        val gyro = Vector3.ZERO
        val rawAccel = Vector3.ZERO
        madgwickFilter.update(
            gyro.x, gyro.y, gyro.z,
            rawAccel.x, rawAccel.y, rawAccel.z,
            mag.x, mag.y, mag.z
        )
    }

    fun calculateLinearAcceleration(rawAccel: Vector3): Vector3 {
        val gyro = Vector3.ZERO
        val mag = Vector3.ZERO
        madgwickFilter.update(
            gyro.x, gyro.y, gyro.z,
            rawAccel.x, rawAccel.y, rawAccel.z,
            mag.x, mag.y, mag.z
        )

        val gravity = QuaternionHelperTracker.quaternionToGravity(madgwickFilter.quaternion)

        val linearAccel = rawAccel - gravity
        Log.d(
            "TRACKERLOG", "Linear acceleration: x=${String.format("%.2f", linearAccel.x)}, " +
                    "y=${String.format("%.2f", linearAccel.y)}, " +
                    "z=${String.format("%.2f", linearAccel.z)}"
        )
        return linearAccel
    }

    fun calculateAltitude(pressure: Float): Float {
        Log.d("TRACKERLOG", "Calculate altitude with pressure : $pressure")
        // Simplified international barometric formula
        val seaLevelPressure = 1013.25f // hPa
        return 44330f * (1 - (pressure / seaLevelPressure).pow(0.1903f))
    }

    // Modified processIMU function
    fun processIMU(linearAccel: Vector3, timestamp: Long) {
        if (previousTime == 0L) {  // Handle first measurement
            previousTime = timestamp
            return
        }

        // Process swing metrics using dedicated analyzer
        val swingMetrics = swingProcessor.processSwing(
            acceleration = linearAccel,
            timestamp = timestamp
        )

        // Update swing timing for audio impact correlation
        if (swingMetrics.isSwingActive) {
            if (swingStartTime == 0L) {
                swingStartTime = timestamp
            }
            // Optional: Add additional physics calculations here

            // Update speed metrics from analyzer
            currentSpeedKmh = swingMetrics.currentSpeedKmh
            filteredSpeedKmh = swingMetrics.peakSpeedKmh  // Use peak instead of filtered value
            currentSwingPeak = max(currentSwingPeak, swingMetrics.peakSpeedKmh)

            // Existing environment processing
            previousTime = timestamp
        } else {
            swingStartTime = 0L
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
        currentSpeedKmh = filteredSpeedKmh,
        shotDistribution = shotCounts.toMap(),
        lastImpact = ImpactData(),
        environment = environmental
    )

    fun resetSession() {
        swingStartTime = 0L
        currentSwingPeak = 0f
        currentSpeedKmh = 0f
        filteredSpeedKmh = 0f
        shotCounts.keys.forEach { shotCounts[it] = 0 }
        environmental = EnvironmentalConditions()
        previousTime = 0L
        swingProcessor.reset()
    }
}
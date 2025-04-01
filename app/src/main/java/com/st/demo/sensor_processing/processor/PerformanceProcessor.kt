package com.st.demo.sensor_processing.processor

import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.demo.common.model.Vector3
import com.st.demo.common.utils.QuaternionHelper
import com.st.demo.sensor_processing.model.EnvironmentalConditions
import com.st.demo.sensor_processing.model.ImpactData
import com.st.demo.sensor_processing.model.PerformanceMetrics
import com.st.demo.sensor_processing.utils.MadgwickAHRS
import com.st.demo.sensor_processing.utils.SensorFusionHelper
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

class PerformanceProcessor {
    private val fusionHelper = SensorFusionHelper()
    private val swingProcessor = SwingProcessor()
    private val madgwickFilter = MadgwickAHRS(sampleRate = 100f)

    // To reset
    private var swingStartTime = 0L
    private var currentSwingPeak = 0f
    private var currentSpeedKmh = 0f
    private var filteredSpeedKmh = 0f
    private var environmental = EnvironmentalConditions()

    private var lastRawAccel = Vector3.ZERO
    private var lastGyro = Vector3.ZERO
    private var lastMag = Vector3.ZERO

    private var lastAccelTime = 0L
    private var lastGyroTime = 0L
    private var lastMagTime = 0L

    // Session Statistics
    private val shotCounts = mutableMapOf(
        "Forehand" to 0,
        "Backhand" to 0,
        "Smash" to 0,
        "Unclassified" to 0,
        "Total" to 0
    )

    fun processAccel(accel: Vector3, timestamp: Long) {
        lastRawAccel = accel
        lastAccelTime = timestamp
        updateMadgwick()
    }

    fun processGyro(gyro: Vector3, timestamp: Long) {
        lastGyro = gyro
        lastGyroTime = timestamp
        updateMadgwick()
    }

    fun processMagn(mag: Vector3, timestamp: Long) {
        lastMag = mag
        lastMagTime = timestamp
        updateMadgwick()
    }

    private fun updateMadgwick() {
        // Only update when all sensors have recent data
        if (lastAccelTime > 0 && lastGyroTime > 0 && lastMagTime > 0) {
            madgwickFilter.update(
                gx = lastGyro.x,
                gy = lastGyro.y,
                gz = lastGyro.z,
                ax = lastRawAccel.x,
                ay = lastRawAccel.y,
                az = lastRawAccel.z,
                mx = lastMag.x,
                my = lastMag.y,
                mz = lastMag.z
            )
            // Reset timestamps after processing
            lastAccelTime = 0
            lastGyroTime = 0
            lastMagTime = 0
        }
    }

    val currentOrientation: Vector3
        get() = fusionHelper.currentOrientation

    fun calculateLinearAcceleration(rawAccel: Vector3, timeStamp: Long): Vector3 {
        // Update Madgwick first
        processAccel(rawAccel, timeStamp)
        // Get gravity from fused orientation
        val gravity = QuaternionHelper.quaternionToGravity(madgwickFilter.quaternion)
        return rawAccel - gravity
    }

    fun processFusionData(quaternion: Quaternion) {
        fusionHelper.updateOrientation(quaternion)
    }

    fun calculateAltitude(pressure: Float): Float {
        // Simplified international barometric formula
        val seaLevelPressure = 1013.25f // hPa
        return 44330f * (1 - (pressure / seaLevelPressure).pow(0.1903f))
    }

    // Modified processIMU function
    fun processIMU(linearAccel: Vector3, timestamp: Long) {
        // Process swing metrics using dedicated analyzer
        val swingMetrics = swingProcessor.processSwing(
            acceleration = linearAccel,
            orientation = fusionHelper.currentOrientation,
            angularVelocity = lastGyro,
            timestamp = timestamp
        )

        // Update swing timing for audio impact correlation
        if (swingMetrics.isSwingActive) {
            if (swingStartTime == 0L) {
                swingStartTime = timestamp
            }

            // Update speed metrics from analyzer
            currentSpeedKmh = swingMetrics.currentSpeedKmh
            filteredSpeedKmh = 0.2f * swingMetrics.currentSpeedKmh + (1 - 0.2f) * filteredSpeedKmh
            currentSwingPeak = max(currentSwingPeak, swingMetrics.peakSpeedKmh)
        } else if (swingMetrics.type != "Idle") {
            // Handle completed swings
            when (swingMetrics.type) {
                "Forehand", "Backhand", "Smash" -> {
                    shotCounts.merge(swingMetrics.type, 1) { old, new -> old + new }
                }
                "Neutral" -> shotCounts.merge("Unclassified", 1) { old, new -> old + new }
            }

            // Update total swings counter
            shotCounts["Total"] = shotCounts.getOrDefault("Total", 0) + 1
        } else {
            swingStartTime = 0L
        }
    }

    fun processEnvironment(pressure: Float, temp: Float, humidity: Float) {
        val altitude = calculateAltitude(pressure)
        val p = pressure * 100f  // Convert hPa to Pa
        val kelvinTemp = temp + 273.15f   // Convert to Kelvin

        environmental = environmental.copy(
            pressure = pressure,
            temperature = temp,
            humidity = humidity,
            altitude = altitude,
            airDensity = calculateAirDensity(p, kelvinTemp, humidity)
        )
    }

    private fun calculateAirDensity(pressurePa: Float, tempK: Float, humidity: Float): Float {
        // Using CIPM-2007 approximation
        val gasConstantDryAir = 287.05f // Specific gas constant for dry air
        val waterVaporGasConstant = 461.495f // Specific gas constant for water vapor

        val pv = 0.611f * exp(17.27f * (tempK - 273.15f) / (tempK - 273.15f + 237.3f))
        return (pressurePa - humidity / 100f * pv) / (gasConstantDryAir * tempK) +
                (humidity / 100f * pv) / (waterVaporGasConstant * tempK)
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
        swingProcessor.reset()
        lastAccelTime = 0
        lastGyroTime = 0
        lastMagTime = 0
    }
}
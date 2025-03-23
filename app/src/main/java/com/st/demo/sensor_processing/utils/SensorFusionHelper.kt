package com.st.demo.sensor_processing.utils

import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.demo.tracker_sensor.utils.Vector3
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.pow

class SensorFusionHelper {
    private val quaternionBuffer = CircularBuffer(5)
    internal var currentOrientation = Vector3.ZERO
    private var currentGravity = Vector3.ZERO

    fun updateOrientation(quaternion: Quaternion) {
        // Buffer quaternion components for smoothing
        quaternionBuffer.add(Vector3(quaternion.qi, quaternion.qj, quaternion.qk))
        val smoothed = quaternionBuffer.average()

        val smoothedQuaternion = Quaternion(
            quaternion.timeStamp,
            smoothed.x,
            smoothed.y,
            smoothed.z,
            quaternion.qs  // Scalar doesn't need smoothing
        )

        currentOrientation = calculateEulerAngles(smoothedQuaternion)
        currentGravity = calculateGravityVector(smoothedQuaternion)
    }

    private fun calculateEulerAngles(q: Quaternion): Vector3 {
        // Roll (X-axis)
        val roll = Math.toDegrees(
            atan2(
                2 * (q.qs * q.qi + q.qj * q.qk),
                1 - 2 * (q.qi.pow(2) + q.qj.pow(2))
            ).toDouble()
        ).toFloat()

        // Pitch (Y-axis)
        val pitch = Math.toDegrees(
            asin(
                2 * (q.qs * q.qj - q.qk * q.qi).toDouble()
            ).toFloat().toDouble()
        ).toFloat()

        val yaw = Math.toDegrees(
            atan2(
                2 * (q.qs * q.qk + q.qi * q.qj),
                1 - 2 * (q.qj.pow(2) + q.qk.pow(2))
            ).toDouble()
        ).toFloat()

        return Vector3(roll, pitch, yaw)
    }

    fun getGravityVector() = currentGravity

    internal fun calculateGravityVector(q: Quaternion): Vector3 {
        // More stable gravity calculation
        return Vector3(
            2 * (q.qi * q.qk - q.qs * q.qj),
            2 * (q.qs * q.qi + q.qj * q.qk),
            q.qs.pow(2) - q.qi.pow(2) - q.qj.pow(2) + q.qk.pow(2)
        ).normalized() * 9.81f
    }
}
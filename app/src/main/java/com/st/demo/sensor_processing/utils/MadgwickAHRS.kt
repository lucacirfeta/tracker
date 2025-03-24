package com.st.demo.sensor_processing.utils

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class MadgwickAHRS(private val sampleRate: Float) {
    // Algorithm parameters
    private var beta = 0.1f // Algorithm gain (adjust this based on your needs)
    private var zeta = 0.0f // Gyroscope drift compensation

    // Quaternion output
    private var q0 = 1.0f
    private var q1 = 0.0f
    private var q2 = 0.0f
    private var q3 = 0.0f

    val quaternion: FloatArray
        get() = floatArrayOf(q0, q1, q2, q3)

    fun setBeta(newBeta: Float) {
        beta = newBeta
    }

    fun update(
        gx: Float, gy: Float, gz: Float,  // Gyroscope (rad/s)
        ax: Float, ay: Float, az: Float,  // Accelerometer (any units, but consistent)
        mx: Float, my: Float, mz: Float   // Magnetometer (any units, but consistent)
    ) {
        val invSampleRate = 1.0f / sampleRate

        // Use IMU algorithm if magnetometer measurement invalid
        if (mx == 0.0f && my == 0.0f && mz == 0.0f) {
            updateIMU(gx, gy, gz, ax, ay, az)
            return
        }

        // Compute feedback only if accelerometer measurement valid
        if (!(ax == 0.0f && ay == 0.0f && az == 0.0f)) {

            // Normalize accelerometer measurement
            val normA = sqrt(ax * ax + ay * ay + az * az)
            val axn = ax / normA
            val ayn = ay / normA
            val azn = az / normA

            // Normalize magnetometer measurement
            val norm2 = sqrt(mx * mx + my * my + mz * mz)
            val mxn = mx / norm2
            val myn = my / norm2
            val mzn = mz / norm2

            // Reference direction of Earth's magnetic field
            val hx =
                2.0f * mx * (0.5f - q2 * q2 - q3 * q3) + 2.0f * my * (q1 * q2 - q0 * q3) + 2.0f * mz * (q1 * q3 + q0 * q2)
            val hy =
                2.0f * mx * (q1 * q2 + q0 * q3) + 2.0f * my * (0.5f - q1 * q1 - q3 * q3) + 2.0f * mz * (q2 * q3 - q0 * q1)
            val bx = sqrt(hx * hx + hy * hy)
            val bz =
                2.0f * mx * (q1 * q3 - q0 * q2) + 2.0f * my * (q2 * q3 + q0 * q1) + 2.0f * mz * (0.5f - q1 * q1 - q2 * q2)

            // Gradient descent algorithm
            val s1 =
                (-2 * q2 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn + 2 * q1 * (2 * (q1 * q3 - q0 * q2) - axn) +
                        (-2 * q3 * (2 * (q1 * q2 + q0 * q3) - ayn) +
                                2 * q0 * (2 * (0.5f - q1 * q1 - q2 * q2) - ayn) +
                                (-bx * q3 + bz * q1) * (2 * (0.5f - q1 * q1 - q2 * q2) - azn) +
                                (bx * q2 + bz * q0) * (2 * (q1 * q3 - q0 * q2) - axn) +
                                (bx * q3 - bz * q1) * (2 * (q1 * q2 + q0 * q3) - ayn))))

            val s2 = (2 * q3 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                    2 * q0 * (2 * (q1 * q3 - q0 * q2) - axn) +
                    (-2 * q1 * (2 * (q1 * q2 + q0 * q3) - ayn)) +
                    (-bx * q0 + bz * q2) * (2 * (0.5f - q1 * q1 - q2 * q2) - azn) +
                    (bx * q3 + bz * q1) * (2 * (q1 * q3 - q0 * q2) - axn) +
                    (bx * q0 - bz * q2) * (2 * (q1 * q2 + q0 * q3) - ayn))

            val s3 = (-2 * q0 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                    2 * q3 * (2 * (q1 * q3 - q0 * q2) - axn) +
                    (-2 * q2 * (2 * (q1 * q2 + q0 * q3) - ayn)) +
                    (bx * q1 + bz * q3) * (2 * (0.5f - q1 * q1 - q2 * q2) - azn) +
                    (bx * q2 + bz * q0) * (2 * (q1 * q3 - q0 * q2) - axn) +
                    (-bx * q1 - bz * q3) * (2 * (q1 * q2 + q0 * q3) - ayn))

            val s4 = (2 * q1 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                    2 * q2 * (2 * (q1 * q3 - q0 * q2) - axn) +
                    (-bx * q2 + bz * q0) * (2 * (0.5f - q1 * q1 - q2 * q2) - azn) +
                    (-bx * q1 + bz * q3) * (2 * (q1 * q3 - q0 * q2) - axn))

            // Normalize step magnitude
            val norm = 1.0f / sqrt(s1 * s1 + s2 * s2 + s3 * s3 + s4 * s4)
            val s1n = s1 * norm
            val s2n = s2 * norm
            val s3n = s3 * norm
            val s4n = s4 * norm

            // Compute rate of change of quaternion
            val qDot1 = 0.5f * (-q1 * gx - q2 * gy - q3 * gz) - beta * s1n
            val qDot2 = 0.5f * (q0 * gx + q2 * gz - q3 * gy) - beta * s2n
            val qDot3 = 0.5f * (q0 * gy - q1 * gz + q3 * gx) - beta * s3n
            val qDot4 = 0.5f * (q0 * gz + q1 * gy - q2 * gx) - beta * s4n

            // Integrate to yield quaternion
            q0 += qDot1 * invSampleRate
            q1 += qDot2 * invSampleRate
            q2 += qDot3 * invSampleRate
            q3 += qDot4 * invSampleRate

            // Normalize quaternion
            val normQ = 1.0f / sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
            q0 *= normQ
            q1 *= normQ
            q2 *= normQ
            q3 *= normQ
        }
    }

    fun updateIMU(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float) {
        val invSampleRate = 1.0f / sampleRate

        // Normalize accelerometer measurement
        val normA = sqrt(ax * ax + ay * ay + az * az)
        if (normA == 0.0f) return // Handle NaN
        val axn = ax / normA
        val ayn = ay / normA
        val azn = az / normA

        // Compute gradient
        val s1 = -2 * q2 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                -2 * q3 * (2 * (q1 * q2 + q0 * q3) - ayn) +
                2 * q0 * (2 * (q1 * q3 - q0 * q2) - axn)

        val s2 = 2 * q3 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                2 * q0 * (2 * (q1 * q3 - q0 * q2) - axn) +
                -2 * q1 * (2 * (q1 * q2 + q0 * q3) - ayn)

        val s3 = -2 * q0 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                2 * q3 * (2 * (q1 * q3 - q0 * q2) - axn) +
                -2 * q2 * (2 * (q1 * q2 + q0 * q3) - ayn)

        val s4 = 2 * q1 * (2 * (0.5f - q2 * q2 - q3 * q3) - azn) +
                2 * q2 * (2 * (q1 * q3 - q0 * q2) - axn)

        // Normalize gradient
        val norm = 1.0f / sqrt(s1 * s1 + s2 * s2 + s3 * s3 + s4 * s4)
        val s1n = s1 * norm
        val s2n = s2 * norm
        val s3n = s3 * norm
        val s4n = s4 * norm

        // Compute rate of change of quaternion
        val qDot1 = 0.5f * (-q1 * gx - q2 * gy - q3 * gz) - beta * s1n
        val qDot2 = 0.5f * (q0 * gx + q2 * gz - q3 * gy) - beta * s2n
        val qDot3 = 0.5f * (q0 * gy - q1 * gz + q3 * gx) - beta * s3n
        val qDot4 = 0.5f * (q0 * gz + q1 * gy - q2 * gx) - beta * s4n

        // Integrate to yield quaternion
        q0 += qDot1 * invSampleRate
        q1 += qDot2 * invSampleRate
        q2 += qDot3 * invSampleRate
        q3 += qDot4 * invSampleRate

        // Normalize quaternion
        val normQ = 1.0f / sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
        q0 *= normQ
        q1 *= normQ
        q2 *= normQ
        q3 *= normQ
    }

    fun getEulerAngles(): FloatArray {
        return floatArrayOf(
            atan2(2 * (q0 * q1 + q2 * q3), 1 - 2 * (q1 * q1 + q2 * q2)), // Roll
            asin(2 * (q0 * q2 - q3 * q1)),                               // Pitch
            atan2(2 * (q0 * q3 + q1 * q2), 1 - 2 * (q2 * q2 + q3 * q3))  // Yaw
        )
    }
}
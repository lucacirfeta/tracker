package com.st.demo.common.utils

import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.demo.common.model.Vector3

object QuaternionHelper {

    fun quaternionToGravity(q: Quaternion): Vector3 {
        return Vector3(
            x = 2 * (q.qi * q.qk - q.qs * q.qj),
            y = 2 * (q.qj * q.qk + q.qs * q.qi),
            z = 1 - 2 * (q.qi * q.qi + q.qj * q.qj)
        )
    }

    fun quaternionToGravity(q: FloatArray): Vector3 {
        return Vector3(
            x = 2 * (q[1] * q[3] - q[0] * q[2]),
            y = 2 * (q[0] * q[1] + q[2] * q[3]),
            z = q[0] * q[0] - q[1] * q[1] - q[2] * q[2] + q[3] * q[3]
        )
    }

    fun dev.romainguy.kotlin.math.Quaternion.toRotationMatrix(matrix: FloatArray) {
        val xx = x * x
        val xy = x * y
        val xz = x * z
        val xw = x * w
        val yy = y * y
        val yz = y * z
        val yw = y * w
        val zz = z * z
        val zw = z * w

        matrix[0] = 1 - 2 * (yy + zz)
        matrix[1] = 2 * (xy - zw)
        matrix[2] = 2 * (xz + yw)
        matrix[3] = 0f

        matrix[4] = 2 * (xy + zw)
        matrix[5] = 1 - 2 * (xx + zz)
        matrix[6] = 2 * (yz - xw)
        matrix[7] = 0f

        matrix[8] = 2 * (xz - yw)
        matrix[9] = 2 * (yz + xw)
        matrix[10] = 1 - 2 * (xx + yy)
        matrix[11] = 0f

        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f
    }

    fun inverse(quaternion: Quaternion): Quaternion {
        val norm =
            quaternion.qi * quaternion.qi + quaternion.qj * quaternion.qj + quaternion.qk * quaternion.qk + quaternion.qs * quaternion.qs
        return Quaternion(
            timeStamp = quaternion.timeStamp,
            qi = -quaternion.qi / norm,
            qj = -quaternion.qj / norm,
            qk = -quaternion.qk / norm,
            qs = quaternion.qs / norm
        )
    }

    fun multiply(q1: Quaternion, q2: Quaternion): Quaternion {
        val qi = q1.qs * q2.qi + q1.qi * q2.qs + q1.qj * q2.qk - q1.qk * q2.qj
        val qj = q1.qs * q2.qj - q1.qi * q2.qk + q1.qj * q2.qs + q1.qk * q2.qi
        val qk = q1.qs * q2.qk + q1.qi * q2.qj - q1.qj * q2.qi + q1.qk * q2.qs
        val qs = q1.qs * q2.qs - q1.qi * q2.qi - q1.qj * q2.qj - q1.qk * q2.qk
        return Quaternion(
            timeStamp = q1.timeStamp,
            qi = qi,
            qj = qj,
            qk = qk,
            qs = qs
        )
    }

    fun toMathQuaternion(quaternion: Quaternion): dev.romainguy.kotlin.math.Quaternion {
        return dev.romainguy.kotlin.math.Quaternion(
            quaternion.qi,
            quaternion.qj,
            quaternion.qk,
            quaternion.qs
        )
    }


    fun computeAngularVelocity(
        currentQuaternion: Quaternion,
        previousQuaternion: Quaternion,
        deltaTime: Float
    ): Vector3 {
        // Quaternion difference: Δq = current ⊗ previous⁻¹
        val deltaQ = multiply(currentQuaternion, inverse(previousQuaternion))

        // Convert quaternion to angular velocity (rad/s)
        return Vector3(
            x = 2 * deltaQ.qi / deltaTime,
            y = 2 * deltaQ.qj / deltaTime,
            z = 2 * deltaQ.qk / deltaTime
        )
    }
}
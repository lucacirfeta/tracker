package com.st.demo.render.utils

import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.demo.tracker_sensor.utils.Vector3

object QuaternionHelper {

    fun quaternionToGravity(q: FloatArray): Vector3 {
        return Vector3(
            x = 2 * (q[1] * q[3] - q[0] * q[2]),
            y = 2 * (q[0] * q[1] + q[2] * q[3]),
            z = q[0] * q[0] - q[1] * q[1] - q[2] * q[2] + q[3] * q[3]
        )
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
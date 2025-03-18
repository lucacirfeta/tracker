package com.st.demo.tracker_sensor.utils

object QuaternionHelper {
    fun quaternionToGravity(q: FloatArray): Vector3 {
        return Vector3(
            x = 2 * (q[1] * q[3] - q[0] * q[2]),
            y = 2 * (q[0] * q[1] + q[2] * q[3]),
            z = q[0] * q[0] - q[1] * q[1] - q[2] * q[2] + q[3] * q[3]
        )
    }
}
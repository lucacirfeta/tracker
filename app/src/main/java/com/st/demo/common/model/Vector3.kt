package com.st.demo.common.model

import kotlin.math.sqrt

data class Vector3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus() = Vector3(-x, -y, -z)

    fun normalized(): Vector3 {
        val length = length()
        return if (length > 0) this * (1f / length) else this
    }

    fun length() = sqrt(x * x + y * y + z * z)

    companion object {
        val ZERO = Vector3()
    }
}
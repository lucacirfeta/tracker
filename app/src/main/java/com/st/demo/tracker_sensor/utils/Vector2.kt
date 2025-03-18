package com.st.demo.tracker_sensor.utils

data class Vector2(val x: Float = 0f, val y: Float = 0f) {
    companion object {
        val ZERO = Vector2()
    }
}

fun Vector3.toVector2() = Vector2(x, y)
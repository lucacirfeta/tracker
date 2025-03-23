package com.st.demo.sensor_processing.utils

import com.st.demo.tracker_sensor.utils.Vector3

class CircularBuffer(private val capacity: Int) {
    private val buffer = mutableListOf<Vector3>()

    fun add(vector: Vector3) {
        if (buffer.size >= capacity) buffer.removeAt(0)
        buffer.add(vector)
    }

    fun average(): Vector3 {
        if (buffer.isEmpty()) return Vector3.ZERO
        val sum = buffer.reduce { acc, vec -> acc + vec }
        return sum * (1f / buffer.size)
    }
}
package com.st.demo.sensor_processing.audio

import kotlin.math.abs
import kotlin.math.log10

object AudioAnalyzer {
    fun analyzeImpact(buffer: ShortArray): Pair<Float, Float> {
        var sum = 0f
        var zeroCrossings = 0
        var prev = 0f

        buffer.forEach { sample ->
            val s = sample.toFloat() / 32768f
            sum += abs(s)

            if (s * prev < 0) zeroCrossings++
            prev = s
        }

        val avgAmplitude = sum / buffer.size
        val dB = 20 * log10(avgAmplitude)
        return Pair(avgAmplitude * 10f, zeroCrossings.toFloat())
    }
}
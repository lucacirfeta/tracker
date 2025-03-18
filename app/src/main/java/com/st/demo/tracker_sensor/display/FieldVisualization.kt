package com.st.demo.tracker_sensor.display

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.st.demo.tracker_sensor.utils.Vector2

@Composable()
internal fun FieldVisualization(
    position: Vector2,
    highlights: List<Vector2>,
    fieldSize: Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / fieldSize.first
        val scaleY = size.height / fieldSize.second

        // Draw highlights
        highlights.forEach { highlight ->
            drawCircle(
                color = Color.Red,
                center = Offset(highlight.x * scaleX, highlight.y * scaleY),
                radius = 8f
            )
        }

        // Draw current position
        drawCircle(
            color = Color.Blue,
            center = Offset(position.x * scaleX, position.y * scaleY),
            radius = 12f
        )
    }
}
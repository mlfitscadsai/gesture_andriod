package com.example.tellogesturecontrol.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.tellogesturecontrol.gesture.HandDetectionResult

private val HAND_CONNECTIONS = listOf(
    // Thumb
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    // Index
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    // Middle
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    // Ring
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    // Pinky
    13 to 17, 17 to 18, 18 to 19, 19 to 20,
    // Palm base
    0 to 17
)

@Composable
fun HandOverlay(
    detection: HandDetectionResult,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        detection.boundingBox?.let { box ->
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(box.left * w, box.top * h),
                size = Size((box.right - box.left) * w, (box.bottom - box.top) * h),
                style = Stroke(width = 4f)
            )
        }

        val pts = detection.landmarks
        if (pts.size >= 21) {
            for ((startIdx, endIdx) in HAND_CONNECTIONS) {
                val p1 = pts[startIdx]
                val p2 = pts[endIdx]
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(p1.first * w, p1.second * h),
                    end = Offset(p2.first * w, p2.second * h),
                    strokeWidth = 5f
                )
            }

            pts.forEachIndexed { index, (nx, ny) ->
                val isTip = index == 4 || index == 8 || index == 12 || index == 16 || index == 20
                drawCircle(
                    color = if (isTip) Color(0xFFFFEA00) else Color.White,
                    radius = if (isTip) 10f else 7f,
                    center = Offset(nx * w, ny * h)
                )
                drawCircle(
                    color = Color.Black,
                    radius = if (isTip) 10f else 7f,
                    center = Offset(nx * w, ny * h),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

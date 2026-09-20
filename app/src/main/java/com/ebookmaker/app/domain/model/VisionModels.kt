package com.ebookmaker.app.domain.model

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class VisionPoint(
    val x: Float,
    val y: Float
) {
    fun distanceTo(other: VisionPoint): Float {
        return hypot(x - other.x, y - other.y)
    }
}

data class Quadrilateral(
    val topLeft: VisionPoint,
    val topRight: VisionPoint,
    val bottomRight: VisionPoint,
    val bottomLeft: VisionPoint
) {
    val points: List<VisionPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    // Calculate approximate polygon area using Shoelace formula
    val area: Float
        get() {
            var sum1 = 0f
            var sum2 = 0f
            val pts = points
            for (i in pts.indices) {
                val next = pts[(i + 1) % pts.size]
                sum1 += pts[i].x * next.y
                sum2 += pts[i].y * next.x
            }
            return kotlin.math.abs(sum1 - sum2) / 2f
        }

    val width: Float
        get() {
            val topW = topLeft.distanceTo(topRight)
            val botW = bottomLeft.distanceTo(bottomRight)
            return max(topW, botW)
        }

    val height: Float
        get() {
            val leftH = topLeft.distanceTo(bottomLeft)
            val rightH = topRight.distanceTo(bottomRight)
            return max(leftH, rightH)
        }

    fun isMaxMovementLessThan(other: Quadrilateral, threshold: Float): Boolean {
        val d1 = topLeft.distanceTo(other.topLeft)
        val d2 = topRight.distanceTo(other.topRight)
        val d3 = bottomRight.distanceTo(other.bottomRight)
        val d4 = bottomLeft.distanceTo(other.bottomLeft)
        return max(max(d1, d2), max(d3, d4)) < threshold
    }
}

data class DetectionResult(
    val quad: Quadrilateral?,
    val sharpness: Float,
    val isStable: Boolean,
    val frameDiff: Float,
    val timestamp: Long = System.currentTimeMillis()
)

enum class EnhancementMode {
    COLOR,
    GRAYSCALE,
    BINARIZED
}

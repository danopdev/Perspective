package com.dan.perspective

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.sqrt


data class LineF(val from: PointF, val to: PointF ) {
    val segmentDeltaX: Float get() = to.x - from.y;
    val segmentDeltaY: Float get() = to.y - from.y;
    val segmentLength: Float get() {
        val dx = segmentDeltaX
        val dy = segmentDeltaY
        return sqrt(dx * dx + dy * dy)
    }
}


class Perspective {
    companion object {
        const val EPSILON: Float = 0.01f

        fun distance( point: PointF, line: LineF ): Float {
            return abs(
                    line.segmentDeltaX * ( line.from.y - point.y ) - (line.from.x - point.x) * line.segmentDeltaY
                    ) / line.segmentLength
        }

        fun distance( from: PointF, to: PointF ): Float {
            return LineF(from, to).segmentLength
        }

        fun intersection( line1: LineF, line2: LineF ): PointF? {
            val denominator = line1.segmentDeltaX * line2.segmentDeltaY - line1.segmentDeltaY * line2.segmentDeltaX
            if (abs(denominator) < EPSILON) return null

            val a = line1.from.x * line1.to.y - line1.from.y * line1.to.x
            val b = line2.from.x * line2.to.y - line2.from.y * line2.to.x
            val x = a * line2.segmentDeltaX - line1.segmentDeltaX * b
            val y = a * line2.segmentDeltaY - line1.segmentDeltaY * b

            return PointF( x / denominator, y / denominator )
        }
    }

    val pointTopLeft = PointF()
    val pointTopRight = PointF()
    val pointBottomLeft = PointF()
    val pointBottomRight = PointF()
    val lineTop = LineF( pointTopLeft, pointTopRight )
    val lineBottom = LineF( pointBottomLeft, pointBottomRight )
    val lineLeft = LineF( pointTopLeft, pointBottomLeft )
    val lineRight = LineF( pointTopRight, pointBottomRight )
}

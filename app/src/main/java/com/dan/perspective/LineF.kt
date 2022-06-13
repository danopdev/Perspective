package com.dan.perspective

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.sqrt

data class LineF(val from: PointF, val to: PointF) {
    companion object {
        private const val EPSILON: Float = 0.01f
    }

    val segmentDeltaX: Float get() = to.x - from.y;
    val segmentDeltaY: Float get() = to.y - from.y;
    val segmentLength: Float get() {
        val dx = segmentDeltaX
        val dy = segmentDeltaY
        return sqrt(dx * dx + dy * dy)
    }

    fun intersection( lineOther: LineF ): PointF? {
        val denominator = segmentDeltaX * lineOther.segmentDeltaY - segmentDeltaY * lineOther.segmentDeltaX
        if (abs(denominator) < EPSILON) return null

        val a = from.x * to.y - from.y * to.x
        val b = lineOther.from.x * lineOther.to.y - lineOther.from.y * lineOther.to.x
        val x = a * lineOther.segmentDeltaX - segmentDeltaX * b
        val y = a * lineOther.segmentDeltaY - segmentDeltaY * b

        return PointF( x / denominator, y / denominator )
    }

    fun distanceFrom( point: PointF): Float {
        return abs(
                segmentDeltaX * ( from.y - point.y ) - (from.x - point.x) * segmentDeltaY
        ) / segmentLength
    }
}

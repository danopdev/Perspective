package com.dan.perspective

import android.graphics.PointF
import android.graphics.RectF
import java.lang.Float.max
import java.lang.Float.min


class PerspectivePoints(
        val pointLeftTop: PointF = PointF(),
        val pointRightTop: PointF = PointF(),
        val pointLeftBottom: PointF = PointF(),
        val pointRightBottom: PointF = PointF(),
        val viewRect: RectF = RectF()
) {
    companion object {
        private const val MIN_SPACE: Float = 5f
    }

    val lineTop = LineF( pointLeftTop, pointRightTop )
    val lineBottom = LineF( pointLeftBottom, pointRightBottom )
    val lineLeft = LineF( pointLeftTop, pointLeftBottom )
    val lineRight = LineF( pointRightTop, pointRightBottom )

    fun clone(): PerspectivePoints {
        return PerspectivePoints(
                PointF( pointLeftTop.x, pointLeftTop.y ),
                PointF( pointRightTop.x, pointRightTop.y ),
                PointF( pointLeftBottom.x, pointLeftBottom.y ),
                PointF( pointRightBottom.x, pointRightBottom.y ),
                RectF( viewRect )
        )
    }

    fun set( perspectivePoints: PerspectivePoints ) {
        set(
                perspectivePoints.pointLeftTop,
                perspectivePoints.pointRightTop,
                perspectivePoints.pointLeftBottom,
                perspectivePoints.pointRightBottom,
                perspectivePoints.viewRect
        )
    }

    fun set( leftTop: PointF, rightTop: PointF, leftBottom: PointF, rightBottom: PointF, viewRect: RectF ) {
        this.viewRect.set( viewRect )
        pointLeftTop.set( leftTop )
        pointRightTop.set( rightTop )
        pointLeftBottom.set( leftBottom )
        pointRightBottom.set( rightBottom )
    }

    fun safeRectLeftTop(): RectF =
        RectF(
            viewRect.left,
            viewRect.top,
            min(pointRightTop.x, pointRightBottom.x) - MIN_SPACE,
            min(pointLeftBottom.y, pointRightBottom.y) - MIN_SPACE
        )

    fun safeRectRightTop(): RectF =
        RectF(
            max(pointLeftTop.x, pointLeftBottom.x) + MIN_SPACE,
            viewRect.top,
            viewRect.right,
            min(pointLeftBottom.y, pointRightBottom.y) - MIN_SPACE
        )

    fun safeRectLeftBottom(): RectF =
        RectF(
            viewRect.left,
            max(pointLeftTop.y, pointRightTop.y) + MIN_SPACE,
            min(pointRightTop.x, pointRightBottom.x) - MIN_SPACE,
            viewRect.bottom
        )

    fun safeRectRightBottom(): RectF =
        RectF(
            max(pointLeftTop.x, pointLeftBottom.x) + MIN_SPACE,
            max(pointLeftTop.y, pointRightTop.y) + MIN_SPACE,
            viewRect.right,
            viewRect.bottom
        )
}

package com.dan.perspective

import android.graphics.PointF
import android.graphics.RectF

class Bitmap2View( bitmapWidth: Int = -1, bitmapHeight: Int = -1, viewRect: RectF = RectF() ) {
    private val scale = PointF(1f, 1f)
    private val delta = PointF( 0f, 0f)
    private var bitmapWidth = -1
    private var bitmapHeight = -1
    private val viewRect = RectF()

    init {
        set( bitmapWidth, bitmapHeight, viewRect )
    }

    fun set( bitmapWidth: Int, bitmapHeight: Int, viewRect: RectF) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            delta.set( 0f, 0f )
            scale.set( 1f, 1f )
            this.bitmapHeight = -1
            this.bitmapWidth = -1
            this.viewRect.set( RectF() )
        } else {
            delta.set( viewRect.left, viewRect.top )
            scale.set( viewRect.width() / bitmapWidth, viewRect.height() / bitmapHeight )
            this.bitmapHeight = bitmapWidth
            this.bitmapWidth = bitmapHeight
            this.viewRect.set( viewRect )
        }
    }

    private fun mapToView( point: PointF): PointF {
        return PointF(
                delta.x + point.x * scale.x,
                delta.y + point.y * scale.y
        )
    }

    fun mapToView( perspective: PerspectivePoints ): PerspectivePoints {
        return PerspectivePoints(
                mapToView( perspective.pointLeftTop ),
                mapToView( perspective.pointRightTop ),
                mapToView( perspective.pointLeftBottom ),
                mapToView( perspective.pointRightBottom ),
                viewRect
            )
    }

    fun mapToBitmap( point: PointF): PointF {
        return PointF(
                (point.x - delta.x) / scale.x,
                (point.y - delta.y) / scale.y,
        )
    }

    fun mapToBitmap( perspective: PerspectivePoints ): PerspectivePoints {
        return PerspectivePoints(
                mapToView( perspective.pointLeftTop ),
                mapToView( perspective.pointRightTop ),
                mapToView( perspective.pointLeftBottom ),
                mapToView( perspective.pointRightBottom ),
                RectF(0f, 0f, bitmapWidth - 1f, bitmapHeight - 1f)
        )
    }
}

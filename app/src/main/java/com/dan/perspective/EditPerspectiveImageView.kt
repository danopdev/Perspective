package com.dan.perspective

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent

data class PerspectivePoints(
        val leftTop: PointF = PointF(),
        val leftBottom: PointF = PointF(),
        val rightTop: PointF = PointF(),
        val rightBottom: PointF = PointF()
) {
    fun set( points: PerspectivePoints ) {
        leftTop.set(points.leftTop)
        leftBottom.set(points.leftBottom)
        rightTop.set(points.rightTop)
        rightBottom.set(points.rightBottom)
    }
}


private class ViewTransform(bitmapWidth: Int, bitmapHeight: Int, viewRect: RectF) {
    private val scale = PointF()
    private val delta = PointF()

    init {
        delta.set( viewRect.left, viewRect.top )
        scale.set( viewRect.width() / bitmapWidth, viewRect.height() / bitmapHeight )
    }

    fun mapToView( point: PointF ): PointF {
        return PointF(
                delta.x + point.x * scale.x,
                delta.y + point.y * scale.y
        )
    }

    fun mapToView( points: PerspectivePoints ): PerspectivePoints {
        return PerspectivePoints(
                mapToView( points.leftTop ),
                mapToView( points.leftBottom ),
                mapToView( points.rightTop ),
                mapToView( points.rightBottom )
        )
    }

    fun mapToBitmap( point: PointF ): PointF {
        return PointF(
                (point.x - delta.x) / scale.x,
                (point.y - delta.y) / scale.y,
        )
    }

    fun mapToBitmap( points: PerspectivePoints ): PerspectivePoints {
        return PerspectivePoints(
                mapToBitmap( points.leftTop ),
                mapToBitmap( points.leftBottom ),
                mapToBitmap( points.rightTop ),
                mapToBitmap( points.rightBottom )
        )
    }
}

class EditPerspectiveImageView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TouchImageView(context, attrs, defStyleAttr) {

    companion object {
        const val BORDER = 5 //percent
        const val POINT_RADIUS = 15 // dp
        const val LINE_WIDTH = 5 //dp
        const val MIN_POINT_DISTANCE_TO_TRACK = 20 //dp
    }

    private val perspectivePoints_ = PerspectivePoints()
    private var trackedPoint: PointF? = null
    private var trackedViewPoint = PointF()
    private val trackedOldPosition = PointF()

    var perspectivePoints: PerspectivePoints
        get() = perspectivePoints_
        set(points) {
            perspectivePoints_.set(points)
            invalidate()
        }

    override fun setBitmap(bitmap: Bitmap? ) {
        super.setBitmap(bitmap)
        resetPoints()
    }

    private fun dpToPixels( value: Int ): Float {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                Resources.getSystem().displayMetrics)
    }

    fun resetPoints() {
        val bitmap = super.getBitmap() ?: return

        val left = bitmap.width * BORDER.toFloat() / 100
        val right = bitmap.width - left
        val top = bitmap.height * BORDER.toFloat() / 100
        val bottom = bitmap.height - top

        perspectivePoints_.leftTop.set(left, top)
        perspectivePoints_.leftBottom.set(left, bottom)
        perspectivePoints_.rightTop.set(right, top)
        perspectivePoints_.rightBottom.set(right, bottom)
    }

    private fun drawPoint( point: PointF, radius: Float, canvas: Canvas, paint: Paint ) {
        canvas.drawCircle( point.x, point.y, radius, paint )
    }

    private fun calculateCy( Ax: Float, Ay: Float, Bx: Float, By: Float, Cx:Float ): Float =
        Ay + (By - Ay) * (Cx - Ax) / (Bx - Ax)

    private fun drawLine( pointA: PointF, pointB: PointF, canvas: Canvas, paint: Paint, viewRect: RectF, isHorizontal: Boolean ) {
        val pointFrom: PointF
        val pointTo: PointF

        if (isHorizontal) {
            pointFrom = PointF(
                viewRect.left,
                calculateCy( pointA.x, pointA.y, pointB.x, pointB.y, viewRect.left )
            )
            pointTo = PointF(
                    viewRect.right,
                    calculateCy( pointA.x, pointA.y, pointB.x, pointB.y, viewRect.right )
            )
        } else {
            pointFrom = PointF(
                    calculateCy( pointA.y, pointA.x, pointB.y, pointB.x, viewRect.top ),
                    viewRect.top
            )
            pointTo = PointF(
                    calculateCy( pointA.y, pointA.x, pointB.y, pointB.x, viewRect.bottom ),
                    viewRect.bottom
            )
        }

        canvas.drawLine( pointFrom.x, pointFrom.y, pointTo.x, pointTo.y, paint )
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (null == canvas) return

        val bitmap = super.getBitmap() ?: return
        val viewRect = super.viewRect
        val transform = ViewTransform( bitmap.width, bitmap.height, viewRect )
        val viewPoints = transform.mapToView( perspectivePoints_ )

        val paint = Paint()

        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = dpToPixels(LINE_WIDTH)
        paint.color = Color.argb( 128, 255, 0, 0 )
        drawLine( viewPoints.leftTop, viewPoints.leftBottom, canvas, paint, viewRect, false )
        drawLine( viewPoints.leftTop, viewPoints.rightTop, canvas, paint, viewRect, true )
        drawLine( viewPoints.rightTop, viewPoints.rightBottom, canvas, paint, viewRect, false )
        drawLine( viewPoints.leftBottom, viewPoints.rightBottom, canvas, paint, viewRect, true )

        val radius = dpToPixels(POINT_RADIUS)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb( 128, 0, 0, 255 )
        drawPoint( viewPoints.leftTop, radius, canvas, paint )
        drawPoint( viewPoints.leftBottom, radius, canvas, paint )
        drawPoint( viewPoints.rightTop, radius, canvas, paint )
        drawPoint( viewPoints.rightBottom, radius, canvas, paint )
    }

    private fun distance( pointA: PointF, pointB: PointF ): Float =
            PointF.length( pointA.x - pointB.x, pointA.y - pointB.y )

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (null == event) return true
        val bitmap = super.getBitmap() ?: return true

        when( event.action ) {
            MotionEvent.ACTION_DOWN -> {
                Log.i("EDIT", "DOWN")

                if (null == trackedPoint) {
                    val screenPoint = PointF(event.x, event.y)
                    val transform = ViewTransform(bitmap.width, bitmap.height, viewRect)
                    val viewPoints = transform.mapToView(perspectivePoints_)
                    val minDistance = dpToPixels(MIN_POINT_DISTANCE_TO_TRACK)

                    trackedOldPosition.set(event.x, event.y)

                    if (distance(viewPoints.leftTop, screenPoint) < minDistance) {
                        trackedPoint = perspectivePoints_.leftTop
                        trackedViewPoint.set(viewPoints.leftTop)
                    } else if (distance(viewPoints.leftBottom, screenPoint) < minDistance) {
                        trackedPoint = perspectivePoints_.leftBottom
                        trackedViewPoint.set(viewPoints.leftBottom)
                    } else if (distance(viewPoints.rightTop, screenPoint) < minDistance) {
                        trackedPoint = perspectivePoints_.rightTop
                        trackedViewPoint.set(viewPoints.rightTop)
                    } else if (distance(viewPoints.rightBottom, screenPoint) < minDistance) {
                        trackedPoint = perspectivePoints_.rightBottom
                        trackedViewPoint.set(viewPoints.rightBottom)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val trackedPoint = this.trackedPoint

                if (null != trackedPoint) {
                    val transform = ViewTransform(bitmap.width, bitmap.height, viewRect)

                    val dx = event.x - trackedOldPosition.x
                    val dy = event.y - trackedOldPosition.y

                    trackedViewPoint.offset( dx, dy )
                    trackedPoint.set( transform.mapToBitmap(trackedViewPoint) )

                    Log.i("EDIT", "dx: ${dx}, dy: ${dy}, trackedPoint: ${trackedPoint}")

                    trackedOldPosition.set(event.x, event.y)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (null != trackedPoint) {
                    trackedPoint = null
                    return true
                }
            }
        }

        if (null != trackedPoint) {
            Log.i("EDIT", "Track is ON")
            return true
        }

        return super.onTouchEvent(event)
    }
}
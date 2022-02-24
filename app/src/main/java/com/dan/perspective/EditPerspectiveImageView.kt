package com.dan.perspective

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    private val _perspectivePoints = PerspectivePoints()
    private var trackedPoint: PointF? = null
    private var trackedViewPoint = PointF()
    private var trackedAllowedRect = RectF()
    private val trackedOldPosition = PointF()
    private var onPerspectiveChanged: (()->Unit)? = null
    private var onEditStart: (()->Unit)? = null
    private var onEditEnd: (()->Unit)? = null

    var perspectivePoints: PerspectivePoints
        get() = _perspectivePoints
        set(points) {
            _perspectivePoints.set(points)
            invalidate()
        }

    fun setOnPerspectiveChanged( listener: (()->Unit)? ) {
        onPerspectiveChanged = listener
    }

    fun setOnEditStart( listener: (()->Unit)? ) {
        onEditStart = listener
    }

    fun setOnEditEnd( listener: (()->Unit)? ) {
        onEditEnd = listener
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

        _perspectivePoints.leftTop.set(left, top)
        _perspectivePoints.leftBottom.set(left, bottom)
        _perspectivePoints.rightTop.set(right, top)
        _perspectivePoints.rightBottom.set(right, bottom)

        invalidate()
        onPerspectiveChanged?.invoke()
    }

    private fun drawPoint( point: PointF, radius: Float, canvas: Canvas, paint: Paint ) {
        canvas.drawCircle( point.x, point.y, radius, paint )
    }

    private fun calculateCy( Ax: Float, Ay: Float, Bx: Float, By: Float, Cx:Float ): Float =
        Ay + (By - Ay) * (Cx - Ax) / (Bx - Ax)

    private fun drawLine( pointA: PointF, pointB: PointF, canvas: Canvas, paint: Paint, viewRect: RectF ) {
        val pointFrom: PointF
        val pointTo: PointF

        val isHorizontal = abs(pointA.x - pointB.x) > abs(pointA.y - pointB.y)

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
        val viewPoints = transform.mapToView( _perspectivePoints )

        val paint = Paint()

        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = dpToPixels(LINE_WIDTH)
        paint.color = Color.argb( 128, 255, 0, 0 )
        drawLine( viewPoints.leftTop, viewPoints.leftBottom, canvas, paint, viewRect )
        drawLine( viewPoints.leftTop, viewPoints.rightTop, canvas, paint, viewRect )
        drawLine( viewPoints.rightTop, viewPoints.rightBottom, canvas, paint, viewRect )
        drawLine( viewPoints.leftBottom, viewPoints.rightBottom, canvas, paint, viewRect )

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
                if (null == trackedPoint) {
                    val screenPoint = PointF(event.x, event.y)
                    val transform = ViewTransform(bitmap.width, bitmap.height, viewRect)
                    val viewPoints = transform.mapToView(_perspectivePoints)
                    val minDistance = dpToPixels(MIN_POINT_DISTANCE_TO_TRACK)

                    trackedOldPosition.set(event.x, event.y)

                    when {
                        distance(viewPoints.leftTop, screenPoint) < minDistance -> {
                            trackedPoint = _perspectivePoints.leftTop
                            trackedViewPoint.set(viewPoints.leftTop)

                            trackedAllowedRect.set(
                                    viewRect.left,
                                    viewRect.top,
                                    min(viewPoints.rightTop.x, viewPoints.rightBottom.x) - minDistance,
                                    min(viewPoints.leftBottom.y, viewPoints.rightBottom.y) - minDistance
                            )
                        }

                        distance(viewPoints.leftBottom, screenPoint) < minDistance -> {
                            trackedPoint = _perspectivePoints.leftBottom
                            trackedViewPoint.set(viewPoints.leftBottom)

                            trackedAllowedRect.set(
                                    viewRect.left,
                                    max(viewPoints.leftTop.y, viewPoints.rightTop.y) + minDistance,
                                    min(viewPoints.rightTop.x, viewPoints.rightBottom.x) - minDistance,
                                    viewRect.bottom
                            )
                        }

                        distance(viewPoints.rightTop, screenPoint) < minDistance -> {
                            trackedPoint = _perspectivePoints.rightTop
                            trackedViewPoint.set(viewPoints.rightTop)

                            trackedAllowedRect.set(
                                    max(viewPoints.leftTop.x, viewPoints.leftBottom.x) + minDistance,
                                    viewRect.top,
                                    viewRect.right,
                                    min(viewPoints.leftBottom.y, viewPoints.rightBottom.y) - minDistance
                            )
                        }

                        distance(viewPoints.rightBottom, screenPoint) < minDistance -> {
                            trackedPoint = _perspectivePoints.rightBottom
                            trackedViewPoint.set(viewPoints.rightBottom)

                            trackedAllowedRect.set(
                                    max(viewPoints.leftTop.x, viewPoints.leftBottom.x) + minDistance,
                                    max(viewPoints.leftTop.y, viewPoints.rightTop.y) + minDistance,
                                    viewRect.right,
                                    viewRect.bottom
                            )
                        }
                    }

                    if (null != trackedPoint) onEditStart?.invoke()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val trackedPoint = this.trackedPoint

                if (null != trackedPoint) {
                    val transform = ViewTransform(bitmap.width, bitmap.height, viewRect)

                    val dx = event.x - trackedOldPosition.x
                    val dy = event.y - trackedOldPosition.y

                    trackedViewPoint.offset( dx, dy )
                    if (trackedViewPoint.x < trackedAllowedRect.left) trackedViewPoint.x = trackedAllowedRect.left
                    if (trackedViewPoint.x > trackedAllowedRect.right) trackedViewPoint.x = trackedAllowedRect.right
                    if (trackedViewPoint.y < trackedAllowedRect.top) trackedViewPoint.y = trackedAllowedRect.top
                    if (trackedViewPoint.y > trackedAllowedRect.bottom) trackedViewPoint.y = trackedAllowedRect.bottom

                    trackedPoint.set( transform.mapToBitmap(trackedViewPoint) )
                    trackedOldPosition.set(event.x, event.y)
                    invalidate()
                    onPerspectiveChanged?.invoke()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (null != trackedPoint) {
                    trackedPoint = null
                    onEditEnd?.invoke()
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
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

class EditPerspectiveImageView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TouchImageView(context, attrs, defStyleAttr) {

    companion object {
        const val BORDER = 5 //percent
        const val POINT_RADIUS = 15 // dp
        const val TRACKED_POINT_RADIUS = 20 // dp
        const val LINE_WIDTH = 5 //dp
        const val MIN_POINT_DISTANCE_TO_TRACK = 20 //dp

        const val POINT_EDIT_DIRECTION_ALL = 0
        const val POINT_EDIT_DIRECTION_HORIZONTAL = 1
        const val POINT_EDIT_DIRECTION_VERTICAL = 2

        fun dpToPixels( value: Int ): Float {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value.toFloat(),
                    Resources.getSystem().displayMetrics)
        }
    }

    var pointEditDirection = POINT_EDIT_DIRECTION_ALL

    private val perspectivePoints = PerspectivePoints()
    private val transform = Bitmap2View()

    private val trackedOldPosition = PointF()

    //Edit point
    private var trackedPoint: PointF? = null
    private var trackedViewPoint = PointF()
    private var trackedAllowedRect = RectF()

    //Edit line: second point
    private var trackedPoint2: PointF? = null
    private var trackedViewPoint2 = PointF()
    private var trackedAllowedRect2 = RectF()

    private val paint = Paint()
    private var onPerspectiveChanged: (()->Unit)? = null
    private var onEditStart: (()->Unit)? = null
    private var onEditEnd: (()->Unit)? = null

    fun getPerspective(): PerspectivePoints = perspectivePoints.clone()

    fun setPerspective(leftTop: PointF, rightTop: PointF, leftBottom: PointF, rightBottom: PointF) {
        perspectivePoints.set( leftTop, rightTop, leftBottom, rightBottom, this.perspectivePoints.viewRect )
        invalidate()
        onPerspectiveChanged?.invoke()
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

    fun resetPoints() {
        val bitmap = super.getBitmap() ?: return

        val left = bitmap.width * BORDER.toFloat() / 100
        val right = bitmap.width - left
        val top = bitmap.height * BORDER.toFloat() / 100
        val bottom = bitmap.height - top

        perspectivePoints.set(
                PointF(left, top),
                PointF(right, top),
                PointF(left, bottom),
                PointF(right, bottom),
                RectF( 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat() )
        )

        invalidate()
        onPerspectiveChanged?.invoke()
    }

    private fun drawPoint( point: PointF, canvas: Canvas, paint: Paint, isActive: Boolean ) {
        val radius = if (isActive) dpToPixels(TRACKED_POINT_RADIUS) else dpToPixels(POINT_RADIUS)
        paint.style = if (isActive) Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE
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

        transform.set( bitmap.width, bitmap.height, viewRect )
        val viewPerspective = transform.mapToView( perspectivePoints )

        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = dpToPixels(LINE_WIDTH)
        paint.color = Color.argb( 128, 255, 0, 0 )
        drawLine( viewPerspective.pointLeftTop, viewPerspective.pointLeftBottom, canvas, paint, viewRect )
        drawLine( viewPerspective.pointLeftTop, viewPerspective.pointRightTop, canvas, paint, viewRect )
        drawLine( viewPerspective.pointRightTop, viewPerspective.pointRightBottom, canvas, paint, viewRect )
        drawLine( viewPerspective.pointLeftBottom, viewPerspective.pointRightBottom, canvas, paint, viewRect )

        paint.style = Paint.Style.FILL_AND_STROKE
        paint.color = Color.argb( 128, 0, 0, 255 )

        drawPoint( viewPerspective.pointLeftTop, canvas, paint, perspectivePoints.pointLeftTop == trackedPoint )
        drawPoint( viewPerspective.pointLeftBottom, canvas, paint, perspectivePoints.pointLeftBottom == trackedPoint )
        drawPoint( viewPerspective.pointRightTop, canvas, paint, perspectivePoints.pointRightTop == trackedPoint )
        drawPoint( viewPerspective.pointRightBottom, canvas, paint, perspectivePoints.pointRightBottom == trackedPoint )
    }

    private fun distance( pointA: PointF, pointB: PointF ): Float =
            PointF.length( pointA.x - pointB.x, pointA.y - pointB.y )

    private fun startEditPoint( trackedPoint: PointF, trackedViewPoint: PointF, allowedRect: RectF ) {
        this.trackedPoint2 = null

        this.trackedPoint = trackedPoint
        this.trackedViewPoint.set( trackedViewPoint )
        this.trackedAllowedRect.set( allowedRect )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (null == event) return true
        val bitmap = super.getBitmap() ?: return true

        when( event.action ) {
            MotionEvent.ACTION_DOWN -> {
                if (null == trackedPoint) {
                    transform.set(bitmap.width, bitmap.height, viewRect)

                    val screenPoint = PointF(event.x, event.y)
                    val viewPerspective = transform.mapToView(perspectivePoints)
                    val minDistance = dpToPixels(MIN_POINT_DISTANCE_TO_TRACK)

                    trackedOldPosition.set(event.x, event.y)

                    when {
                        distance(viewPerspective.pointLeftTop, screenPoint) < minDistance -> {
                            startEditPoint(
                                    perspectivePoints.pointLeftTop,
                                    viewPerspective.pointLeftTop,
                                    viewPerspective.safeRectLeftTop()
                            )
                        }

                        distance(viewPerspective.pointLeftBottom, screenPoint) < minDistance -> {
                            startEditPoint(
                                    perspectivePoints.pointLeftBottom,
                                    viewPerspective.pointLeftBottom,
                                    viewPerspective.safeRectLeftBottom()
                            )
                        }

                        distance(viewPerspective.pointRightTop, screenPoint) < minDistance -> {
                            startEditPoint(
                                    perspectivePoints.pointRightTop,
                                    viewPerspective.pointRightTop,
                                    viewPerspective.safeRectRightTop()
                            )
                        }

                        distance(viewPerspective.pointRightBottom, screenPoint) < minDistance -> {
                            startEditPoint(
                                    perspectivePoints.pointRightBottom,
                                    viewPerspective.pointRightBottom,
                                    viewPerspective.safeRectRightBottom()
                            )
                        }
                    }

                    if (null != trackedPoint) onEditStart?.invoke()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val trackedPoint = this.trackedPoint

                if (null != trackedPoint) {
                    transform.set(bitmap.width, bitmap.height, viewRect)

                    val dx = if (POINT_EDIT_DIRECTION_VERTICAL == pointEditDirection) 0f else event.x - trackedOldPosition.x
                    val dy = if (POINT_EDIT_DIRECTION_HORIZONTAL == pointEditDirection) 0f else event.y - trackedOldPosition.y

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
package com.dan.perspective

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import androidx.core.graphics.contains
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

    //Point edit or first point of line edit
    private var trackedPoint: PointF? = null
    private var trackedViewPoint = PointF()
    private var trackedAllowedRect = RectF()
    private var trackedMovedAlongLine: LineF? = null
    private var trackedMovedAlongLineHorizontal = true

    //Second point of line edit
    private var trackedPoint2: PointF? = null
    private var trackedViewPoint2 = PointF()
    private var trackedAllowedRect2 = RectF()
    private var trackedMovedAlongLine2: LineF? = null

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

        drawPoint(
                viewPerspective.pointLeftTop,
                canvas,
                paint,
                perspectivePoints.pointLeftTop == trackedPoint || perspectivePoints.pointLeftTop == trackedPoint2)
        drawPoint(
                viewPerspective.pointLeftBottom,
                canvas,
                paint,
                perspectivePoints.pointLeftBottom == trackedPoint || perspectivePoints.pointLeftBottom == trackedPoint2)
        drawPoint(
                viewPerspective.pointRightTop,
                canvas,
                paint,
                perspectivePoints.pointRightTop == trackedPoint || perspectivePoints.pointRightTop == trackedPoint2)
        drawPoint(
                viewPerspective.pointRightBottom,
                canvas,
                paint,
                perspectivePoints.pointRightBottom == trackedPoint || perspectivePoints.pointRightBottom == trackedPoint2)
    }

    private fun distance( pointA: PointF, pointB: PointF ): Float =
            PointF.length( pointA.x - pointB.x, pointA.y - pointB.y )

    private fun startEdit(
            trackedPoint: PointF,
            trackedViewPoint: PointF,
            allowedRect: RectF,
            moveAlongLine: LineF? = null,
            lineIsHorizontal: Boolean = true,
            trackedPoint2: PointF? = null,
            trackedViewPoint2: PointF? = null,
            allowedRect2: RectF? = null,
            moveAlongLine2: LineF? = null
    ) {
        this.trackedPoint = trackedPoint
        this.trackedViewPoint.set( trackedViewPoint )
        this.trackedAllowedRect.set( allowedRect )
        this.trackedMovedAlongLine = moveAlongLine
        this.trackedMovedAlongLineHorizontal = lineIsHorizontal

        if (null != trackedPoint2 && null != trackedViewPoint2 && null != allowedRect2) {
            this.trackedPoint2 = trackedPoint2
            this.trackedViewPoint2.set( trackedViewPoint2 )
            this.trackedAllowedRect2.set( allowedRect2 )
            this.trackedMovedAlongLine2 = moveAlongLine2
        } else {
            this.trackedPoint2 = null
        }
    }

    private fun calculateNewViewPoint(
            dx: Float,
            dy: Float,
            viewPoint: PointF,
            allowedRect: RectF,
            moveAlongLine: LineF?,
            moveAlongLineHorizontal: Boolean
    ): PointF? {
        val newViewPoint = PointF(viewPoint.x + dx, viewPoint.y + dy )

        if (null != moveAlongLine) {
            if (moveAlongLineHorizontal) {
                newViewPoint.y = moveAlongLine.segmentDeltaY * ( newViewPoint.x - moveAlongLine.from.x ) / moveAlongLine.segmentDeltaX + moveAlongLine.from.y
            } else {
                newViewPoint.x = moveAlongLine.segmentDeltaX * ( newViewPoint.y - moveAlongLine.from.y ) / moveAlongLine.segmentDeltaY + moveAlongLine.from.x
            }
        }

        return if ( allowedRect.contains(newViewPoint) ) newViewPoint else null
    }

    private fun getMoveAlongLine( point: PointF, pointHorizontal: PointF, pointVertical: PointF ): LineF? {
        if (POINT_EDIT_DIRECTION_ALL == pointEditDirection) return null
        if (POINT_EDIT_DIRECTION_HORIZONTAL == pointEditDirection) return LineF(
                PointF(point.x, point.y),
                PointF(pointHorizontal.x, pointHorizontal.y)
        )

        return LineF(
                PointF(point.x, point.y),
                PointF(pointVertical.x, pointVertical.y)
        )
    }

    private fun handleActionDown( event: MotionEvent, bitmap: Bitmap ): Boolean {
        if (null != trackedPoint) return true

        transform.set(bitmap.width, bitmap.height, viewRect)

        val screenPoint = PointF(event.x, event.y)
        val viewPerspective = transform.mapToView(perspectivePoints)
        val minDistance = dpToPixels(MIN_POINT_DISTANCE_TO_TRACK)
        val allowEditLineHorizontal = POINT_EDIT_DIRECTION_ALL == pointEditDirection || POINT_EDIT_DIRECTION_HORIZONTAL == pointEditDirection
        val allowEditLineVertical = POINT_EDIT_DIRECTION_ALL == pointEditDirection || POINT_EDIT_DIRECTION_VERTICAL == pointEditDirection

        trackedOldPosition.set(event.x, event.y)

        when {
            distance(viewPerspective.pointLeftTop, screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.pointLeftTop,
                        viewPerspective.pointLeftTop,
                        viewPerspective.safeRectLeftTop(),
                        getMoveAlongLine( viewPerspective.pointLeftTop, viewPerspective.pointRightTop, viewPerspective.pointLeftBottom ),
                        pointEditDirection == POINT_EDIT_DIRECTION_HORIZONTAL
                )
            }

            distance(viewPerspective.pointLeftBottom, screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.pointLeftBottom,
                        viewPerspective.pointLeftBottom,
                        viewPerspective.safeRectLeftBottom(),
                        getMoveAlongLine( viewPerspective.pointLeftBottom, viewPerspective.pointRightBottom, viewPerspective.pointLeftTop ),
                        pointEditDirection == POINT_EDIT_DIRECTION_HORIZONTAL
                )
            }

            distance(viewPerspective.pointRightTop, screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.pointRightTop,
                        viewPerspective.pointRightTop,
                        viewPerspective.safeRectRightTop(),
                        getMoveAlongLine( viewPerspective.pointRightTop, viewPerspective.pointLeftTop, viewPerspective.pointRightBottom ),
                        pointEditDirection == POINT_EDIT_DIRECTION_HORIZONTAL
                )
            }

            distance(viewPerspective.pointRightBottom, screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.pointRightBottom,
                        viewPerspective.pointRightBottom,
                        viewPerspective.safeRectRightBottom(),
                        getMoveAlongLine( viewPerspective.pointRightBottom, viewPerspective.pointLeftBottom, viewPerspective.pointRightTop ),
                        pointEditDirection == POINT_EDIT_DIRECTION_HORIZONTAL
                )
            }

            allowEditLineVertical && viewPerspective.lineTop.distanceFrom(screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.lineTop.from,
                        viewPerspective.lineTop.from,
                        viewPerspective.safeRectLeftTop(),
                        viewPerspective.lineLeft.clone(),
                        false,
                        perspectivePoints.lineTop.to,
                        viewPerspective.lineTop.to,
                        viewPerspective.safeRectRightTop(),
                        viewPerspective.lineRight.clone()
                )
            }

            allowEditLineVertical && viewPerspective.lineBottom.distanceFrom(screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.lineBottom.from,
                        viewPerspective.lineBottom.from,
                        viewPerspective.safeRectLeftBottom(),
                        viewPerspective.lineLeft.clone(),
                        false,
                        perspectivePoints.lineBottom.to,
                        viewPerspective.lineBottom.to,
                        viewPerspective.safeRectRightBottom(),
                        viewPerspective.lineRight.clone()
                )
            }

            allowEditLineHorizontal && viewPerspective.lineLeft.distanceFrom(screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.lineLeft.from,
                        viewPerspective.lineLeft.from,
                        viewPerspective.safeRectLeftTop(),
                        viewPerspective.lineTop.clone(),
                        true,
                        perspectivePoints.lineLeft.to,
                        viewPerspective.lineLeft.to,
                        viewPerspective.safeRectLeftBottom(),
                        viewPerspective.lineBottom.clone()
                )
            }

            allowEditLineHorizontal && viewPerspective.lineRight.distanceFrom(screenPoint) < minDistance -> {
                startEdit(
                        perspectivePoints.lineRight.from,
                        viewPerspective.lineRight.from,
                        viewPerspective.safeRectRightTop(),
                        viewPerspective.lineTop.clone(),
                        true,
                        perspectivePoints.lineRight.to,
                        viewPerspective.lineRight.to,
                        viewPerspective.safeRectRightBottom(),
                        viewPerspective.lineBottom.clone()
                )
            }
        }

        if (null != trackedPoint) {
            onEditStart?.invoke()
            return true
        }

        return false
    }

    private fun handleActionMove(event: MotionEvent, bitmap: Bitmap): Boolean {
        val trackedPoint = this.trackedPoint ?: return false

        transform.set(bitmap.width, bitmap.height, viewRect)

        val dx = event.x - trackedOldPosition.x
        val dy = event.y - trackedOldPosition.y

        trackedOldPosition.set(event.x, event.y)

        val newTrackedViewPoint = calculateNewViewPoint(
                dx,
                dy,
                trackedViewPoint,
                trackedAllowedRect,
                trackedMovedAlongLine,
                trackedMovedAlongLineHorizontal
        ) ?: return true

        val trackedPoint2 = this.trackedPoint2
        if (null != trackedPoint2) {
            val newTrackedViewPoint2 = calculateNewViewPoint(
                    dx,
                    dy,
                    trackedViewPoint2,
                    trackedAllowedRect2,
                    trackedMovedAlongLine2,
                    trackedMovedAlongLineHorizontal
            ) ?: return true

            trackedViewPoint2.set(newTrackedViewPoint2)
            trackedPoint2.set(transform.mapToBitmap(trackedViewPoint2))
        }

        trackedViewPoint.set(newTrackedViewPoint)
        trackedPoint.set(transform.mapToBitmap(trackedViewPoint))

        invalidate()
        onPerspectiveChanged?.invoke()

        return true
    }

    private fun handleActionUp(): Boolean {
        if (null != trackedPoint) {
            trackedPoint = null
            trackedPoint2 = null
            onEditEnd?.invoke()
            return true
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (null == event) return true
        val bitmap = super.getBitmap() ?: return true

        when( event.action ) {
            MotionEvent.ACTION_DOWN -> if (handleActionDown(event, bitmap)) return true
            MotionEvent.ACTION_MOVE -> if (handleActionMove(event, bitmap)) return true
            MotionEvent.ACTION_UP -> if (handleActionUp()) return true
        }

        if (null != trackedPoint) {
            Log.i("EDIT", "Track is ON")
            return true
        }

        return super.onTouchEvent(event)
    }
}
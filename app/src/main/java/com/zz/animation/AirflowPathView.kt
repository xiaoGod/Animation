package com.zz.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Three-path airflow direction view.
 *
 * The three start dots are fixed. Drag near the dots to preview a new center
 * target inside a 150dp radius, then release to commit it as the active airflow.
 */
class AirflowPathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class InteractionState {
        Idle,
        Dragging,
        Committing
    }

    private class PathSpec {
        val path = Path()
        val pathMeasure = PathMeasure()
        var length = 0f
        var startX = 0f
        var startY = 0f
        var endX = 0f
        var endY = 0f
    }

    private val density = resources.displayMetrics.density
    private val dragRadius = 150f * density
    private val topRangePadding = 16f * density
    private val startSpacing = 12f * density
    private val hitRadius = 36f * density
    private val pathHitDistance = 24f * density
    private val endpointHitRadius = 32f * density
    private val effectiveDragDistance = 8f * density
    private val maxSideSpread = 42f * density
    private val pathHitSamples = 24

    private var animationDuration = 1600L
    private var pathColor = Color.parseColor("#7DEBFF")
    private val arrowStartScale = 0.60f
    private val arrowEndScale = 1.0f
    private val movingArrowVisibleProgress = 0.92f

    private var lineWidth = 2.0f * density
    private var previewLineWidth = 1.5f * density
    private var arrowSize = 14.0f * density
    private var endpointArrowOffset = 5.0f * density
    private var dotRadius = 3.2f * density

    private val realPaths = arrayOf(PathSpec(), PathSpec(), PathSpec())
    private val previewPaths = arrayOf(PathSpec(), PathSpec(), PathSpec())

    private val origin = PointF()
    private val currentTarget = PointF()
    private val previewTarget = PointF()
    private val downPoint = PointF()
    private val startDownTarget = PointF()

    private val arrowPath = Path()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)
    private val hitPos = FloatArray(2)

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private var airflowAnimator: ValueAnimator? = null
    private var commitAnimator: ValueAnimator? = null
    private var globalProgress = 0f
    private var commitProgress = 1f
    private var state = InteractionState.Idle
    private var hasGeometry = false
    private var hasValidDrag = false

    init {
        updatePaintSettings()
        buildArrowPath()
        isClickable = true
    }

    private fun updatePaintSettings() {
        pathPaint.color = pathColor
        pathPaint.strokeWidth = lineWidth
        pathPaint.alpha = 210

        glowPaint.color = pathColor
        glowPaint.strokeWidth = lineWidth * 2.2f
        glowPaint.alpha = 35

        arrowPaint.color = pathColor
        arrowPaint.alpha = 255

        dotPaint.color = pathColor
        dotPaint.alpha = 230

        rangePaint.color = pathColor
        rangePaint.alpha = 42
    }

    private fun buildArrowPath() {
        arrowPath.reset()
        val tailX = -arrowSize
        val notchX = -arrowSize * 0.42f
        val halfHeight = arrowSize * 0.34f

        arrowPath.moveTo(0f, 0f)
        arrowPath.lineTo(tailX, -halfHeight)
        arrowPath.lineTo(notchX, 0f)
        arrowPath.lineTo(tailX, halfHeight)
        arrowPath.close()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val preferredOriginY = max(h * 0.22f, dragRadius + topRangePadding)
        val maxOriginY = h - dragRadius - topRangePadding
        val originY = if (maxOriginY > dragRadius) {
            preferredOriginY.coerceAtMost(maxOriginY)
        } else {
            h * 0.5f
        }
        origin.set(w * 0.5f, originY)

        if (!hasGeometry) {
            currentTarget.set(origin.x, origin.y + dragRadius * 0.76f)
            clampTarget(currentTarget.x, currentTarget.y, currentTarget)
            hasGeometry = true
        } else {
            clampTarget(currentTarget.x, currentTarget.y, currentTarget)
        }

        buildAirflowPaths(currentTarget, realPaths)
        buildAirflowPaths(currentTarget, previewPaths)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasGeometry || realPaths[0].length <= 0f) return

        drawStartDots(canvas)

        when (state) {
            InteractionState.Idle -> drawRealAirflow(canvas, 1f, drawMovingArrows = true)
            InteractionState.Dragging -> {
                drawRealAirflow(canvas, 0.32f, drawMovingArrows = false)
                drawDragRange(canvas, 1f)
                drawPreviewAirflow(canvas, 1f)
            }
            InteractionState.Committing -> {
                val previewWeight = 1f - commitProgress
                if (previewWeight > 0f) {
                    drawDragRange(canvas, previewWeight)
                    drawPreviewAirflow(canvas, previewWeight)
                }
                drawRealAirflow(canvas, 0.35f + 0.65f * commitProgress, drawMovingArrows = commitProgress > 0.35f)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasGeometry) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInsideDragHotspot(event.x, event.y)) return false

                parent?.requestDisallowInterceptTouchEvent(true)
                commitAnimator?.cancel()
                state = InteractionState.Dragging
                hasValidDrag = false
                downPoint.set(event.x, event.y)
                startDownTarget.set(currentTarget.x, currentTarget.y)
                previewTarget.set(currentTarget.x, currentTarget.y)
                buildAirflowPaths(previewTarget, previewPaths)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state != InteractionState.Dragging) return false

                clampTarget(event.x, event.y, previewTarget)
                buildAirflowPaths(previewTarget, previewPaths)
                val dragDistance = hypot(event.x - downPoint.x, event.y - downPoint.y)
                hasValidDrag = hasValidDrag || dragDistance >= effectiveDragDistance
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state != InteractionState.Dragging) return false

                parent?.requestDisallowInterceptTouchEvent(false)
                if (hasValidDrag) {
                    commitPreviewDirection()
                    performClick()
                } else {
                    cancelPreviewDirection()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (state == InteractionState.Dragging || state == InteractionState.Committing) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    cancelPreviewDirection()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun isInsideStartHotspot(x: Float, y: Float): Boolean {
        return hypot(x - origin.x, y - origin.y) <= hitRadius
    }

    private fun isInsideDragHotspot(x: Float, y: Float): Boolean {
        return isInsideStartHotspot(x, y) ||
            isNearAnyEndpoint(x, y) ||
            isNearAnyPath(x, y)
    }

    private fun isNearAnyEndpoint(x: Float, y: Float): Boolean {
        for (spec in realPaths) {
            if (hypot(x - spec.endX, y - spec.endY) <= endpointHitRadius) {
                return true
            }
        }
        return false
    }

    private fun isNearAnyPath(x: Float, y: Float): Boolean {
        for (spec in realPaths) {
            if (isNearPath(spec, x, y)) return true
        }
        return false
    }

    private fun isNearPath(spec: PathSpec, x: Float, y: Float): Boolean {
        if (spec.length <= 0f) return false

        var lastX = spec.startX
        var lastY = spec.startY
        var minDistance = Float.MAX_VALUE

        for (i in 1..pathHitSamples) {
            val distance = spec.length * i / pathHitSamples
            if (spec.pathMeasure.getPosTan(distance, hitPos, null)) {
                minDistance = min(minDistance, distanceToSegment(x, y, lastX, lastY, hitPos[0], hitPos[1]))
                lastX = hitPos[0]
                lastY = hitPos[1]
            }
        }

        return minDistance <= pathHitDistance
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared <= 0f) return hypot(px - ax, py - ay)

        val t = (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0f, 1f)
        val closestX = ax + abx * t
        val closestY = ay + aby * t
        return hypot(px - closestX, py - closestY)
    }

    private fun clampTarget(x: Float, y: Float, out: PointF) {
        val dx = x - origin.x
        val dy = y - origin.y
        val distance = hypot(dx, dy)
        if (distance <= dragRadius) {
            out.set(x, y)
        } else if (distance > 0f) {
            val scale = dragRadius / distance
            out.set(origin.x + dx * scale, origin.y + dy * scale)
        } else {
            out.set(origin.x, origin.y + dragRadius * 0.76f)
        }
    }

    private fun commitPreviewDirection() {
        currentTarget.set(previewTarget.x, previewTarget.y)
        buildAirflowPaths(currentTarget, realPaths)
        commitProgress = 0f
        globalProgress = 0f
        state = InteractionState.Committing

        commitAnimator?.cancel()
        commitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener {
                commitProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    commitProgress = 1f
                    state = InteractionState.Idle
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    commitProgress = 1f
                }
            })
            start()
        }
    }

    private fun cancelPreviewDirection() {
        commitAnimator?.cancel()
        state = InteractionState.Idle
        commitProgress = 1f
        previewTarget.set(startDownTarget.x, startDownTarget.y)
        buildAirflowPaths(currentTarget, realPaths)
        buildAirflowPaths(currentTarget, previewPaths)
        invalidate()
    }

    private fun buildAirflowPaths(target: PointF, specs: Array<PathSpec>) {
        val vx = target.x - origin.x
        val vy = target.y - origin.y
        var length = hypot(vx, vy)
        var dirX = 0f
        var dirY = 1f
        if (length > 1f) {
            dirX = vx / length
            dirY = vy / length
        } else {
            length = dragRadius * 0.76f
        }

        val sideLength = max(1f, length * 0.86f - min(16f * density, length * 0.08f))
        val sideSpread = min(maxSideSpread, length * 0.22f)
        val minEndGap = startSpacing * 1.4f

        val centerEndX = origin.x + dirX * length
        val centerEndY = origin.y + dirY * length
        val normalX = -dirY
        val normalY = dirX
        val leftNormalSign = if (normalX < 0f || (abs(normalX) < 0.001f && normalY < 0f)) 1f else -1f
        val leftOutX = normalX * leftNormalSign
        val leftOutY = normalY * leftNormalSign
        val rightOutX = -leftOutX
        val rightOutY = -leftOutY
        val sideBaseX = origin.x + dirX * sideLength
        val sideBaseY = origin.y + dirY * sideLength
        val rawLeftEndX = sideBaseX + leftOutX * sideSpread
        val rawRightEndX = sideBaseX + rightOutX * sideSpread
        val leftEndX = min(rawLeftEndX, centerEndX - minEndGap)
        val rightEndX = max(rawRightEndX, centerEndX + minEndGap)
        val leftSpreadX = leftEndX - sideBaseX
        val leftSpreadY = leftOutY * sideSpread
        val rightSpreadX = rightEndX - sideBaseX
        val rightSpreadY = rightOutY * sideSpread

        buildSidePath(
            specs[0],
            origin.x - startSpacing,
            origin.y,
            sideBaseX,
            sideBaseY,
            leftSpreadX,
            leftSpreadY
        )
        buildCenterPath(specs[1], origin.x, origin.y, centerEndX, centerEndY)
        buildSidePath(
            specs[2],
            origin.x + startSpacing,
            origin.y,
            sideBaseX,
            sideBaseY,
            rightSpreadX,
            rightSpreadY
        )
    }

    private fun buildSidePath(
        spec: PathSpec,
        startX: Float,
        startY: Float,
        sideBaseX: Float,
        sideBaseY: Float,
        spreadX: Float,
        spreadY: Float
    ) {
        val baseDx = sideBaseX - startX
        val baseDy = sideBaseY - startY
        val endX = sideBaseX + spreadX
        val endY = sideBaseY + spreadY

        spec.startX = startX
        spec.startY = startY
        spec.endX = endX
        spec.endY = endY
        spec.path.reset()
        spec.path.moveTo(startX, startY)
        spec.path.cubicTo(
            startX + baseDx * 0.30f + spreadX * 0.08f,
            startY + baseDy * 0.32f + spreadY * 0.08f,
            startX + baseDx * 0.70f + spreadX * 0.55f,
            startY + baseDy * 0.70f + spreadY * 0.55f,
            endX,
            endY
        )
        spec.pathMeasure.setPath(spec.path, false)
        spec.length = spec.pathMeasure.length
    }

    private fun buildCenterPath(spec: PathSpec, startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = endX - startX
        val dy = endY - startY
        val length = max(1f, hypot(dx, dy))
        val centerBend = min(4f * density, length * 0.018f)

        spec.startX = startX
        spec.startY = startY
        spec.endX = endX
        spec.endY = endY
        spec.path.reset()
        spec.path.moveTo(startX, startY)
        spec.path.cubicTo(
            startX + dx * 0.32f + centerBend,
            startY + dy * 0.32f,
            startX + dx * 0.72f + centerBend * 0.4f,
            startY + dy * 0.72f,
            endX,
            endY
        )
        spec.pathMeasure.setPath(spec.path, false)
        spec.length = spec.pathMeasure.length
    }

    private fun drawStartDots(canvas: Canvas) {
        dotPaint.color = pathColor
        dotPaint.alpha = when (state) {
            InteractionState.Idle -> 230
            InteractionState.Dragging -> 255
            InteractionState.Committing -> 230
        }
        canvas.drawCircle(origin.x - startSpacing, origin.y, dotRadius, dotPaint)
        canvas.drawCircle(origin.x, origin.y, dotRadius, dotPaint)
        canvas.drawCircle(origin.x + startSpacing, origin.y, dotRadius, dotPaint)
    }

    private fun drawDragRange(canvas: Canvas, alphaScale: Float) {
        rangePaint.color = pathColor
        rangePaint.alpha = (42 * alphaScale).toInt().coerceIn(0, 255)
        canvas.drawCircle(origin.x, origin.y, dragRadius, rangePaint)
    }

    private fun drawPreviewAirflow(canvas: Canvas, alphaScale: Float) {
        drawPathSet(canvas, previewPaths, pathAlpha = 88 * alphaScale, glowAlpha = 18 * alphaScale, arrowAlpha = 115 * alphaScale)
        drawEndpointArrows(canvas, previewPaths, alpha = 120 * alphaScale, scale = 0.84f)
    }

    private fun drawRealAirflow(canvas: Canvas, alphaScale: Float, drawMovingArrows: Boolean) {
        drawPathSet(canvas, realPaths, pathAlpha = 210 * alphaScale, glowAlpha = 35 * alphaScale, arrowAlpha = 255 * alphaScale)
        drawEndpointArrows(canvas, realPaths, alpha = 255 * alphaScale, scale = arrowEndScale)
        if (drawMovingArrows) {
            drawMovingArrows(canvas, alpha = 255 * alphaScale)
        }
    }

    private fun drawPathSet(
        canvas: Canvas,
        specs: Array<PathSpec>,
        pathAlpha: Float,
        glowAlpha: Float,
        arrowAlpha: Float
    ) {
        glowPaint.color = pathColor
        glowPaint.strokeWidth = lineWidth * 2.2f
        glowPaint.alpha = glowAlpha.toInt().coerceIn(0, 255)
        pathPaint.color = pathColor
        pathPaint.strokeWidth = if (state == InteractionState.Dragging && specs === previewPaths) previewLineWidth else lineWidth
        pathPaint.alpha = pathAlpha.toInt().coerceIn(0, 255)
        arrowPaint.color = pathColor
        arrowPaint.alpha = arrowAlpha.toInt().coerceIn(0, 255)

        for (spec in specs) {
            canvas.drawPath(spec.path, glowPaint)
            canvas.drawPath(spec.path, pathPaint)
        }
    }

    private fun drawEndpointArrows(canvas: Canvas, specs: Array<PathSpec>, alpha: Float, scale: Float) {
        arrowPaint.color = pathColor
        arrowPaint.alpha = alpha.toInt().coerceIn(0, 255)
        for (spec in specs) {
            drawEndpointArrowOnPath(canvas, spec, scale)
        }
    }

    private fun drawMovingArrows(canvas: Canvas, alpha: Float) {
        val localProgress = globalProgress
        if (localProgress > movingArrowVisibleProgress) return

        val pathProgress = localProgress / movingArrowVisibleProgress
        val scale = arrowStartScale + (arrowEndScale - arrowStartScale) * pathProgress
        arrowPaint.color = pathColor
        arrowPaint.alpha = alpha.toInt().coerceIn(0, 255)

        for (spec in realPaths) {
            drawArrowOnPath(canvas, spec, pathProgress, scale)
        }
    }

    private fun drawArrowOnPath(canvas: Canvas, spec: PathSpec, progress: Float, scale: Float) {
        val distance = spec.length * progress
        if (spec.pathMeasure.getPosTan(distance, pos, tan)) {
            val degrees = Math.toDegrees(atan2(tan[1].toDouble(), tan[0].toDouble())).toFloat()

            canvas.save()
            canvas.translate(pos[0], pos[1])
            canvas.rotate(degrees)
            canvas.scale(scale, scale)
            canvas.drawPath(arrowPath, arrowPaint)
            canvas.restore()
        }
    }

    private fun drawEndpointArrowOnPath(canvas: Canvas, spec: PathSpec, scale: Float) {
        if (spec.pathMeasure.getPosTan(spec.length, pos, tan)) {
            val tangentLength = hypot(tan[0], tan[1])
            if (tangentLength <= 0f) return

            val offsetX = tan[0] / tangentLength * endpointArrowOffset
            val offsetY = tan[1] / tangentLength * endpointArrowOffset
            val degrees = Math.toDegrees(atan2(tan[1].toDouble(), tan[0].toDouble())).toFloat()

            canvas.save()
            canvas.translate(pos[0] + offsetX, pos[1] + offsetY)
            canvas.rotate(degrees)
            canvas.scale(scale, scale)
            canvas.drawPath(arrowPath, arrowPaint)
            canvas.restore()
        }
    }

    fun startAnimation() {
        if (airflowAnimator?.isRunning == true) return

        airflowAnimator?.cancel()
        airflowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                globalProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        airflowAnimator?.cancel()
        airflowAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        if (state == InteractionState.Dragging) cancelPreviewDirection()
        commitAnimator?.cancel()
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus && state == InteractionState.Dragging) {
            cancelPreviewDirection()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            if (state == InteractionState.Dragging) cancelPreviewDirection()
            stopAnimation()
        }
    }

    fun setPathColor(color: Int) {
        pathColor = color
        updatePaintSettings()
        invalidate()
    }
}

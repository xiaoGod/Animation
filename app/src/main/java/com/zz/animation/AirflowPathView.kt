package com.zz.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2

/**
 * 极简三路径箭头流动动画 - 稳定版
 *
 * 修复点：
 * 1. 修正 Paint.alpha 被 Paint.color 覆盖导致的视觉异常。
 * 2. 增强 ValueAnimator 初始化与生命周期绑定。
 * 3. 优化 PathMeasure 测量逻辑。
 */
class AirflowPathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- 视觉参数 ---
    private var animationDuration = 1600L
    private var pathColor = Color.parseColor("#7DEBFF")
    private val arrowStartScale = 0.60f
    private val arrowEndScale = 1.0f
    private val movingArrowVisibleProgress = 0.92f

    private val density = resources.displayMetrics.density
    private var lineWidth = 2.0f * density
    private var arrowSize = 14.0f * density

    // --- 画笔复用 ---
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

    // --- 内部数据结构 ---
    private class PathSpec {
        val path = Path()
        val pathMeasure = PathMeasure()
        var length = 0f
    }

    private val paths = arrayOf(
        PathSpec(),   // 左侧
        PathSpec(),   // 中间
        PathSpec()    // 右侧
    )

    private val arrowPath = Path()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    private var animator: ValueAnimator? = null
    private var globalProgress = 0f

    init {
        updatePaintSettings()
        buildArrowPath()
    }

    private fun updatePaintSettings() {
        // 核心修复：alpha 必须在设置颜色之后设定
        pathPaint.color = pathColor
        pathPaint.strokeWidth = lineWidth
        pathPaint.alpha = 210

        glowPaint.color = pathColor
        glowPaint.strokeWidth = lineWidth * 2.2f
        glowPaint.alpha = 35 // 设定轻微透明度

        arrowPaint.color = pathColor
        arrowPaint.alpha = 255
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

        val wf = w.toFloat()
        val hf = h.toFloat()
        val centerX = wf * 0.5f
        val startY = hf * 0.18f
        val startSpacing = wf * 0.035f

        // 三个独立起点同一水平线，路径向终点逐渐展开成外短中长的扇形。
        paths[0].path.apply {
            reset()
            moveTo(centerX - startSpacing, startY)
            cubicTo(wf * 0.44f, hf * 0.30f, wf * 0.41f, hf * 0.46f, wf * 0.38f, hf * 0.60f)
        }
        paths[1].path.apply {
            reset()
            moveTo(centerX, startY)
            cubicTo(wf * 0.49f, hf * 0.34f, wf * 0.51f, hf * 0.52f, wf * 0.50f, hf * 0.68f)
        }
        paths[2].path.apply {
            reset()
            moveTo(centerX + startSpacing, startY)
            cubicTo(wf * 0.56f, hf * 0.30f, wf * 0.59f, hf * 0.46f, wf * 0.62f, hf * 0.60f)
        }

        for (spec in paths) {
            spec.pathMeasure.setPath(spec.path, false)
            spec.length = spec.pathMeasure.length
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 检查是否已测量
        if (paths[0].length <= 0) return

        for (spec in paths) {
            canvas.drawPath(spec.path, glowPaint)
            canvas.drawPath(spec.path, pathPaint)
        }

        for (spec in paths) {
            drawArrowOnPath(canvas, spec, 1.0f, arrowEndScale)
        }

        for (spec in paths) {
            val localProgress = globalProgress
            if (localProgress <= movingArrowVisibleProgress) {
                val pathProgress = localProgress / movingArrowVisibleProgress
                val scale = arrowStartScale + (arrowEndScale - arrowStartScale) * pathProgress
                drawArrowOnPath(canvas, spec, pathProgress, scale)
            }
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

    // --- 动画管理 ---

    fun startAnimation() {
        if (animator?.isRunning == true) return

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                globalProgress = it.animatedValue as Float
                invalidate() // 强制重绘
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimation() else stopAnimation()
    }

    fun setPathColor(color: Int) {
        this.pathColor = color
        updatePaintSettings()
        invalidate()
    }
}

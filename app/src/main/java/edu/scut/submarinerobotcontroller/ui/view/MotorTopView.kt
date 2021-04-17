package edu.scut.submarinerobotcontroller.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.tools.MyColor
import kotlin.math.abs
import kotlin.math.min

class MotorTopView constructor(context: Context, attributeSet: AttributeSet? = null) :
    View(context, attributeSet) {

    private var lastPower = 0f
    var motorPower: Float = 0f
        set(value) {
            field = value
            if (value != lastPower) {
//                debug("绘制MotorView")
                invalidate()
            }
            lastPower = value
        }
    var motorReserve: Boolean = false

    private val OUTER_WIDTH_SIZE: Int
    private val OUTER_HEIGHT_SIZE: Int
    private var realRadius = 0
    private var centerX = 0f
    private var centerY = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measureWidth(widthMeasureSpec)
        val height = measureHeight(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun measureWidth(widthMeasureSpec: Int): Int {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthVal = MeasureSpec.getSize(widthMeasureSpec)
        //处理三种模式
        return if (widthMode == MeasureSpec.EXACTLY) {
            widthVal + paddingLeft + paddingRight
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            OUTER_WIDTH_SIZE
        } else {
            OUTER_WIDTH_SIZE.coerceAtMost(widthVal)
        }
    }

    private fun measureHeight(heightMeasureSpec: Int): Int {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightVal = MeasureSpec.getSize(heightMeasureSpec)
        //处理三种模式
        return if (heightMode == MeasureSpec.EXACTLY) {
            heightVal + paddingTop + paddingBottom
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            OUTER_HEIGHT_SIZE
        } else {
            OUTER_HEIGHT_SIZE.coerceAtMost(heightVal)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        realRadius = min(w, h)
        centerX = (w / 2).toFloat()
        centerY = (h / 2).toFloat()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val motorRate = 0.2f
        val drawPower = if (motorReserve.not()) motorPower else -motorPower

        val motorRadius = realRadius * motorRate / 2
        val motorPaint = Paint()
        motorPaint.style = Paint.Style.FILL_AND_STROKE
        motorPaint.color = MyColor.argb(1f, 0.1f, 0.75f, abs(drawPower) * 0.75f)

        val waterRadius = motorRadius + (abs(drawPower) * (1 - motorRate) / 2f * realRadius) / 2f
        val waterPaint = Paint()
        waterPaint.style = Paint.Style.FILL_AND_STROKE
        val waterColor =
            MyColor.argb(1f, 120 / 255f, 220 / 255f, 255 / 255f)
        waterPaint.shader = RadialGradient(
            centerX,
            centerY,
            waterRadius,
            intArrayOf(waterColor, Color.TRANSPARENT),
            floatArrayOf(0.4f, 1.0f),
            Shader.TileMode.MIRROR
        )

        val drawText =
            if (drawPower > Constant.MotorTopViewEps) "↑" else if (drawPower < -Constant.MotorTopViewEps) "↓" else ""
        val textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL_AND_STROKE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 60f
        val bounds = Rect()
        textPaint.getTextBounds(drawText, 0, drawText.length, bounds)
        val offSet = ((bounds.top + bounds.bottom) / 2).toFloat()

        //前后水流
        canvas.drawCircle(centerX, centerY, waterRadius, waterPaint)

        //马达本体
        canvas.drawCircle(centerX, centerY, motorRadius, motorPaint)

        //文字
        canvas.drawText(drawText, centerX, centerY - offSet, textPaint)
    }

    companion object {
        fun dip2px(context: Context, dpValue: Float): Int {
            val scale = context.resources.displayMetrics.density
            return (dpValue * scale + 0.5f).toInt()
        }
    }

    init {
        OUTER_WIDTH_SIZE = dip2px(context, 200.0f)
        OUTER_HEIGHT_SIZE = dip2px(context, 200.0f)
    }
}
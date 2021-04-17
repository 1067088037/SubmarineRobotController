package edu.scut.submarinerobotcontroller.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import edu.scut.submarinerobotcontroller.tools.MyColor
import edu.scut.submarinerobotcontroller.tools.debug
import kotlin.math.abs

class MotorSideView constructor(context: Context, attributeSet: AttributeSet? = null) :
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
    private var realWidth = 0
    private var realHeight = 0
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
        realWidth = w
        realHeight = h
        centerX = (realWidth / 2).toFloat()
        centerY = (realHeight / 2).toFloat()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val motorHeightRate = 0.12f
        val drawPower = if (motorReserve.not()) motorPower else -motorPower
//        val drawPower = 0.5f

        val motorWidth = realWidth
        val motorHeight = realHeight * motorHeightRate
        val motorPaint = Paint()
        motorPaint.style = Paint.Style.FILL_AND_STROKE
        motorPaint.color = MyColor.argb(1f, 0.1f, 0.75f, abs(drawPower) * 0.75f)

        val waterBorder = 36f
        val waterWidth = motorWidth - 12f
        val waterHeight =
            motorHeight + (abs(drawPower) * (1 - motorHeightRate) / 2f * realHeight)
        val waterPaint = Paint()
        waterPaint.style = Paint.Style.FILL_AND_STROKE
        val motorRectPoint = floatArrayOf(
            centerX - motorWidth / 2,
            centerY - motorHeight / 2,
            centerX + motorWidth / 2,
            centerY + motorHeight / 2
        )
        val waterRectPoint = floatArrayOf(
            centerX - waterWidth / 2,
            if (drawPower > 0) centerY else if (drawPower < 0) centerY - waterHeight else centerY,
            centerX + waterWidth / 2,
            if (drawPower > 0) centerY + waterHeight else if (drawPower < 0) centerY else centerY
        )
        val waterColor =
            MyColor.argb(0.2f + abs(drawPower) * 0.8f, 120 / 255f, 220 / 255f, 255 / 255f)
        waterPaint.shader = LinearGradient(
            centerX, waterRectPoint[1], centerX, waterRectPoint[3],
            if (drawPower < 0) Color.TRANSPARENT else waterColor,
            if (drawPower < 0) waterColor else Color.TRANSPARENT,
            Shader.TileMode.MIRROR
        )

        //前后水流
        canvas.drawRoundRect(
            RectF(waterRectPoint[0], waterRectPoint[1], waterRectPoint[2], waterRectPoint[3]),
            waterBorder, waterBorder, waterPaint
        )

        //马达本体
        canvas.drawRoundRect(
            RectF(motorRectPoint[0], motorRectPoint[1], motorRectPoint[2], motorRectPoint[3]),
            6f, 6f, motorPaint
        )
    }

    companion object {
        fun dip2px(context: Context, dpValue: Float): Int {
            val scale = context.resources.displayMetrics.density
            return (dpValue * scale + 0.5f).toInt()
        }
    }

    init {
        OUTER_WIDTH_SIZE = dip2px(context, 40.0f)
        OUTER_HEIGHT_SIZE = dip2px(context, 160.0f)
    }
}
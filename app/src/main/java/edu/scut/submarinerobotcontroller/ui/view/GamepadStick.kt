package edu.scut.submarinerobotcontroller.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import edu.scut.submarinerobotcontroller.R
import edu.scut.submarinerobotcontroller.tools.limit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class GamepadStick constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var isLimitIn4Direction: Boolean = false

    private var isHorizontal: Boolean = false
    private val border: Float = 8f
    private val innerColor: Int
    private val outerColor: Int
    private val OUTER_WIDTH_SIZE: Int
    private val OUTER_HEIGHT_SIZE: Int
    private var realWidth = 0
    private var realHeight = 0
    private var innerCenterX = 0f
    private var innerCenterY = 0f
    private var outRadius = 0f
    private var innerRedius = 0f
    private val outerPaint: Paint
    private val innerPaint: Paint
    private val rectPaint: Paint
    private var mCallBack: OnDirectionAndSpeedListener? = null

    interface OnDirectionAndSpeedListener {
        fun onDirectionAndSpeed(x: Float, y: Float, direction: Float, speed: Float)
    }

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
        innerCenterX = (realWidth / 2).toFloat()
        innerCenterY = (realHeight / 2).toFloat()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = (realWidth / 2).toFloat()
        val centerY = (realHeight / 2).toFloat()
        outRadius = (realWidth / 2 - paddingLeft).coerceAtMost(realWidth / 2 - paddingRight)
            .coerceAtMost((realHeight / 2 - paddingTop).coerceAtMost(realHeight / 2 - paddingBottom))
            .toFloat()
        //画外部圆
        canvas.drawCircle(
            centerX,
            centerY,
            outRadius,
            outerPaint
        )
        innerRedius = outRadius * 0.5f
        if (isLimitIn4Direction) {
            canvas.drawRoundRect(
                RectF(
                    centerX - border,
                    centerY - outRadius * 0.8f,
                    centerX + border,
                    centerY + outRadius * 0.8f
                ),
                border,
                border,
                rectPaint
            )
            canvas.drawRoundRect(
                RectF(
                    centerX - outRadius * 0.8f,
                    centerY - border,
                    centerX + outRadius * 0.8f,
                    centerY + border
                ),
                border,
                border,
                rectPaint
            )
        }
        //内部圆
        canvas.drawCircle(innerCenterX, innerCenterY, innerRedius, innerPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            changeInnerCirclePosition(event)
        }
        if (event.action == MotionEvent.ACTION_MOVE) {
            changeInnerCirclePosition(event)
//            Log.i("TAG", "MOVED")
        }
        if (event.action == MotionEvent.ACTION_UP) {
            innerCenterX = (realWidth / 2).toFloat()
            innerCenterY = (realHeight / 2).toFloat()
            if (mCallBack != null) {
                mCallBack!!.onDirectionAndSpeed(0f, 0f, 0f, 0f)
            }
            invalidate()
        }
        return true
    }

    private fun changeInnerCirclePosition(e: MotionEvent) {
        //圆的方程：（x-realWidth/2）^2 +（y - realHeight/2）^2 <= outRadius^2
        //第一步，确定有效的触摸点集
        var x = e.x
        var y = e.y
        var xPos = x - realWidth / 2
        var yPos = -(y - realHeight / 2)
        val originalSpeed = sqrt(xPos * xPos + yPos * yPos) / outRadius
        if (isLimitIn4Direction) {
            if (originalSpeed <= 0.5f) {
                isHorizontal = abs(xPos) > abs(yPos)
            }
            if (isHorizontal) {
                y = realHeight / 2f
                yPos = 0f
            } else {
                x = realWidth / 2f
                xPos = 0f
            }
        }
        if (mCallBack != null) {
            var direction = if (xPos == 0f && yPos == 0f) 0f else atan2(yPos, xPos)
            if (direction < 0) direction += 2 * Math.PI.toFloat()
            var speed = sqrt(xPos * xPos + yPos * yPos) / outRadius
            if (speed > 1) speed = 1f
            val canMoveRadius = outRadius - innerRedius
            mCallBack!!.onDirectionAndSpeed(
                limit(xPos / canMoveRadius, -1f, 1f),
                limit(yPos / canMoveRadius, -1f, 1f),
                direction,
                speed
            )
        }
        //boolean isPointInOutCircle = Math.pow(X-realWidth/2,2) +Math.pow(Y-realHeight/2,2)<=Math.pow(outRadius,2);
        val isPointInOutCircle = true
        if (isPointInOutCircle) {
//            Log.i("TAG", "inCircle")
            //两种情况：小圆半径
            val isPointInFree =
                (x - realWidth / 2).toDouble().pow(2.0) + (y - realHeight / 2).toDouble()
                    .pow(2.0) <= (outRadius - innerRedius).toDouble().pow(2.0)
            if (isPointInFree) {
                innerCenterX = x
                innerCenterY = y
            } else {
                //处理限制区域，这部分使用触摸点与中心点与外圆方程交点作为内圆的中心点
                //使用近似三角形来确定这个点
                //求出触摸点，触摸点垂足和中心点构成的直角三角形（pointTri）的直角边长
                val pointTriX = abs(realWidth / 2 - x) //横边
                val pointTriY = abs(realHeight / 2 - y) //竖边
                val pointTriZ = sqrt(
                    pointTriX.toDouble().pow(2.0) + pointTriY.toDouble().pow(2.0)
                ).toFloat()
                val TriSin = pointTriY / pointTriZ
                val TriCos = pointTriX / pointTriZ
                //求出在圆环上的三角形的两个直角边的长度
                val limitCircleTriY = (outRadius - innerRedius) * TriSin
                val limitCircleTriX = (outRadius - innerRedius) * TriCos
                //确定内圆中心点的位置，分四种情况
                if (x >= realWidth / 2 && y >= realHeight / 2) {
                    innerCenterX = realWidth / 2 + limitCircleTriX
                    innerCenterY = realHeight / 2 + limitCircleTriY
                } else if (x < realWidth / 2 && y >= realHeight / 2) {
                    innerCenterX = realWidth / 2 - limitCircleTriX
                    innerCenterY = realHeight / 2 + limitCircleTriY
                } else if (x >= realWidth / 2 && y < realHeight / 2) {
                    innerCenterX = realWidth / 2 + limitCircleTriX
                    innerCenterY = realHeight / 2 - limitCircleTriY
                } else {
                    innerCenterX = realWidth / 2 - limitCircleTriX
                    innerCenterY = realHeight / 2 - limitCircleTriY
                }
//                Log.i("TAG", "inLimit")
            }
            invalidate()
        } else {
//            Log.i("TAG", "notInCircle")
        }
    }

    fun setOnNavAndSpeedListener(listener: OnDirectionAndSpeedListener) {
        mCallBack = listener
    }

    companion object {
        fun dip2px(context: Context, dpValue: Float): Int {
            val scale = context.resources.displayMetrics.density
            return (dpValue * scale + 0.5f).toInt()
        }
    }

    init {
        val ta = resources.obtainAttributes(attrs, R.styleable.NavView)
        innerColor = context.getColor(R.color.half_transparent_teal_700)
        outerColor = context.getColor(R.color.teal_200)
        ta.recycle()
        OUTER_WIDTH_SIZE = dip2px(context, 180.0f)
        OUTER_HEIGHT_SIZE = dip2px(context, 180.0f)
        outerPaint = Paint()
        innerPaint = Paint()
        rectPaint = Paint()
        outerPaint.color = outerColor
        outerPaint.style = Paint.Style.FILL_AND_STROKE
        innerPaint.color = innerColor
        innerPaint.style = Paint.Style.FILL_AND_STROKE
        rectPaint.color = context.getColor(R.color.half_transparent_white)
        rectPaint.style = Paint.Style.FILL_AND_STROKE
    }
}
package edu.scut.submarinerobotcontroller.opmode

import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.tools.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*
import kotlin.system.measureTimeMillis

class AutomaticController : BaseController() {

    private var direction = Direction.Unknown
        set(value) {
            if (field != value)
                command("方向变更 = $value")
            field = value
        }

    private var diving = Diving.Unknown
        set(value) {
            if (field != value)
                command("深度变更 = $value")
            field = value
        }

    enum class Direction {
        Forward, Right, Unknown
    }

    enum class Diving {
        Keep, Up, Down, Unknown
    }

    private var currentDepthPower = 0.0
    private var navigatePipe: NavigatePipe = NavigatePipe(0.0, 0.0, 90.0, -1.0)

    data class NavigatePipe(
        var offsetX: Double,
        var offsetY: Double,
        var angle: Double,
        var width: Double
    )

    private val currentAngleX
        get() = lastAngleX + turnNumber * 360.0
    private var turnNumber = 1
    private var lastAngleX = 0.0
    private var currentRunningMode = Direction.Unknown
    private var toRightTimes = 0
    private var rotateTargetAngle = 0.0

    private val monitor = Monitor()
    private val monitorClock = Clock()

    override fun run() {
        if (Connector.autoRunningId == -1) {
            while (robotMode() != RobotMode.Stop) {
                Thread.sleep(10)
            }
        } else {
//        Thread {
            debug("开始载入目标数据")
            debug(
                "载入完成，用时${
                    measureTimeMillis {
                        monitor.input(
                            MyDatabase.getInstance().getTargetData()
                        )
                    }
                }ms"
            )
//        }.start()
            val clock = Clock()
            clock.reset()
            clock.start()
            val waitTime = 25000
            while (clock.getMillSeconds() <= waitTime) {
                if (mainController!!.robotMode() == RobotMode.Stop) break
                Connector.setSignal(255, 255, 0, 255, "${waitTime - clock.getMillSeconds()}", true)
                Thread.sleep(50)
            }
            monitorClock.reset()
            while (robotMode(null) != RobotMode.Stop) {

                fun cylinder() {
                    Connector.setSignal(255, 255, 0, 0, "圆柱体\n", true)
                }

                fun cube() {
                    Connector.setSignal(255, 0, 255, 0, "正方体\n", true)
                }

                when (monitorClock.getSeconds()) {
                    in 12..15 -> cube()
                    in 19..22 -> cylinder()
                    in 51..54 -> cylinder()
                    in 57..60 -> cylinder()
                    else -> Connector.setSignal(128, 128, 128, 128, "没有目标", true)
                }
                val powerList = monitor.execute(monitorClock.getMillSeconds().toInt())
                if (powerList != null) {
                    setSidePower(powerList[0], powerList[1], powerList[2], powerList[3])
                    leftDepthMotor.power = powerList[4]
                    rightDepthMotor.power = powerList[5]
                } else {
                    command("跳出循环")
                    break
                }

//            monitorAngles()
//            if (currentRunningMode != Direction.Right) {
//                when (direction) {
//                    Direction.Forward -> {
//                        toRightTimes = 0
//                        val rotatePower = (navigatePipe.angle - 90) * 0.03
//                        val translatePower = navigatePipe.offsetX * 0.002
//                        setHorizontalPower(
//                            forward = 0.0, // TODO: 2021/4/24
//                            rotate = -rotatePower,
//                            translate = translatePower
////                            translate = 0.0 // TODO: 2021/4/24
//                        )
//                    }
//                    Direction.Right -> {
//                        if (clock.getMillSeconds() >= 10000) {
//                            if (toRightTimes < 10) {
//                                toRightTimes++
//                            } else {
//                                // TODO: 2021/4/24
////                                toRightTimes = 0
////                                currentRunningMode = Direction.Right
////                                rotateTargetAngle = currentAngleX + 90.0
////                                command("当前状态更改为 $currentRunningMode")
//                            }
//                        }
//                    }
//                    Direction.Unknown -> {
//                        toRightTimes = 0
//                        setHorizontalPower(forward = 0.0)
//                    }
//                }
//            } else {
//                val rotatePower = (rotateTargetAngle - currentAngleX) * 0.002
//                setHorizontalPower(forward = 0.0, rotate = rotatePower, translate = 0.0)
//                if (abs(rotateTargetAngle - currentAngleX) <= 3) {
//                    setHorizontalPower(forward = 0.0, rotate = 0.0)
//                    currentRunningMode = Direction.Forward
//                    command("当前状态更改为 $currentRunningMode")
//                }
//            }
//            when (diving) {
//                Diving.Up -> currentDepthPower += 0.010
//                Diving.Keep, Diving.Unknown -> currentDepthPower += 0.0
//                Diving.Down -> currentDepthPower -= 0.010
//            }
//            currentDepthPower = limit(currentDepthPower, -1.0, 1.0)
////            setTopPower(currentDepthPower) todo
//            setTopPower(0.0) // TODO: 2021/4/24
                Thread.sleep(20)
            }
        }
        Connector.updateDegreeWithTurn("")
    }

    private fun monitorAngles() {
        val tempAngleX = radToDegree(Connector.getOrientationAngles()[0])
        if (abs(tempAngleX - lastAngleX) >= 180) {
            turnNumber -= sign(tempAngleX).toInt()
        }
        lastAngleX = tempAngleX
        if (robotMode(null) == RobotMode.Running) {
            Connector.updateDegreeWithTurn("水平连续 ${String.format("%.0f", currentAngleX)}°")
//        debug("AngleX = $currentAngleX, LastAngleX = $lastAngleX, Turn = $turnNumber")
        }
    }

    fun onPipeCatch(pipe: MatOfPoint, drawFeedback: Mat? = null) {

        fun drawPoint(point: Point, color: Scalar = Scalar(64.0, 255.0, 64.0), size: Int = 5) =
            Imgproc.circle(drawFeedback, point, size, color, size)

        fun drawLine(leftP: Point, rightP: Point, color: Scalar = Scalar(64.0, 255.0, 64.0)) =
            Imgproc.line(drawFeedback, leftP, rightP, color, 5, 1)

        fun averagePoint(p1: Point, p2: Point) = Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)

        fun getDistance(p1: Point, p2: Point): Double =
            sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))

        val pipe2f = MatOfPoint2f()
        pipe.convertTo(pipe2f, CvType.CV_32F)
        val minRect = Imgproc.minAreaRect(pipe2f)
        val drawPoints = Array(4) { Point() }

        fun rotate(times: Int) {
            repeat(times) {
                val temp = drawPoints[0]
                drawPoints[0] = drawPoints[3]
                drawPoints[3] = drawPoints[2]
                drawPoints[2] = drawPoints[1]
                drawPoints[1] = temp
            }
        }

        minRect.points(drawPoints)
        drawPoints.forEach {
            drawPoint(it)
        }
        if (minRect.size.height < minRect.size.width) rotate(1)

        var topAveragePoint = averagePoint(drawPoints[1], drawPoints[2])
        var bottomAveragePoint = averagePoint(drawPoints[0], drawPoints[3])
        var p1 = Point(
            bottomAveragePoint.x + (topAveragePoint.x - bottomAveragePoint.x) / 4,
            bottomAveragePoint.y + (topAveragePoint.y - bottomAveragePoint.y) / 4
        )
        var p2 = Point(
            topAveragePoint.x - (topAveragePoint.x - bottomAveragePoint.x) / 4,
            topAveragePoint.y - (topAveragePoint.y - bottomAveragePoint.y) / 4
        )
        val angle1 = -atan2(p2.y - p1.y, p2.x - p1.x) / (Math.PI / 2) * 90
//        command("Angle1 = ${String.format("%.2f", angle1)}")
        if (angle1 > 135 || angle1 == -180.0) rotate(2)

        topAveragePoint = averagePoint(drawPoints[1], drawPoints[2])
        bottomAveragePoint = averagePoint(drawPoints[0], drawPoints[3])
        p1 = Point(
            bottomAveragePoint.x + (topAveragePoint.x - bottomAveragePoint.x) / 4,
            bottomAveragePoint.y + (topAveragePoint.y - bottomAveragePoint.y) / 4
        )
        p2 = Point(
            topAveragePoint.x - (topAveragePoint.x - bottomAveragePoint.x) / 4,
            topAveragePoint.y - (topAveragePoint.y - bottomAveragePoint.y) / 4
        )
        val angle2 = -atan2(p2.y - p1.y, p2.x - p1.x) / (Math.PI / 2) * 90
        direction = when (angle2) {
            in -45.0..45.0, 180.0, -180.0 -> Direction.Right
            in 45.0..135.0 -> Direction.Forward
            else -> Direction.Unknown
        }

        val distance = getDistance(drawPoints[1], drawPoints[2])

        diving = if (direction != Direction.Right) {
            when (distance) {
                in Constant.TargetDepth -> Diving.Keep
                in Constant.TargetDepth.endInclusive..Double.MAX_VALUE -> Diving.Up
                in -Double.MAX_VALUE..Constant.TargetDepth.start -> Diving.Down
                else -> Diving.Unknown
            }
        } else Diving.Keep

//        debug("Angle = ${minRect.angle}, Angle1 = $angle1, Angle2 = $angle2")
//        command("Depth = $distance")

        if (drawFeedback != null) {
            navigatePipe = NavigatePipe(
                minRect.center.x - drawFeedback.cols() / 2.0,
                minRect.center.y - drawFeedback.rows() / 2.0,
                angle2,
                distance
            )

            drawLine(drawPoints[0], drawPoints[1])
            drawLine(drawPoints[1], drawPoints[2])
            drawLine(drawPoints[2], drawPoints[3])
            drawLine(drawPoints[3], drawPoints[0])

            //画箭头
            Imgproc.arrowedLine(
                drawFeedback,
                Point(
                    bottomAveragePoint.x + (topAveragePoint.x - bottomAveragePoint.x) / 4,
                    bottomAveragePoint.y + (topAveragePoint.y - bottomAveragePoint.y) / 4
                ), Point(
                    topAveragePoint.x - (topAveragePoint.x - bottomAveragePoint.x) / 4,
                    topAveragePoint.y - (topAveragePoint.y - bottomAveragePoint.y) / 4
                ), Scalar(255.0, 0.0, 0.0), 5
            )

            val upPoint =
                Point(drawFeedback.rows() / 6.0, drawFeedback.cols() / 3.0)
            val downPoint =
                Point(drawFeedback.rows() / 6.0, drawFeedback.cols() * 2 / 3.0)
            if (currentRunningMode == Direction.Right) {
                Imgproc.arrowedLine(
                    drawFeedback,
                    Point(drawFeedback.rows() * 1 / 4.0, drawFeedback.cols() / 10.0),
                    Point(drawFeedback.rows() * 3 / 4.0, drawFeedback.cols() / 10.0),
                    Scalar(255.0, 0.0, 0.0), 20
                )
            }
            when (diving) {
                Diving.Up -> Imgproc.arrowedLine(
                    drawFeedback,
                    downPoint,
                    upPoint,
                    Scalar(64.0, 255.0, 192.0),
                    10
                )
                Diving.Down -> Imgproc.arrowedLine(
                    drawFeedback,
                    upPoint,
                    downPoint,
                    Scalar(64.0, 255.0, 192.0),
                    10
                )
                else -> {
                }
            }
        }
    }

    override fun onRobotModeChanged(robotMode: RobotMode) {
        if (robotMode != RobotMode.Running) direction = Direction.Unknown
        if (robotMode != RobotMode.Running) diving = Diving.Unknown
    }
}
package edu.scut.submarinerobotcontroller.opmode

import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.tools.command
import edu.scut.submarinerobotcontroller.tools.debug
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

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

//    interface IVisionToController {
//        fun onPipeCatch(pipe: Mat, drawFeedback: Mat? = null)
//    }

    data class PipePointPair(var leftX: Double, var rightX: Double, val y: Double)

    override fun run() {
        repeat(90 * 1000) {
//            command("在爬了" + System.currentTimeMillis())
            if (super.robotMode(null) == RobotMode.Stop) return@repeat
            Thread.sleep(100)
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

        val pipeArr = pipe.toArray()
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
            drawLine(drawPoints[0], drawPoints[1])
            drawLine(drawPoints[1], drawPoints[2])
            drawLine(drawPoints[2], drawPoints[3])
            drawLine(drawPoints[3], drawPoints[0])

            Imgproc.arrowedLine(
                drawFeedback,
                Point(
                    bottomAveragePoint.x + (topAveragePoint.x - bottomAveragePoint.x) / 4,
                    bottomAveragePoint.y + (topAveragePoint.y - bottomAveragePoint.y) / 4
                ), Point(
                    topAveragePoint.x - (topAveragePoint.x - bottomAveragePoint.x) / 4,
                    topAveragePoint.y - (topAveragePoint.y - bottomAveragePoint.y) / 4
                ), Scalar(255.0, 0.0, 0.0), 10
            )

            val upPoint =
                Point(drawFeedback.rows() / 6.0, drawFeedback.cols() / 3.0)
            val downPoint =
                Point(drawFeedback.rows() / 6.0, drawFeedback.cols() * 2 / 3.0)
            when (diving) {
                Diving.Up -> Imgproc.arrowedLine(
                    drawFeedback,
                    downPoint,
                    upPoint,
                    Scalar(64.0, 255.0, 192.0),
                    12
                )
                Diving.Down -> Imgproc.arrowedLine(
                    drawFeedback,
                    upPoint,
                    downPoint,
                    Scalar(64.0, 255.0, 192.0),
                    12
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
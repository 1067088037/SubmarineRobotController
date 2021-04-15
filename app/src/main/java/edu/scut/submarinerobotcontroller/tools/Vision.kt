package edu.scut.submarinerobotcontroller.tools

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 视觉处理
 */
object Vision {

    /**
     * 获取平均点
     */
    fun getAveragePoint(points: MatOfPoint): Point {
        var sumOfX = 0.0
        var sumOfY = 0.0
        val pointArray = points.toArray()
        pointArray.forEach {
            sumOfX += it.x
            sumOfY += it.y
        }
        return Point(sumOfX / pointArray.size.toDouble(), sumOfY / pointArray.size.toDouble())
    }

    /**
     * 通过颜色抓取
     */
    fun catchByColor(
        hsv: Mat,
        lowerb: Scalar,
        upperb: Scalar,
        erodeAndDilateSize: Int = 4,
        iterations: Int = 3
    ): Pair<Mat, List<MatOfPoint>> {
        var mask = hsv.clone()
        Core.inRange(hsv, lowerb, upperb, mask)
        mask = erode(mask, erodeAndDilateSize, erodeAndDilateSize, iterations)
        mask = dilate(mask, erodeAndDilateSize, erodeAndDilateSize, iterations)
        Imgproc.GaussianBlur(mask, mask, Size(5.0, 5.0), 10.0, 20.0)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            mask,
            contours,
            hierarchy,
            Imgproc.RETR_CCOMP,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        contours.sortByDescending { Imgproc.contourArea(it) }
        return Pair(mask, contours)
    }

    /**
     * 膨胀处理
     */
    fun dilate(mat: Mat, i: Int, j: Int, iterations: Int = 1): Mat {
        val resMat = mat.clone()
        Imgproc.dilate(
            mat, resMat, Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT, Size(i.toDouble(), j.toDouble())
            ), Point(-1.0, -1.0),
            iterations
        )
        return resMat
    }

    /**
     * 腐蚀处理
     */
    fun erode(mat: Mat, i: Int, j: Int, iterations: Int = 1): Mat {
        val resMat = mat.clone()
        Imgproc.erode(
            mat, resMat, Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT, Size(i.toDouble(), j.toDouble())
            ), Point(-1.0, -1.0),
            iterations
        )
        return resMat
    }

}
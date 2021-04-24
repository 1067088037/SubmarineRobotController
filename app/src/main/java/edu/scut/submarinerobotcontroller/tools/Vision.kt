package edu.scut.submarinerobotcontroller.tools

import android.graphics.Bitmap
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.tensorflow.ImageUtils
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 视觉处理
 */
object Vision {

    private var internalWhiteArea = Mat()

    private fun whiteArea(rows: Int, cols: Int, channels: Int): Mat {
        return if (internalWhiteArea.rows() == rows && internalWhiteArea.cols() == cols && internalWhiteArea.channels() == channels) internalWhiteArea
        else {
            internalWhiteArea = Mat.ones(rows, cols, CvType.CV_8UC1)
            repeat(7) {
                Core.add(internalWhiteArea, internalWhiteArea, internalWhiteArea)
            }
            internalWhiteArea = copyToChannels(internalWhiteArea, 4)
            internalWhiteArea
        }
    }


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
     * 准备用于推理的信息
     */
    fun prepareToPredict(bitmap: Bitmap): FloatArray {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val hsv = Mat()
        Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGB2HSV)
        return prepareToPredict(rgba, hsv).first
    }

    /**
     * 准备用于推理的信息
     */
    fun prepareToPredict(rgba: Mat, hsv: Mat): Pair<FloatArray, Mat> {
        val blackMask =
            catchByColor(hsv, Constant.BlackColorLower, Constant.BlackColorUpper, 10, 3).first
        val outBlackMask = Mat()
        Core.bitwise_not(blackMask, outBlackMask)
        val dst = Mat()
        rgba.copyTo(dst, blackMask)
        val bitmap = Bitmap.createBitmap(
            rgba.width(),
            rgba.height(),
            Bitmap.Config.ARGB_8888
        )

        Core.bitwise_or(dst, copyToChannels(outBlackMask, rgba.channels()), dst)
//        val res = dst
        val res = rgba
        Utils.matToBitmap(res, bitmap) //预处理之后
        return Pair(
            ImageUtils.prepareCameraImage(bitmap, 0),
            res
        )
    }

    private fun copyToChannels(input: Mat, channels: Int): Mat {
        val output = Mat()
        val arrayList = MutableList(channels) { input }
        Core.merge(arrayList, output)
        return output
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
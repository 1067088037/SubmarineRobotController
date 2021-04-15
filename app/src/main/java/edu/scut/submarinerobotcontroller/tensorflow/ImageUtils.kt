package edu.scut.submarinerobotcontroller.tensorflow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix

object ImageUtils {

    private const val LOWER_BYTE_MASK = 0xFF

    fun prepareCameraImage(bitmap: Bitmap, rotationDegrees: Int): FloatArray? {
        val modelImageSize = TransferLearningModelWrapper.IMAGE_SIZE
        val paddedBitmap: Bitmap = padToSquare(bitmap)
        val scaledBitmap = Bitmap.createScaledBitmap(
            paddedBitmap, modelImageSize, modelImageSize, true
        )
        val rotationMatrix = Matrix()
        rotationMatrix.postRotate(rotationDegrees.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(
            scaledBitmap, 0, 0, modelImageSize, modelImageSize, rotationMatrix, false
        )
        val normalizedRgb = FloatArray(modelImageSize * modelImageSize * 3)
        var nextIdx = 0
        for (y in 0 until modelImageSize) {
            for (x in 0 until modelImageSize) {
                val rgb = rotatedBitmap.getPixel(x, y)
                val r = (rgb shr 16 and LOWER_BYTE_MASK) * (1 / 255f)
                val g = (rgb shr 8 and LOWER_BYTE_MASK) * (1 / 255f)
                val b = (rgb and LOWER_BYTE_MASK) * (1 / 255f)
                normalizedRgb[nextIdx++] = r
                normalizedRgb[nextIdx++] = g
                normalizedRgb[nextIdx++] = b
            }
        }
        return normalizedRgb
    }

    private fun padToSquare(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val paddingX = if (width < height) (height - width) / 2 else 0
        val paddingY = if (height < width) (width - height) / 2 else 0
        val paddedBitmap = Bitmap.createBitmap(
            width + 2 * paddingX, height + 2 * paddingY, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(paddedBitmap)
        canvas.drawARGB(0xFF, 0xFF, 0xFF, 0xFF)
        canvas.drawBitmap(source, paddingX.toFloat(), paddingY.toFloat(), null)
        return paddedBitmap
    }

}
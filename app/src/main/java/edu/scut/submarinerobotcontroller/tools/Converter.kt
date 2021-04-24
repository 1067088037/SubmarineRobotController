package edu.scut.submarinerobotcontroller.tools

import java.nio.ByteBuffer
import java.nio.FloatBuffer

object Converter {

    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocate(4 * floats.size)
        val floatBuffer: FloatBuffer = buffer.asFloatBuffer()
        floatBuffer.put(floats)
        return buffer.array()
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer: ByteBuffer = ByteBuffer.wrap(bytes)
        val fb: FloatBuffer = buffer.asFloatBuffer()
        val floatArray = FloatArray(fb.limit())
        fb.get(floatArray)
        return floatArray
    }

}
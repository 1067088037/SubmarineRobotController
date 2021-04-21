package edu.scut.submarinerobotcontroller

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import cn.wandersnail.bluetooth.Connection
import edu.scut.submarinerobotcontroller.opmode.*
import edu.scut.submarinerobotcontroller.tensorflow.TransferLearningModelWrapper
import edu.scut.submarinerobotcontroller.tools.debug
import org.opencv.android.JavaCamera2View
import org.opencv.core.Mat
import java.lang.Exception

/**
 * 非UI类与UI类交互内容
 */
object Connector {
    lateinit var updateOrientationAnglesText: (str: ArrayList<String>) -> Unit
    lateinit var updateOrientationAngles: (values: FloatArray) -> Unit
    lateinit var updateDegreeWithTurn: (str: String) -> Unit
    lateinit var getOrientationAngles: () -> FloatArray
    lateinit var setSignal: (a: Int, r: Int, g: Int, b: Int, text: String) -> Unit
    lateinit var setCamera2View: (enable: Boolean) -> Unit
    lateinit var stop: (stopMode: StopMode) -> Unit
    var updateCommand: ((string: String) -> Unit)? = null

    var mainController: BaseController?
        get() = BaseController.mainController
        set(value) {
            BaseController.mainController = value
        }
    var robotControllerMode = RobotControllerMode.Automatic
    var controllerCanScroll = true
    var bluetoothConnection: Connection? = null
    var tlModel: TransferLearningModelWrapper? = null
    var isAutomaticControl = false//false手机控制, true单片机控制
    var needToBePredicted: FloatArray? = null

    private var leftStickX: Float = 0f
    private var leftStickY: Float = 0f
    private var rightStickX: Float = 0f
    private var rightStickY: Float = 0f

    /**
     * 发送信息
     */
    @Synchronized
    fun sendMessage(message: ByteArray) {
        if (bluetoothConnection != null && bluetoothConnection!!.isConnected && bluetoothConnection!!.state == Connection.STATE_CONNECTED) {
            bluetoothConnection!!.write("Connector", message, null)
        }
    }

    /**
     * set或get 手柄摇杆的x坐标 get不传入参数
     */
    @Synchronized
    fun refreshLeftStickX(value: Float = Float.NaN): Float {
        if (value.isNaN().not()) leftStickX = value
        return leftStickX
    }

    @Synchronized
    fun refreshLeftStickY(value: Float = Float.NaN): Float {
        if (value.isNaN().not()) leftStickY = value
        return leftStickY
    }

    @Synchronized
    fun refreshRightStickX(value: Float = Float.NaN): Float {
        if (value.isNaN().not()) rightStickX = value
        return rightStickX
    }

    @Synchronized
    fun refreshRightStickY(value: Float = Float.NaN): Float {
        if (value.isNaN().not()) rightStickY = value
        return rightStickY
    }

}

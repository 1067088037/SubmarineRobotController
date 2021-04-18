package edu.scut.submarinerobotcontroller.opmode

import android.bluetooth.BluetoothDevice
import cn.wandersnail.bluetooth.BTManager
import cn.wandersnail.bluetooth.EventObserver
import cn.wandersnail.commons.observer.Observe
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.tools.debug

abstract class BaseController : EventObserver, IRobotMode {

    private var robotMode: RobotMode = RobotMode.Stop
    private var emergency: Boolean = false

    lateinit var leftFrontMotor: Motor
    lateinit var rightFrontMotor: Motor
    lateinit var leftRearMotor: Motor
    lateinit var rightRearMotor: Motor
    lateinit var leftDepthMotor: Motor
    lateinit var rightDepthMotor: Motor
    lateinit var leftServo: Servo
    lateinit var rightServo: Servo
    lateinit var motorArray: Array<Motor>

    init {
        if (baseControllerMainThread.state == Thread.State.NEW) baseControllerMainThread.start()
        BTManager.getInstance().registerObserver(this)
        onInit()
    }

    abstract fun run()

    private fun onInit() {
        Connector.mainController = this

        leftFrontMotor = Motor(this, "左前方", 0, Motor.Direction.Forward)
        rightFrontMotor = Motor(this, "右前方", 1, Motor.Direction.Forward)
        leftRearMotor = Motor(this, "左后方", 2, Motor.Direction.Forward)
        rightRearMotor = Motor(this, "右后方", 3, Motor.Direction.Forward)
        leftDepthMotor = Motor(this, "左深度", 4, Motor.Direction.Forward)
        rightDepthMotor = Motor(this, "右深度", 5, Motor.Direction.Forward)
        motorArray = arrayOf(
            leftFrontMotor,
            rightFrontMotor,
            leftRearMotor,
            rightRearMotor,
            leftDepthMotor,
            rightDepthMotor
        )
        leftServo = Servo(this, "左舵机", 6)
        rightServo = Servo(this, "右舵机", 7)

        emergency = false
        robotMode(RobotMode.WaitForStart)
    }

    fun onPause() {
        if (robotMode() != RobotMode.WaitForStart) robotMode(RobotMode.Pause)
    }

    fun onContinue() {
        robotMode(RobotMode.Running)
    }

    fun onStop() {
        onPause()
        emergency = true
        robotMode(RobotMode.Stop)
        Thread.sleep(50)
        Connector.mainController =
            if (this is AutomaticController) AutomaticController()
            else ManualController()
        Thread.sleep(50)
    }

    /**
     * 如果[BTManager.Builder.setObserveAnnotationRequired]设置为false时，无论加不加[Observe]注解都会收到消息。
     * 设置为true时，必须加[Observe]才会收到消息。
     * 默认为false，方法默认执行线程在[BTManager.Builder.setMethodDefaultThreadMode]指定
     */
    @Observe
    /**
     * 收到时回调
     */
    override fun onRead(device: BluetoothDevice, value: ByteArray) {
//        var message = ""
//        for (i in value.indices) {
//            message += "[$i]=${value[i]} "
//        }
//        debug("接收 $message")
    }

    /**
     * 当写入时回调
     */
    @Observe
    override fun onWrite(device: BluetoothDevice, tag: String, value: ByteArray, result: Boolean) {
//        var message = ""
//        for (i in value.indices) {
//            message += "[$i]=${value[i]} "
//        }
//        debug("发送 $message")
    }

    @Synchronized
    fun robotMode(mode: RobotMode? = null): RobotMode {
        if (mode != null) {
            robotMode = mode
            this.onRobotModeChanged(mode)
        }
        return robotMode
    }

    fun setHorizontalPower(forward: Double, rotate: Double = 0.0, translate: Double = 0.0) {
        setSidePower(
            forward + rotate + translate,
            forward - rotate - translate,
            forward + rotate - translate,
            forward - rotate + translate
        )
    }

    fun setSidePower(p0: Double, p1: Double, p2: Double, p3: Double) {
        if (this::leftDepthMotor.isInitialized) {
            leftFrontMotor.power = p0
            rightFrontMotor.power = p1
            leftRearMotor.power = p2
            rightRearMotor.power = p3
        }
    }

    fun setTopPower(p4_and_p5: Double) {
        if (this::leftDepthMotor.isInitialized && this::rightDepthMotor.isInitialized) {
            leftDepthMotor.power = p4_and_p5
            rightDepthMotor.power = p4_and_p5
        }
    }

    fun setServoPosition(left: Double, right: Double) {
        leftServo.position = left
        rightServo.position = right
    }

    companion object {
        var mainController: BaseController? = null

        //主线程
        private val baseControllerMainThread = Thread {
            Thread.currentThread().name = "基础控制器主线程"
            while (Thread.currentThread().isInterrupted.not()) {
                if (mainController != null) {
                    while (mainController?.robotMode() == RobotMode.WaitForStart)
                        Thread.sleep(1)

                    mainController?.run()

                    mainController?.setSidePower(0.0, 0.0, 0.0, 0.0)
                    mainController?.setTopPower(0.0)

                    if (mainController?.emergency?.not() == true) Connector.stop(StopMode.Normal)
                } else {
                    Thread.sleep(10)
                }
            }
        }
    }
}
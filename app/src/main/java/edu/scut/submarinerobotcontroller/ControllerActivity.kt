package edu.scut.submarinerobotcontroller

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.Color
import android.hardware.*
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import cn.wandersnail.bluetooth.BTManager
import cn.wandersnail.bluetooth.Connection
import cn.wandersnail.bluetooth.EventObserver
import cn.wandersnail.commons.observer.Observe
import cn.wandersnail.commons.poster.RunOn
import cn.wandersnail.commons.poster.Tag
import cn.wandersnail.commons.poster.ThreadMode
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import edu.scut.submarinerobotcontroller.databinding.ActivityControllerBinding
import edu.scut.submarinerobotcontroller.opmode.*
import edu.scut.submarinerobotcontroller.tools.*
import edu.scut.submarinerobotcontroller.ui.main.AutoFragment
import edu.scut.submarinerobotcontroller.ui.main.ManualFragment
import edu.scut.submarinerobotcontroller.ui.main.SectionsPagerAdapter
import edu.scut.submarinerobotcontroller.ui.view.NoScrollViewPager
import edu.scut.submarinerobotcontroller.ui.viewmodel.ControllerSharedViewModel
import java.util.*
import kotlin.math.max
import kotlin.system.measureTimeMillis

class ControllerActivity : AppCompatActivity(), SensorEventListener, EventObserver,
    ViewPager.OnPageChangeListener {

    private val robotController: BaseController?
        get() = Connector.mainController
    private lateinit var viewModel: ControllerSharedViewModel
    private lateinit var dataBinding: ActivityControllerBinding

    lateinit var runAndPauseButton: Button
    lateinit var emergencyStopButton: Button
    lateinit var runningTime: TextView
    lateinit var coordinatorLayout: CoordinatorLayout
    lateinit var controllerTitleTextView: TextView

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val currentOrientationAngles = FloatArray(3)
    private var lastRequestOrientationAnglesTime = System.currentTimeMillis()

    private val clock = Clock()
    private var isRunning = false
    private var connectionThreadRunning = true
    private var lastReadFeedbackTime = System.currentTimeMillis()
    private var lastShowTime = 0L
    private var readByteQueue: Queue<Byte> = LinkedList()

    private lateinit var cylinderSound: MediaPlayer
    private lateinit var cubeSound: MediaPlayer
    private var cylinderTimes = 0
    private var cubeTimes = 0
    private var lastRunOnUiTime = System.currentTimeMillis()

    private val updateUIAndPredictThread = Thread {
        fun postMotorPowerUi() {
            if (System.currentTimeMillis() - lastRunOnUiTime >= 20) {
                viewModel.motorPower.postValue(robotController!!.motorArray.map { it.power }
                    .toTypedArray())
                lastRunOnUiTime = System.currentTimeMillis()
            }
        }
        Thread.currentThread().name = "控制器时间显示和推理线程"
        while (Thread.currentThread().isInterrupted.not()) {
            val connection = Connector.bluetoothConnection
            val title = if (connection != null)
                "${connection.device.name} ${
                    when (connection.state) {
                        Connection.STATE_CONNECTING -> "连接中"
                        Connection.STATE_PAIRING -> "配对中"
                        Connection.STATE_PAIRED -> "配对完成"
                        Connection.STATE_CONNECTED -> "已连接"
                        Connection.STATE_DISCONNECTED -> "连接失败"
                        Connection.STATE_RELEASED -> "已释放"
                        else -> "未知"
                    }
                }" else "蓝牙连接断开"
            if (title != controllerTitleTextView.text) viewModel.title.postValue(title)
            postMotorPowerUi()
            if (clock.getMillSeconds() != 0L) {
                while (isRunning(false)) {
                    if (clock.getSeconds() != lastShowTime) {
                        logRunOnUi("更新时间")
                        viewModel.time.postValue("${getString(R.string.running_time)}\n${clock.getSeconds()} 秒")
                        lastShowTime = clock.getSeconds()
                    }
                    postMotorPowerUi()
//                debug("我的线程数 = $threadCount， 总线程数 = ${Thread.activeCount()}")
                    if (Connector.robotControllerMode == RobotControllerMode.Automatic)
                        Connector.updateOrientationAngles(orientationAngles())
//                Connector.updateCommand(" when ${clock.getMillSeconds()} ms")
                    //推理部分
                    if (Connector.needToBePredicted != null) {
//                    debug("推理开始")
                        val predictions =
                            Connector.tlModel?.predict(Connector.needToBePredicted)
                        if (predictions != null && predictions.isNotEmpty()) {
                            var cylinderCoincidence = 0f
                            var cubeCoincidence = 0f
                            predictions.forEach {
                                if (it.className == Constant.CylinderId)
                                    cylinderCoincidence = it.confidence
                                if (it.className == Constant.CubeId)
                                    cubeCoincidence = it.confidence
                            }
                            if (cylinderCoincidence > cubeCoincidence) {
                                if (cylinderCoincidence >= Constant.NeedCoincidence) {
                                    cylinderTimes++
                                    cubeTimes = 0
                                    if (cubeSound.isPlaying) cubeSound.stop()
                                    if (cylinderTimes >= Constant.NeedPredictTimes) {
                                        if (cylinderSound.isPlaying.not()) cylinderSound.start()
                                        Connector.setSignal(
                                            255,
                                            255,
                                            0,
                                            0,
                                            "圆柱体"
                                        )
                                    }
                                } else Connector.setSignal(32, 0, 0, 0, "置信度低")
                            } else {
                                if (cubeCoincidence >= Constant.NeedCoincidence) {
                                    cubeTimes++
                                    cylinderTimes = 0
                                    if (cylinderSound.isPlaying) cylinderSound.stop()
                                    if (cubeTimes >= Constant.NeedPredictTimes) {
                                        if (cubeSound.isPlaying.not()) cubeSound.start()
                                        Connector.setSignal(
                                            255,
                                            0,
                                            255,
                                            0,
                                            "正方体"
                                        )
                                    }
                                } else Connector.setSignal(32, 0, 0, 0, "置信度低")
                            }
                        } else Connector.setSignal(32, 0, 0, 0, "预测失败")
                        Connector.needToBePredicted = null
                    } else {
                        repeat(10) {
                            Thread.sleep(1)
                            if (isRunning(false).not()) return@repeat
                        }
                    }
                }
            } else Thread.sleep(10)
        }
    }

    private val bluetoothSendMessageThread = Thread {
        Thread.currentThread().name = "控制器蓝牙发送线程"
        while (connectionThreadRunning) {
            measureTimeMillis {
                val message = ByteArray(32) { Constant.EmptyCode }
                //三位起始码
                message[0] = Constant.CommandStartCode
                message[1] = Constant.CommandStartCode
                message[2] = Constant.CommandStartCode
                //自动控制开关
                message[3] =
                    if (Connector.isAutomaticControl) Constant.TrueCode else Constant.FalseCode
                //三个方向的倾角
                //—————————————————————————————————————————————————————————————————————————————————— 不保证正确
                orientationAngles()
                message[4] = limit(
                    ((radToDegree(currentOrientationAngles[0]).toInt() + 180) / 2 - 90),
                    -90,
                    89
                ).toByte()
                message[5] = limit(
                    ((radToDegree(currentOrientationAngles[1]).toInt() + 180) / 2 - 90),
                    -90,
                    89
                ).toByte()
                message[6] = limit(
                    ((radToDegree(currentOrientationAngles[2]).toInt() + 180) / 2 - 90),
                    -90,
                    89
                ).toByte()
                //探头状态
                message[7] = Constant.EmptyCode
                message[8] = Constant.EmptyCode
                message[9] = Constant.EmptyCode
                message[10] = Constant.EmptyCode
                //六组功率
                message[11] =
                    Connector.mainController?.leftFrontMotor?.getHardwarePower() ?: 0
                message[12] =
                    Connector.mainController?.rightFrontMotor?.getHardwarePower() ?: 0
                message[13] =
                    Connector.mainController?.leftRearMotor?.getHardwarePower() ?: 0
                message[14] =
                    Connector.mainController?.rightRearMotor?.getHardwarePower() ?: 0
                message[15] =
                    Connector.mainController?.leftDepthMotor?.getHardwarePower() ?: 0
                message[16] =
                    Connector.mainController?.rightDepthMotor?.getHardwarePower() ?: 0
                //舵机角度
                message[17] =
                    Connector.mainController?.leftServo?.getHardwarePosition() ?: 0
                message[18] =
                    Connector.mainController?.leftServo?.getHardwarePosition() ?: 0
                //额外参数
                message[19] = Constant.EmptyCode
                message[20] = Constant.EmptyCode
                message[21] = Constant.EmptyCode
                message[22] = Constant.EmptyCode
                message[23] = Constant.EmptyCode
                message[24] = Constant.EmptyCode
                //延迟测算
                val runTimeMillis =
                    (System.currentTimeMillis() - Constant.SystemStartTime).toInt()
                message[25] = (runTimeMillis % 100).toByte()
                message[26] = (runTimeMillis / 100 % 100).toByte()
                message[27] = (runTimeMillis / 10000 % 100).toByte()
                message[28] = (runTimeMillis / 1000000 % 100).toByte()
                //校验和
                message[29] = kotlin.run {
                    var tmp = 0
                    for (i in 3..10) tmp += message[i].toInt()
                    max(Constant.MinValidCommandCode.toInt(), tmp).toByte()
                }
                message[30] = kotlin.run {
                    var tmp = 0
                    for (i in 11..18) tmp += message[i].toInt()
                    max(Constant.MinValidCommandCode.toInt(), tmp).toByte()
                }
                message[31] = kotlin.run {
                    var tmp = 0
                    for (i in 19..28) tmp += message[i].toInt()
                    max(Constant.MinValidCommandCode.toInt(), tmp).toByte()
                }

                Connector.sendMessage(message)
            }
            if (System.currentTimeMillis() - lastReadFeedbackTime >= 1000) updatePing(9999)
            Thread.sleep(100)
        }
    }

    init {
        Connector.stop = this::stop
        Connector.getOrientationAngles = this::orientationAngles
        clock.pause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataBinding = DataBindingUtil.setContentView(this, R.layout.activity_controller)
        viewModel = ViewModelProvider(this).get(
            "ControllerSharedViewModel",
            ControllerSharedViewModel::class.java
        )
        dataBinding.data = viewModel
        dataBinding.lifecycleOwner = this

        viewModel.time.value = getString(R.string.running_time)

        debug("Controller onCreate 总线程数 = ${Thread.activeCount()}")
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: NoScrollViewPager = findViewById(R.id.view_pager)
        viewPager.adapter = sectionsPagerAdapter
        viewPager.addOnPageChangeListener(this)
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)

        runAndPauseButton = findViewById(R.id.btn_run_and_pause)
        emergencyStopButton = findViewById(R.id.btn_emergency_stop)
        runningTime = findViewById(R.id.text_running_time)
        coordinatorLayout = findViewById(R.id.controller_coordinatorlayout)
        controllerTitleTextView = findViewById(R.id.controller_title)

        BTManager.getInstance().registerObserver(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        Connector.mainController = AutomaticController()
        updatePing(9999)

        connectionThreadRunning = true

        cylinderSound = MediaPlayer.create(applicationContext, R.raw.cylinder)
        cubeSound = MediaPlayer.create(applicationContext, R.raw.cube)
        cylinderSound.isLooping = false
        cubeSound.isLooping = false

        updateUIAndPredictThread.start()
        bluetoothSendMessageThread.start()
    }

    @Observe
    override fun onRead(device: BluetoothDevice, value: ByteArray) {
        if (device == Connector.bluetoothConnection?.device) {
            value.forEach {
                readByteQueue.offer(it)
            }
        }

        //确保开头是-128
        while (readByteQueue.peek() != null && readByteQueue.peek() != Constant.CommandStartCode) {
            readByteQueue.poll()
        }
        if (readByteQueue.size >= Constant.LengthOfMessage) {
            val readArray = ByteArray(Constant.LengthOfMessage) { 0 }
            for (i in 0 until Constant.LengthOfMessage) {
                readArray[i] = readByteQueue.poll()!!
            }
            if (readArray[0] == Constant.CommandStartCode && readArray[1] == Constant.CommandStartCode && readArray[2] == Constant.CommandStartCode) {
                val checkOutCode = IntArray(3) { 0 }
                //校验和
                checkOutCode[0] = kotlin.run {
                    var tmp = 0
                    for (i in 3..10) tmp += readArray[i]
                    max(Constant.MinValidCommandCode.toInt(), tmp)
                }
                checkOutCode[1] = kotlin.run {
                    var tmp = 0
                    for (i in 11..18) tmp += readArray[i]
                    max(Constant.MinValidCommandCode.toInt(), tmp)
                }
                checkOutCode[2] = kotlin.run {
                    var tmp = 0
                    for (i in 19..28) tmp += readArray[i]
                    max(Constant.MinValidCommandCode.toInt(), tmp)
                }
                debug(
                    "延迟 = ${readArray[25]} ${readArray[26]} ${readArray[27]} ${readArray[28]}, " +
                            "校验 = ${checkOutCode[0].toByte()}?=${readArray[29]} ${checkOutCode[1].toByte()}?=${readArray[30]} ${checkOutCode[2].toByte()}?=${readArray[31]} "
                )
                val onSendTime = //发送蓝牙信息时的时间
                    readArray[25].toLong() + readArray[26].toLong() * 100L + readArray[27].toLong() * 10000L + readArray[28].toLong() * 1000000L + Constant.SystemStartTime
                val delayTime = ((System.currentTimeMillis() - onSendTime) / 2L).toInt() //来回时间除以2
                debug("延迟 = $delayTime")
                updatePing(delayTime)
                lastReadFeedbackTime = System.currentTimeMillis()
            } else debug("非法消息")
        }
    }

    @Synchronized
    fun isRunning(set: Boolean = false, running: Boolean = false): Boolean {
        if (set) isRunning = running
        return isRunning
    }

    override fun onBackPressed() {
        if (isRunning(false)) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("程序正在运行，你确定要返回吗？")
                .setPositiveButton("确定") { _, _ -> super.onBackPressed() }
                .setNegativeButton("取消") { _, _ -> }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        debug("Controller onResume 总线程数 = ${Thread.activeCount()}")
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        pause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionThreadRunning = false
        stop(StopMode.Emergency)
        ManualFragment.destroy()
        AutoFragment.destroy()
    }

    /**
     * 使用[Observe]确定要接收消息，[RunOn]指定在主线程执行方法，设置[Tag]防混淆后找不到方法
     */
    @Tag("onConnectionStateChanged")
    @Observe
    @RunOn(ThreadMode.MAIN)
    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        when (state) {
            Connection.STATE_CONNECTING -> {
                showSnackbar(R.string.STATE_CONNECTING)
            }
            Connection.STATE_PAIRING -> {
                showSnackbar(R.string.STATE_PAIRING)
            }
            Connection.STATE_PAIRED -> {
                showSnackbar(R.string.STATE_PAIRED)
            }
            Connection.STATE_CONNECTED -> {
                showSnackbar(R.string.STATE_CONNECTED)
            }
            Connection.STATE_DISCONNECTED -> {
                showSnackbar(R.string.STATE_DISCONNECTED)
            }
            Connection.STATE_RELEASED -> {
                showSnackbar(R.string.STATE_RELEASED)
            }
        }
    }

    /**
     * 启动和暂停
     */
    fun runAndPause(view: View) {
        isRunning(true, isRunning(false).not())
        if (isRunning(false)) run()
        else pause()

//        val stack = Thread.getAllStackTraces()
//        stack.forEach { (t, u) ->
//            debug("$t = ${t.state}")
//        }
        debug("线程总数 = ${Thread.activeCount()}")
    }

    /**
     * 启动程序
     */
    private fun run() {
        isRunning(set = true, running = true)
        clock.start()
        Connector.updateCommand!!(getString(R.string.run))
        Connector.controllerCanScroll = false
        runAndPauseButton.setBackgroundColor(getColor(R.color.neutral_blue))
        runAndPauseButton.text = getString(R.string.pause_icon)

        robotController?.onContinue() //先干其它事情最后再开始
    }

    /**
     * 暂停
     */
    private fun pause(isStop: Boolean = false) {
        isRunning(set = true, running = false)
        clock.pause()
        if (isStop.not()) {
            robotController?.onPause() //先暂停再干其它事情
            Connector.updateCommand!!(getString(R.string.pause))
        }
        Connector.controllerCanScroll = true
        runAndPauseButton.setBackgroundColor(getColor(R.color.positive_green))
        runAndPauseButton.text = getString(R.string.run_icon)
    }

    /**
     * 紧急停止程序
     */
    private fun stop(stopMode: StopMode) {
        isRunning(set = false, running = false)
        pause(true)
        robotController?.onStop()
        if (stopMode == StopMode.WhenWaitForStart) {
            // Do Nothing
        } else {
            Connector.updateCommand!!(
                "${
                    if (stopMode == StopMode.Emergency) getString(R.string.emergency_stop) else getString(
                        R.string.normal_stop
                    )
                }\n\n${
                    getString(R.string.command_default)
                }"
            )
        }
        Connector.updateOrientationAnglesText(arrayListOf())
        viewModel.time.postValue(getString(R.string.running_time))
        clock.reset()
    }

    /**
     * 紧急停止程序
     */
    fun emergencyStop(view: View) {
        if (Connector.mainController is ManualController)
            stop(StopMode.Normal)
        else
            stop(StopMode.Emergency)
    }

    private fun updatePing(ping: Int) {
        val pingText =
            "${getString(R.string.ping)} = ${if (ping >= 1000) "Infinity" else ping.toString()} ms"
        viewModel.ping.postValue(pingText)
        viewModel.pingColor.postValue(
            when (ping) {
                in 0..50 -> Color.GREEN
                in 51..200 -> Color.BLUE
                in 201..500 -> Color.YELLOW
                in 501..2500 -> Color.RED
                else -> Color.RED
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_controller, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_exit_1 -> finish()
        }
        return true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun orientationAngles(): FloatArray {
        if (System.currentTimeMillis() - lastRequestOrientationAnglesTime <= 3)
            return currentOrientationAngles
        lastRequestOrientationAnglesTime = System.currentTimeMillis()
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, currentOrientationAngles)
        return currentOrientationAngles
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
        if (Connector.mainController != null)
            when (Connector.mainController!!.robotMode()) {
                RobotMode.Running, RobotMode.Pause -> stop(StopMode.Emergency)
                RobotMode.WaitForStart -> stop(StopMode.WhenWaitForStart)
                else -> {
                }
            }
        Connector.robotControllerMode = if (position == 0) {
            Connector.setCamera2View(true)
            Connector.mainController = AutomaticController()
            RobotControllerMode.Automatic
        } else {
            Connector.setCamera2View(false)
            Connector.mainController = ManualController()
            RobotControllerMode.Manual
        }
        if (isRunning(false)) stop(StopMode.Emergency)
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    private fun showSnackbar(resId: Int) {
        Snackbar.make(coordinatorLayout, resId, Snackbar.LENGTH_LONG).show()
    }

}
package edu.scut.submarinerobotcontroller

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
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
        Thread.currentThread().name = "????????????????????????????????????"
        while (Thread.currentThread().isInterrupted.not()) {
            val connection = Connector.bluetoothConnection
            val title = if (connection != null)
                "${connection.device.name} ${
                    when (connection.state) {
                        Connection.STATE_CONNECTING -> "?????????"
                        Connection.STATE_PAIRING -> "?????????"
                        Connection.STATE_PAIRED -> "????????????"
                        Connection.STATE_CONNECTED -> "?????????"
                        Connection.STATE_DISCONNECTED -> "????????????"
                        Connection.STATE_RELEASED -> "?????????"
                        else -> "??????"
                    }
                }" else "??????????????????"
            if (title != controllerTitleTextView.text) viewModel.title.postValue(title)
            postMotorPowerUi()
            if (clock.getMillSeconds() != 0L) {
                while (isRunning(false)) {
                    if (clock.getSeconds() != lastShowTime) {
                        logRunOnUi("????????????")
                        viewModel.time.postValue("${getString(R.string.running_time)}\n${clock.getSeconds()} ???")
                        lastShowTime = clock.getSeconds()
                    }
                    debug("AutoId = ${Connector.autoRunningId}")
                    postMotorPowerUi()
//                debug("??????????????? = $threadCount??? ???????????? = ${Thread.activeCount()}")
                    if (Connector.robotControllerMode == RobotControllerMode.Automatic)
                        Connector.updateOrientationAngles(orientationAngles())
//                Connector.updateCommand(" when ${clock.getMillSeconds()} ms")
                    //????????????
                    if (Connector.runMode == AutoRunMode.TrueAuto) {
                        if (Connector.needToBePredicted != null) {
//                    debug("????????????")
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
                                                "?????????",
                                                AutoRunMode.TrueAuto
                                            )
                                        }
                                    } else Connector.setSignal(
                                        32,
                                        0,
                                        0,
                                        0,
                                        "????????????",
                                        AutoRunMode.TrueAuto
                                    )
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
                                                "?????????",
                                                AutoRunMode.TrueAuto
                                            )
                                        }
                                    } else Connector.setSignal(
                                        32,
                                        0,
                                        0,
                                        0,
                                        "????????????",
                                        AutoRunMode.TrueAuto
                                    )
                                }
                            } else Connector.setSignal(32, 0, 0, 0, "????????????", AutoRunMode.TrueAuto)
                            Connector.needToBePredicted = null
                        }
                    }
                    repeat(10) {
                        Thread.sleep(1)
                        if (isRunning(false).not()) return@repeat
                    }
                }
            } else Thread.sleep(10)
        }
    }

    private val bluetoothSendMessageThread = Thread {
        Thread.currentThread().name = "???????????????????????????"
        while (connectionThreadRunning) {
            measureTimeMillis {
                val message = ByteArray(32) { Constant.EmptyCode }
                //???????????????
                message[0] = Constant.CommandStartCode
                message[1] = Constant.CommandStartCode
                message[2] = Constant.CommandStartCode
                //??????????????????
                message[3] =
                    if (Connector.isAutomaticControl) Constant.TrueCode else Constant.FalseCode
                //?????????????????????
                //?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????? ???????????????
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
                //????????????
                message[7] = Constant.EmptyCode
                message[8] = Constant.EmptyCode
                message[9] = Constant.EmptyCode
                message[10] = Constant.EmptyCode
                //????????????
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
                //????????????
                message[17] =
                    Connector.mainController?.leftServo?.getHardwarePosition() ?: 0
                message[18] =
                    Connector.mainController?.leftServo?.getHardwarePosition() ?: 0
                //????????????
                message[19] = Constant.EmptyCode
                message[20] = Constant.EmptyCode
                message[21] = Constant.EmptyCode
                message[22] = Constant.EmptyCode
                message[23] = Constant.EmptyCode
                message[24] = Constant.EmptyCode
                //????????????
                val runTimeMillis =
                    (System.currentTimeMillis() - Constant.SystemStartTime).toInt()
                message[25] = (runTimeMillis % 100).toByte()
                message[26] = (runTimeMillis / 100 % 100).toByte()
                message[27] = (runTimeMillis / 10000 % 100).toByte()
                message[28] = (runTimeMillis / 1000000 % 100).toByte()
                //?????????
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
        viewModel.runAndPauseButtonColor.postValue(ColorStateList.valueOf(getColor(R.color.positive_green)))
        viewModel.runAndPauseButtonText.postValue(getString(R.string.run_icon))

        debug("Controller onCreate ???????????? = ${Thread.activeCount()}")
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: NoScrollViewPager = findViewById(R.id.view_pager)
        viewPager.adapter = sectionsPagerAdapter
        viewPager.addOnPageChangeListener(this)
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)

        dataBinding.signalLight.setOnLongClickListener {
            debug("????????????")
            val autoData = MyDatabase.getInstance().getAllData()
            val autoList = autoData.map { it.description }.toMutableList()
            autoList.add(0, "??????????????????")
            AlertDialog.Builder(this)
                .setTitle("??????????????????")
                .setItems(autoList.toTypedArray()) { _: DialogInterface?, which: Int ->
                    if (which == 0) {
                        Connector.autoRunningId = -1
                        Connector.runMode = AutoRunMode.TrueAuto
                    } else {
                        Connector.autoRunningId = autoData[which - 1].id
                        Connector.runMode = AutoRunMode.RecordedManual
                    }
                }
                .show()
            return@setOnLongClickListener true
        }

        viewModel.signalBackgroundColor.observe(this, androidx.lifecycle.Observer {
            dataBinding.signalLight.setBackgroundColor(it)
        })

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

        //???????????????-128
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
                //?????????
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
                    "?????? = ${readArray[25]} ${readArray[26]} ${readArray[27]} ${readArray[28]}, " +
                            "?????? = ${checkOutCode[0].toByte()}?=${readArray[29]} ${checkOutCode[1].toByte()}?=${readArray[30]} ${checkOutCode[2].toByte()}?=${readArray[31]} "
                )
                val onSendTime = //??????????????????????????????
                    readArray[25].toLong() + readArray[26].toLong() * 100L + readArray[27].toLong() * 10000L + readArray[28].toLong() * 1000000L + Constant.SystemStartTime
                val delayTime = ((System.currentTimeMillis() - onSendTime) / 2L).toInt() //??????????????????2
                debug("?????? = $delayTime")
                updatePing(delayTime)
                lastReadFeedbackTime = System.currentTimeMillis()
            } else debug("????????????")
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
                .setTitle("??????")
                .setMessage("?????????????????????????????????????????????")
                .setPositiveButton("??????") { _, _ -> super.onBackPressed() }
                .setNegativeButton("??????") { _, _ -> }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        debug("Controller onResume ???????????? = ${Thread.activeCount()}")
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
     * ??????[Observe]????????????????????????[RunOn]???????????????????????????????????????[Tag]???????????????????????????
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
     * ???????????????
     */
    fun runAndPause(view: View) {
        isRunning(true, isRunning(false).not())
        if (isRunning(false)) run()
        else pause()

//        val stack = Thread.getAllStackTraces()
//        stack.forEach { (t, u) ->
//            debug("$t = ${t.state}")
//        }
        debug("???????????? = ${Thread.activeCount()}")
    }

    /**
     * ????????????
     */
    private fun run() {
        isRunning(set = true, running = true)
        clock.start()
        Connector.updateCommand!!(getString(R.string.run))
        Connector.controllerCanScroll = false
        viewModel.runAndPauseButtonColor.postValue(ColorStateList.valueOf(getColor(R.color.neutral_blue)))
        viewModel.runAndPauseButtonText.postValue(getString(R.string.pause_icon))

        robotController?.onContinue() //?????????????????????????????????
    }

    /**
     * ??????
     */
    private fun pause(isStop: Boolean = false) {
        isRunning(set = true, running = false)
        clock.pause()
        if (isStop.not()) {
            robotController?.onPause() //???????????????????????????
            Connector.updateCommand!!(getString(R.string.pause))
        }
        Connector.controllerCanScroll = true
        viewModel.runAndPauseButtonColor.postValue(ColorStateList.valueOf(getColor(R.color.positive_green)))
        viewModel.runAndPauseButtonText.postValue(getString(R.string.run_icon))
    }

    /**
     * ??????????????????
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
     * ??????????????????
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
            Connector.mainController = ManualController(this, this::runOnUiThread)
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
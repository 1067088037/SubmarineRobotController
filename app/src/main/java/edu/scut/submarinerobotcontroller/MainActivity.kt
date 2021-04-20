package edu.scut.submarinerobotcontroller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import cn.wandersnail.bluetooth.*
import cn.wandersnail.commons.observer.Observe
import cn.wandersnail.commons.poster.RunOn
import cn.wandersnail.commons.poster.Tag
import cn.wandersnail.commons.poster.ThreadMode
import com.google.android.material.snackbar.Snackbar
import edu.scut.submarinerobotcontroller.Connector.tlModel
import edu.scut.submarinerobotcontroller.tensorflow.TransferLearningModelWrapper
import edu.scut.submarinerobotcontroller.tools.Vision
import edu.scut.submarinerobotcontroller.tools.debug
import edu.scut.submarinerobotcontroller.tools.logRunOnUi
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), EventObserver, TransferLearningModel.LossConsumer {

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var bluetoothWorkingProgress: ProgressBar
    private lateinit var trainProgressBar: ProgressBar
    private lateinit var startControllerActivityBtn: Button
    private lateinit var bluetoothStateTextView: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var btManager: BTManager? = null
    private var connection: Connection?
        get() = Connector.bluetoothConnection
        set(value) {
            Connector.bluetoothConnection = value
        }
    private val targetProgress = 10.0.pow(100)
    private var emergencyStopDiscovery = false

    private val deviceList = arrayListOf<BluetoothDevice>()
    private val trainingList: Queue<Pair<String, Bitmap>> = LinkedList()
    private var preparedSamples = 0

    private var discoveryListener = object : DiscoveryListener {
        private var stDiscoveryTime = System.currentTimeMillis()
        private var findDefaultDevice = false

        override fun onDiscoveryStart() {
            bluetoothStateTextView.text = "搜索中..."
            findDefaultDevice = false
            isShowBluetoothWorking(true)
            deviceList.clear()
            stDiscoveryTime = System.currentTimeMillis()
        }

        override fun onDiscoveryStop() {
            bluetoothStateTextView.text = "搜索结束"
            isShowBluetoothWorking(false)
            if (emergencyStopDiscovery.not()) {
                if (findDefaultDevice.not()) {
                    if (deviceList.isNotEmpty()) chooseConnectDevice()
                    else showSnackbar(R.string.founded_no_device)
                }
            } else emergencyStopDiscovery = false
        }

        override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
            if (device.name != null && !deviceList.contains(device)) {
                debug("发现设备 = ${device.name}")
                Constant.DefaultBluetoothName.forEach {
                    if (device.name == it) {
                        findDefaultDevice = true
                        btManager?.stopDiscovery()
                        connection = btManager?.createConnection(device, this@MainActivity)
                        connection!!.connect(null, object : ConnectCallback {
                            override fun onSuccess() {
                                runOnUiThread {
                                    logRunOnUi("发现默认设备")
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle(R.string.founded_default_device)
                                        .setMessage(R.string.founded_default_device_message)
                                        .setPositiveButton("好") { _, _ ->
                                            startControllerActivity()
                                        }
                                        .show()
                                }
                            }

                            override fun onFail(errMsg: String, e: Throwable?) {
                                showSnackbar(R.string.connect_default_device_failed)
                            }
                        })
                    }
                }
                deviceList.add(device)
            }
        }

        override fun onDiscoveryError(errorCode: Int, errorMsg: String) {
            isShowBluetoothWorking(false)
            when (errorCode) {
                DiscoveryListener.ERROR_LACK_LOCATION_PERMISSION -> showSnackbar(R.string.ERROR_LACK_LOCATION_PERMISSION)
                DiscoveryListener.ERROR_LOCATION_SERVICE_CLOSED -> showSnackbar(R.string.ERROR_LOCATION_SERVICE_CLOSED)
                DiscoveryListener.ERROR_SCAN_FAILED -> showSnackbar(R.string.ERROR_SCAN_FAILED)
            }
        }
    }

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            super.onManagerConnected(status)
            when (status) {
                LoaderCallbackInterface.SUCCESS -> debug("OpenCV载入成功")
                LoaderCallbackInterface.INIT_FAILED -> debug("OpenCV载入失败")
                else -> debug("OpenCV Status = $status")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coordinatorLayout = findViewById(R.id.main_coordinatorlayout)
        bluetoothWorkingProgress = findViewById(R.id.bluetooth_working)
        bluetoothWorkingProgress.visibility = View.INVISIBLE
        trainProgressBar = findViewById(R.id.train_progress)
        startControllerActivityBtn = findViewById(R.id.btn_start_control_activity)
        bluetoothStateTextView = findViewById(R.id.bluetooth_state)
        bluetoothStateTextView.text = "蓝牙连接中断"

        requestPermission()

        trainProgressBar.progress = 0
        startControllerActivityBtn.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (checkAllPermission()) startBluetooth()
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
        Thread {
            if (trainProgressBar.progress <= 99) {
                tlModel = TransferLearningModelWrapper.getInstance(applicationContext)
                runOnUiThread {
                    trainProgressBar.progressTintList =
                        ColorStateList.valueOf(Color.rgb(98, 0, 238))
                }
                for (i in Constant.TrainData2Array) {
                    trainingList.offer(
                        Pair(
                            "2",
                            BitmapFactory.decodeResource(
                                applicationContext!!.resources, i
                            )
                        )
                    )
                }
                for (i in Constant.TrainData4Array) {
                    trainingList.offer(
                        Pair(
                            "4",
                            BitmapFactory.decodeResource(
                                applicationContext!!.resources, i
                            )
                        )
                    )
                }
                val addSampleRunnable = kotlinx.coroutines.Runnable {
                    while (trainingList.isNotEmpty()) {
                        val trainingObj = trainingList.peek()
                        trainingList.poll()
                        if (trainingObj != null) {
                            tlModel!!.addSample(
                                Vision.prepareToPredict(
                                    trainingObj.second
                                ), trainingObj.first
                            ).get()
                        }
                        preparedSamples++
                        runOnUiThread {
                            trainProgressBar.progress =
                                ((preparedSamples.toDouble() / (preparedSamples + trainingList.size).toDouble()) * 100.0).toInt()
                        }
                    }
                }
                val threadList = Array(8) {
                    Thread {
                        addSampleRunnable.run()
                    }
                }
                for (i in threadList) {
                    i.start()
                }
                thread {
                    addSampleRunnable.run()
                    while (threadList.find { it.isAlive } != null) {
                        Thread.sleep(1)
                    }
                    val color = ColorStateList.valueOf(Color.rgb(3, 218, 197))
                    runOnUiThread {
                        trainProgressBar.progress = 0
                        trainProgressBar.progressTintList = color
                    }
                    tlModel!!.enableTraining(this)
                }
            } else runOnUiThread {
                startControllerActivityBtn.isEnabled = true
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        btManager?.release()
        exitProcess(0)
    }


    // ############################## 蓝牙 ############################## Start

    /**
     * 初始化蓝牙
     */
    private fun initBluetooth() {
        debug("初始化蓝牙")
        if (bluetoothAdapter == null) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && btManager == null) {
            btManager = BTManager.getInstance()
            btManager?.addDiscoveryListener(discoveryListener)
        }
    }

    /**
     * 打开蓝牙
     */
    private fun startBluetooth() {
        debug("打开蓝牙")
        initBluetooth()
        if (bluetoothAdapter != null && btManager != null) {
            if (bluetoothAdapter!!.isEnabled.not()) { //如果蓝牙没有启动
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, Constant.StartBlueBoothRequestCode) //启动蓝牙
            } else {
                onBlueBoothEnable() //如果蓝牙已经启动，直接开始
            }
        }
    }

    /**
     * 当蓝牙可用时
     */
    private fun onBlueBoothEnable() {
        if (btManager != null) {
            showSnackbar(R.string.searching_bluetooth_device)
            btManager!!.startDiscovery()
            isShowBluetoothWorking(true)
        }
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
                bluetoothStateTextView.text = kotlin.run { "正在与 ${device.name} 连接" }
                showSnackbar(R.string.STATE_CONNECTING)
                isShowBluetoothWorking(true)
            }
            Connection.STATE_PAIRING -> {
                bluetoothStateTextView.text = kotlin.run { "正在与 ${device.name} 配对" }
                isShowBluetoothWorking(true)
                showSnackbar(R.string.STATE_PAIRING)
            }
            Connection.STATE_PAIRED -> {
                bluetoothStateTextView.text = kotlin.run { "${device.name} 已配对" }
                isShowBluetoothWorking(false)
                showSnackbar(R.string.STATE_PAIRED)
            }
            Connection.STATE_CONNECTED -> {
                bluetoothStateTextView.text = kotlin.run { "${device.name} 已连接" }
                isShowBluetoothWorking(false)
                showSnackbar(R.string.STATE_CONNECTED)
            }
            Connection.STATE_DISCONNECTED -> {
                bluetoothStateTextView.text = kotlin.run { "与 ${device.name} 连接失败" }
                isShowBluetoothWorking(false)
                showSnackbar(R.string.STATE_DISCONNECTED)
            }
            Connection.STATE_RELEASED -> {
                bluetoothStateTextView.text = kotlin.run { "与 ${device.name} 连接释放" }
                isShowBluetoothWorking(false)
                showSnackbar(R.string.STATE_RELEASED)
            }
        }
    }

    private fun manualConnectBluetooth() {
        if (checkAllPermission()) {
            initBluetooth()
            if (bluetoothAdapter!!.isEnabled.not()) { //如果蓝牙没有启动
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, Constant.StartBlueBoothRequestCode) //启动蓝牙
            } else {
                if (connection != null && connection!!.isConnected) {
                    showSnackbar(R.string.bluetooth_has_connected)
                } else {
                    onBlueBoothEnable()
                }
            }
        } else {
            showSnackbar(R.string.permission_not_given_for_bluetooth)
        }
    }

    /**
     * 选择要连接的设备
     */
    private fun chooseConnectDevice() {
        AlertDialog.Builder(this@MainActivity)
            .setTitle(R.string.connection)
            .setCancelable(false)
            .setItems(deviceList.map { it.name }.toTypedArray()) { _, which ->
                val device = deviceList[which]
                btManager?.stopDiscovery()
                connection = btManager?.createConnection(device, this@MainActivity)
                connection!!.connect(null, object : ConnectCallback {
                    override fun onSuccess() {
                        runOnUiThread {
                            logRunOnUi("连接成功")
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(R.string.connect_success)
                                .setMessage(R.string.connect_success_message)
                                .setPositiveButton("好") { _, _ ->
                                    startControllerActivity()
                                }
                                .show()
                        }
                    }

                    override fun onFail(errMsg: String, e: Throwable?) {}
                })
            }
            .setNeutralButton("返回") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ############################## 蓝牙 ############################## End


    // ############################## 按钮 ############################## Start

    /**
     * 打开控制器Activity
     */
    fun startControllerActivity(view: View) {
        startControllerActivity()
    }

    /**
     * 手动申请权限
     */
    fun manualRequestPermission(view: View) {
        requestPermissions(
            Constant.NeedPermission,
            Constant.ManualRequestPermission
        )
    }

    /**
     * 蓝牙
     */
    fun manualConnectBluetooth(view: View) {
        manualConnectBluetooth()
    }

    // ############################## 按钮 ############################## End


    // ############################## 权限 ############################## Start

    /**
     * 申请权限
     */
    private fun requestPermission() {
        if (!checkAllPermission()) requestPermissions(
            Constant.NeedPermission,
            Constant.RequestPermission
        )
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("退出")
            .setMessage("是否要退出控制器？")
            .setPositiveButton("是") { _, _ -> finish() }
            .setNegativeButton("否", null)
            .show()
    }

    /**
     * 检查所有权限
     */
    private fun checkAllPermission(): Boolean {
        var allAllowed = true
        Constant.NeedPermission.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED)
                allAllowed = false
        }
        return allAllowed
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var allAllowed = true
        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED)
                allAllowed = false
        }
        when (requestCode) {
            Constant.ManualRequestPermission -> showSnackbar(if (allAllowed) R.string.permission_given else R.string.permission_not_given)
            Constant.RequestPermission -> if (!allAllowed) showSnackbar(R.string.permission_not_given)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constant.StartBlueBoothRequestCode -> if (resultCode == RESULT_OK) onBlueBoothEnable()
        }
    }

    // ############################## 权限 ############################## End


    // ############################## 其它 ############################## Start

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_bt_disconnect -> {
                BTManager.getInstance().disconnectAllConnections()
                bluetoothStateTextView.text = "蓝牙连接中断"
            }
            R.id.menu_item_about ->
                AlertDialog.Builder(this)
                    .setTitle("关于")
                    .setMessage(
                        "版本号: ${BuildConfig.VERSION_CODE}\n" +
                                "版本名: ${BuildConfig.VERSION_NAME}\n"
                    )
                    .show()
            R.id.menu_item_exit -> finish()
        }
        return true
    }

    private fun showSnackbar(resId: Int) {
        Snackbar.make(coordinatorLayout, resId, Snackbar.LENGTH_LONG).show()
    }

    /**
     * 打开控制器Activity
     */
    private fun startControllerActivity() {
        tlModel?.disableTraining()
        if (checkAllPermission())
            if (startControllerActivityBtn.isEnabled) {
                emergencyStopDiscovery = true
                btManager?.stopDiscovery()

                startActivity(Intent(this, ControllerActivity::class.java))
            } else
                showSnackbar(R.string.training_is_not_loaded)
        else
            showSnackbar(R.string.permission_not_given_cannot_start_controller)
    }

    /**
     * 蓝牙工作条是否显示
     */
    private fun isShowBluetoothWorking(show: Boolean) {
        runOnUiThread {
            logRunOnUi("进度条更改可见性")
            bluetoothWorkingProgress.visibility = if (show) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun onLoss(epoch: Int, loss: Float) {
        val nowProgress = targetProgress.pow(1 - (loss - Constant.TargetTrainLoss))
        val progress = nowProgress / targetProgress * 100
        runOnUiThread {
            logRunOnUi("训练进度更新, loss = $loss")
            trainProgressBar.progress = max(trainProgressBar.progress, progress.toInt())
        }
//        debug("Progress = $progress, Loss = $loss")
        if (loss < Constant.TargetTrainLoss) {
            tlModel?.disableTraining()
            runOnUiThread {
                logRunOnUi("更改开始按钮可操作性")
                startControllerActivityBtn.isEnabled = true
            }
            debug("训练完成")
        }
    }

    // ############################## 其它 ############################## End

}
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
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import cn.wandersnail.bluetooth.*
import cn.wandersnail.commons.observer.Observe
import cn.wandersnail.commons.poster.RunOn
import cn.wandersnail.commons.poster.Tag
import cn.wandersnail.commons.poster.ThreadMode
import com.google.android.material.snackbar.Snackbar
import edu.scut.submarinerobotcontroller.Connector.tlModel
import edu.scut.submarinerobotcontroller.databinding.ActivityMainBinding
import edu.scut.submarinerobotcontroller.tensorflow.TransferLearningModelWrapper
import edu.scut.submarinerobotcontroller.tools.*
import edu.scut.submarinerobotcontroller.ui.viewmodel.MainViewModel
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import java.nio.ByteBuffer
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), EventObserver, TransferLearningModel.LossConsumer {

    private lateinit var database: MyDatabase
    private lateinit var viewModel: MainViewModel
    private lateinit var dataBinding: ActivityMainBinding

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
//        setContentView(R.layout.activity_main)
        dataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        dataBinding.data = viewModel
        dataBinding.lifecycleOwner = this
        database = MyDatabase(this, "main", null, 1)

        coordinatorLayout = findViewById(R.id.main_coordinatorlayout)
        bluetoothWorkingProgress = findViewById(R.id.bluetooth_working)
        bluetoothWorkingProgress.visibility = View.INVISIBLE
        trainProgressBar = findViewById(R.id.train_progress)
        startControllerActivityBtn = findViewById(R.id.btn_start_control_activity)
        bluetoothStateTextView = findViewById(R.id.bluetooth_state)
        bluetoothStateTextView.text = "蓝牙连接中断"

        viewModel.trainingProgressColor.observe(this, androidx.lifecycle.Observer {
            dataBinding.trainProgress.progressTintList = ColorStateList.valueOf(it)
        })

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
        if (trainProgressBar.progress <= 99) {
            debug("需要加载模型")
            Thread {
                tlModel = TransferLearningModelWrapper.getInstance(applicationContext)
                val dataList = database.getData()
                if (dataList.first == Constant.ModelVersion) {
                    debug("训练 从数据库载入资源")
                    val loader = object : ScatteringByteChannel {
                        override fun close() {
                        }

                        override fun isOpen(): Boolean {
                            return true
                        }

                        override fun read(
                            dsts: Array<out ByteBuffer>?,
                            offset: Int,
                            length: Int
                        ): Long {
                            return 0L
                        }

                        override fun read(dsts: Array<ByteBuffer>): Long {
                            for (i in dsts.indices) {
                                dsts[i] = dataList.second[i]
                            }
                            debug("训练 读取 size = ${dsts.size}")
                            return dsts.size.toLong()
                        }

                        override fun read(dst: ByteBuffer?): Int {
                            return 0
                        }
                    }
                    tlModel!!.loadParameters(loader)
                    onTrainingFinished()
                } else {
                    viewModel.trainingProgressColor.postValue(Color.rgb(98, 0, 238))
                    val sampleIndices =
                        if (Constant.AddSampleNumber == -1) Constant.TrainData2Array.indices else 1..Constant.AddSampleNumber
                    for (i in sampleIndices) {
                        trainingList.offer(
                            Pair(
                                "2",
                                BitmapFactory.decodeResource(
                                    applicationContext!!.resources, Constant.TrainData2Array[i]
                                )
                            )
                        )
                    }
                    for (i in sampleIndices) {
                        trainingList.offer(
                            Pair(
                                "4",
                                BitmapFactory.decodeResource(
                                    applicationContext!!.resources, Constant.TrainData4Array[i]
                                )
                            )
                        )
                    }
                    debug("加入训练列表完成")
                    val addSampleRunnable = kotlinx.coroutines.Runnable {
                        while (trainingList.isNotEmpty()) {
                            val trainingObj = trainingList.peek()
                            trainingList.poll()
                            if (trainingObj != null) {
                                tlModel!!.addSample(
                                    Vision.prepareToPredict(trainingObj.second),
                                    trainingObj.first
                                ).get()
                            }
                            preparedSamples++
                            viewModel.trainingProgress.postValue(((preparedSamples.toDouble() / (preparedSamples + trainingList.size).toDouble()) * 100.0).toInt())
                            debug("添加样本剩余 ${trainingList.size}")
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
                        viewModel.trainingProgress.postValue(0)
                        viewModel.trainingProgressColor.postValue(Color.rgb(3, 218, 197))
                        tlModel!!.enableTraining(this)
                    }
                }

            }.start()
        } else {
            debug("无需加载模型")
            viewModel.startControllerActivityBtnEnable.postValue(true)
        }
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
        viewModel.bluetoothWorkingProgressVisibility.postValue(if (show) View.VISIBLE else View.INVISIBLE)
    }

    override fun onLoss(epoch: Int, loss: Float) {
        val nowProgress = targetProgress.pow(1 - (loss - Constant.TargetTrainLoss))
        val progress = nowProgress / targetProgress * 100
        viewModel.trainingProgress.postValue(max(trainProgressBar.progress, progress.toInt()))
        debug("Progress = $progress, Loss = $loss")
        if (loss < Constant.TargetTrainLoss) onTrainingFinished()
    }

    private fun onTrainingFinished(loadFromDb: Boolean = false) {
        viewModel.trainingProgressColor.postValue(Color.rgb(3, 218, 197))
        viewModel.trainingProgress.postValue(100)
        viewModel.startControllerActivityBtnEnable.postValue(true)
        tlModel?.disableTraining()
        debug("训练 完成")

        if (loadFromDb.not()) {
            tlModel?.saveParameters(object : GatheringByteChannel {
                override fun close() {
                }

                override fun isOpen(): Boolean {
                    return true
                }

                override fun write(srcs: Array<out ByteBuffer>?, offset: Int, length: Int): Long {
                    return 0L
                }

                override fun write(srcs: Array<ByteBuffer>): Long {
                    debug("训练 写入 size = ${srcs.size}")
                    database.deleteAll()
                    database.insertData(Pair(Constant.ModelVersion, srcs))
                    return srcs.size.toLong()
                }

                override fun write(src: ByteBuffer?): Int {
                    return 0
                }
            })
        }
    }

    // ############################## 其它 ############################## End

}
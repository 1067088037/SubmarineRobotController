package edu.scut.submarinerobotcontroller.ui.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.R
import edu.scut.submarinerobotcontroller.opmode.AutomaticController
import edu.scut.submarinerobotcontroller.opmode.RobotMode
import edu.scut.submarinerobotcontroller.tensorflow.ImageUtils
import edu.scut.submarinerobotcontroller.tools.Vision
import edu.scut.submarinerobotcontroller.tools.logRunOnUi
import edu.scut.submarinerobotcontroller.tools.radToDegree
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCamera2View
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class AutoFragment : Fragment(), CameraBridgeViewBase.CvCameraViewListener2 {

    lateinit var camera2View: JavaCamera2View
    lateinit var commandTextView: TextView
    lateinit var commandScrollView: ScrollView
    lateinit var signalLight: TextView
    private lateinit var orientationAnglesXTextView: TextView
    private lateinit var orientationAnglesYTextView: TextView
    private lateinit var orientationAnglesZTextView: TextView
    private lateinit var motorPowerProgressBarList: Array<ProgressBar>
    private lateinit var degreeWithTurnTextView: TextView
    private var lastUpdateDegreeTime = System.currentTimeMillis()
    private var commandTextList = arrayListOf<String>()

    private var cameraFrameWidth = 0
    private var cameraFrameHeight = 0

    private var lastSetSignalParameters = arrayOf(0, 0, 0, 0, "")
    private val lastOrientationText: ArrayList<String> = arrayListOf()

    init {
        //将Fragment内部方法放到Connector
        Connector.updateMotorPower = this::updateMotorPower
        Connector.updateAllMotorPower = this::updateAllMotorPower
        Connector.updateCommand = this::updateCommand
        Connector.updateOrientationAnglesText = this::updateOrientationAnglesText
        Connector.updateOrientationAngles = this::updateOrientationAngles
        Connector.updateDegreeWithTurn = this::updateDegreeWithTurn
        Connector.setCamera2View = this::setCamera2View
        Connector.setSignal = this::setSignal
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_auto, container, false)

        signalLight = view.findViewById(R.id.signal_light)
        setSignal()
        orientationAnglesXTextView = view.findViewById(R.id.text_orientation_angles_x)
        orientationAnglesYTextView = view.findViewById(R.id.text_orientation_angles_y)
        orientationAnglesZTextView = view.findViewById(R.id.text_orientation_angles_z)
        motorPowerProgressBarList = arrayOf(
            view.findViewById(R.id.progress_motor_0_power),
            view.findViewById(R.id.progress_motor_1_power),
            view.findViewById(R.id.progress_motor_2_power),
            view.findViewById(R.id.progress_motor_3_power),
            view.findViewById(R.id.progress_motor_4_power),
            view.findViewById(R.id.progress_motor_5_power)
        )
        degreeWithTurnTextView = view.findViewById(R.id.degreeWithTurn)
        commandTextView = view.findViewById(R.id.text_command)
        commandScrollView = view.findViewById(R.id.scrollView_command)

        camera2View = view.findViewById(R.id.camera2_view)
        camera2View.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (cameraFrameWidth == 0) return
                val params = camera2View.layoutParams as LinearLayout.LayoutParams
                val width = camera2View.measuredWidth
                val height = camera2View.measuredHeight
                params.width = width
                params.height = cameraFrameHeight * width / cameraFrameWidth
                camera2View.layoutParams = params
                camera2View.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        camera2View.setCameraPermissionGranted()
        camera2View.setCvCameraViewListener(this)

        updateCommand(getString(R.string.command_default))
        return view
    }

    /**
     * 预览画面
     */
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val rgba = inputFrame!!.rgba()

        Core.rotate(rgba, rgba, Core.ROTATE_90_CLOCKWISE)//90度翻转
        Imgproc.resize(
            rgba,
            rgba,
            Size(camera2View.width.toDouble(), camera2View.height.toDouble())
        )

        val res = rgba.clone()
        if (Connector.mainController?.robotMode() == RobotMode.Running) {
            val hsv = Mat()
            Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGB2HSV)

            //捕捉白色赛道
            val whiteCatch =
                Vision.catchByColor(hsv, Scalar(0.0, 0.0, 180.0), Scalar(180.0, 35.0, 255.0))
            if (whiteCatch.second.isNotEmpty()) {
//                val pipe = Mat.zeros(res.size(), CvType.CV_8UC1)
//                Imgproc.drawContours(
//                    pipe, whiteCatch.second, 0,
//                    Scalar(255.0, 255.0, 255.0), 5, 1
//                ) //二值化图像上画出水管轮廓线

                val whiteObject = whiteCatch.second[0]
                Imgproc.drawContours(
                    res, whiteCatch.second, 0,
                    Scalar(255.0, 0.0, 0.0), 2, 1
                ) //红线包围白色水管

                if (Connector.mainController is AutomaticController)
                    (Connector.mainController as AutomaticController).onPipeCatch(whiteObject, res)
            }

            //捕捉黑色障碍物
            val blackCatch =
                Vision.catchByColor(hsv, Scalar(0.0, 0.0, 0.0), Scalar(180.0, 255.0, 100.0), 10, 3)
            if (blackCatch.second.isNotEmpty()) {
                val blackObject = blackCatch.second[0]
                Imgproc.drawContours(
                    res, blackCatch.second, 0,
                    Scalar(0.0, 0.0, 255.0), 4, 1
                ) //蓝线包围黑色目标物
//                debug("面积 = ${Imgproc.contourArea(blackObject)}")
                if (Imgproc.contourArea(blackObject) > Constant.MinBlackObjArea) {
                    val bitmap = Bitmap.createBitmap(
                        rgba.width(),
                        rgba.height(),
                        Bitmap.Config.ARGB_8888
                    )
                    Utils.matToBitmap(rgba, bitmap)
//                    debug("mat: w = ${rgba.width()}, h = ${rgba.height()}, bitmap: w = ${bitmap.width}, h = ${bitmap.height}")
                    Connector.needToBePredicted = ImageUtils.prepareCameraImage(bitmap, 0)
                } else setSignal(text = "物体太远")
            } else setSignal(text = "没有目标")

        } else setSignal(text = "不在运行")

        return res
    }

    /**
     * 设置信号颜色
     */
    private fun setSignal(
        a: Int = 64,
        r: Int = 0,
        g: Int = 0,
        b: Int = 0,
        text: String = "Signal"
    ) {
        if (a == lastSetSignalParameters[0] && r == lastSetSignalParameters[1] && g == lastSetSignalParameters[2] && b == lastSetSignalParameters[3] && text == lastSetSignalParameters[4]) {
            //不修改UI
        } else {
            activity?.runOnUiThread {
                logRunOnUi("控制台颜色")
                signalLight.setBackgroundColor(Color.argb(a, r, g, b))
                signalLight.setTextColor(Color.argb(a, 255, 255, 255))
                signalLight.text = text
            }
            lastSetSignalParameters[0] = a
            lastSetSignalParameters[1] = r
            lastSetSignalParameters[2] = g
            lastSetSignalParameters[3] = b
            lastSetSignalParameters[4] = text
        }
    }

    private fun updateMotorPower(port: Int, power: Double) {
        if (motorPowerProgressBarList[port].progress != abs(power * 100.0).toInt()) {
            activity?.runOnUiThread {
                logRunOnUi("更新马达功率")
                motorPowerProgressBarList[port].progress = abs(power * 100.0).toInt()
                motorPowerProgressBarList[port].progressTintList = ColorStateList.valueOf(
                    if (power >= 0) activity?.getColor(R.color.positive_green) ?: 0
                    else activity?.getColor(R.color.negative_red) ?: 0
                )
            }
        }
    }

    private fun updateAllMotorPower(powers: DoubleArray) {
        if (powers.size != 6) {
            updateCommand("电机功率更新异常，数目不是6个")
            return
        }
        for (i in 0..5) {
            updateMotorPower(i, powers[i])
        }
    }

    /**
     * 更新方位角文字
     */
    private fun updateOrientationAnglesText(str: ArrayList<String>) {
        if (activity == null) return
        if (str != lastOrientationText) {
            if (str.isEmpty())
                for (i in 1..3) {
                    str.add(getString(R.string.orientation_angles_null))
                }
            activity?.runOnUiThread {
                logRunOnUi("更新方位角")
                orientationAnglesXTextView.text = str[0]
                orientationAnglesYTextView.text = str[1]
                orientationAnglesZTextView.text = str[2]
            }
            lastOrientationText.clear()
            str.forEach {
                lastOrientationText.add(it)
            }
        }
    }

    /**
     * 更新方位角
     */
    private fun updateOrientationAngles(values: FloatArray) {
        updateOrientationAnglesText(
            arrayListOf(
                String.format("%.0f°", radToDegree(values[0])),
                String.format("%.0f°", radToDegree(values[1])),
                String.format("%.0f°", radToDegree(values[2]))
            )
        )
    }

    private fun updateDegreeWithTurn(str: String) {
        if (System.currentTimeMillis() - lastUpdateDegreeTime >= 500) {
            lastUpdateDegreeTime = System.currentTimeMillis()
            if (degreeWithTurnTextView.text != str) {
                activity?.runOnUiThread {
                    degreeWithTurnTextView.text = str
                }
            }
        }
    }

    /**
     * 更新指令
     * @param string 不需要加空行符
     */
    private fun updateCommand(string: String) {
        val calendar = Calendar.getInstance()
        commandTextList.add(
            "${String.format("%02d", calendar.get(Calendar.HOUR))}:" +
                    "${String.format("%02d", calendar.get(Calendar.MINUTE))}:" +
                    "${String.format("%02d", calendar.get(Calendar.SECOND))}:" +
                    "${String.format("%03d", calendar.get(Calendar.MILLISECOND))}:\t " +
                    "$string\n"
        )
        if (this::commandTextView.isInitialized) {
            activity?.runOnUiThread {
                logRunOnUi("更新指令")
                commandTextView.text = kotlin.run {
                    var temp = ""
                    val tempArray = commandTextList.toArray()
                    if (tempArray.size <= Constant.CommandMaxLine) {
                        tempArray.forEach { // TODO: 2021/4/6
                            temp += it
                        }
                    } else {
                        temp = "${tempArray.size - Constant.CommandMaxLine}条命令被折叠\n"
                        for (i in tempArray.size - Constant.CommandMaxLine until tempArray.size) {
                            temp += tempArray[i]
                        }
                    }
                    temp
                }
                commandScrollView.post { commandScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        camera2View.disableView()
    }

    override fun onResume() {
        super.onResume()
        camera2View.enableView()
    }

    private fun setCamera2View(enable: Boolean) {
        if (enable) camera2View.enableView()
        else camera2View.disableView()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var autoFragment: AutoFragment? = null

        @JvmStatic
        fun newInstance(): AutoFragment {
            if (autoFragment == null)
                autoFragment = AutoFragment()
            return autoFragment!!
        }

        @JvmStatic
        fun destroy() {
            autoFragment = null
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        cameraFrameWidth = width
        cameraFrameHeight = height
//        camera2View.enableFpsMeter()
    }

    override fun onCameraViewStopped() {}

}
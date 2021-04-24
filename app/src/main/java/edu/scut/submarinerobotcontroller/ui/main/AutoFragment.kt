package edu.scut.submarinerobotcontroller.ui.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.R
import edu.scut.submarinerobotcontroller.databinding.FragmentAutoBinding
import edu.scut.submarinerobotcontroller.opmode.AutomaticController
import edu.scut.submarinerobotcontroller.opmode.RobotMode
import edu.scut.submarinerobotcontroller.tools.*
import edu.scut.submarinerobotcontroller.ui.viewmodel.ControllerSharedViewModel
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCamera2View
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class AutoFragment : Fragment(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var viewModel: ControllerSharedViewModel
    private lateinit var dataBinding: FragmentAutoBinding

    lateinit var camera2View: JavaCamera2View
    lateinit var commandTextView: TextView
    private var lastUpdateDegreeTime = System.currentTimeMillis()
    private var commandTextList = arrayListOf<String>()

    private var foldCommandTextNumber = 0
    private var cameraFrameWidth = 0
    private var cameraFrameHeight = 0

    private val lastOrientationText: Array<String> = Array(3) { "" }

    init {
        //将Fragment内部方法放到Connector
//        Connector.updateMotorPower = this::updateMotorPower
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
    ): View {
        dataBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_auto, container, true)
        viewModel = ViewModelProvider(requireActivity()).get(
            "ControllerSharedViewModel",
            ControllerSharedViewModel::class.java
        )
        dataBinding.data = viewModel
        dataBinding.lifecycleOwner = this

        viewModel.motorPower.observe(this, androidx.lifecycle.Observer { doubles ->
            val green = activity!!.getColor(R.color.positive_green)
            val red = activity!!.getColor(R.color.negative_red)
            viewModel.motorPowerProgress.value =
                doubles.map { abs(it * 100).toInt() }.toTypedArray()
            viewModel.motorPowerColor.value =
                doubles.map { if (it >= 0) green else red }.toTypedArray()
        })

        setSignal()
        commandTextView = dataBinding.textCommand

        camera2View = dataBinding.camera2View
        camera2View.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                debug("观察者修改布局")
                if (cameraFrameWidth == 0) return
                val params = camera2View.layoutParams as LinearLayout.LayoutParams
//                params.width = cameraFrameWidth
//                params.height = cameraFrameWidth * 4 / 3
                val width = camera2View.measuredWidth
                val height = camera2View.measuredHeight
                params.width = width
                params.height = width * cameraFrameWidth / cameraFrameHeight
                camera2View.layoutParams = params
                camera2View.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        camera2View.setCameraPermissionGranted()
        camera2View.setCvCameraViewListener(this)

        updateCommand(getString(R.string.command_default))
        return dataBinding.root
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
                Vision.catchByColor(hsv, Constant.WhiteColorLower, Constant.WhiteColorUpper)
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
                Vision.catchByColor(hsv, Constant.BlackColorLower, Constant.BlackColorUpper, 10, 3)
            if (blackCatch.second.isNotEmpty()) {
                val blackObject = blackCatch.second[0]
                Imgproc.drawContours(
                    res, blackCatch.second, 0,
                    Scalar(0.0, 0.0, 255.0), 4, 1
                ) //蓝线包围黑色目标物
//                debug("面积 = ${Imgproc.contourArea(blackObject)}")
                if (Imgproc.contourArea(blackObject) > Constant.MinBlackObjArea) {
                    val needToPredict = Vision.prepareToPredict(rgba, hsv)
                    Connector.needToBePredicted = needToPredict.first
//                    return needToPredict.second
                } else setSignal(text = "物体太远")
            } else setSignal(text = "没有目标")

        } else setSignal(text = "不在运行")

        return res
    }

    /**
     * 设置信号颜色
     */
    private fun setSignal(
        a: Int = 32,
        r: Int = 0,
        g: Int = 0,
        b: Int = 0,
        text: String = "Signal"
    ) {
        viewModel.signal.postValue(text)
        viewModel.signalTextColor.postValue(Color.argb(a, 255, 255, 255))
        viewModel.signalBackgroundColor.postValue(Color.argb(a, r, g, b))
    }

    /**
     * 更新方位角文字
     */
    private fun updateOrientationAnglesText(str: ArrayList<String>) {
        if (activity == null) return
        if (str != lastOrientationText) {
            if (str.isEmpty()) {
                for (i in 1..3) {
                    str.add(getString(R.string.orientation_angles_null))
                }
            }
            for ((i, value) in str.withIndex()) {
                lastOrientationText[i] = value
            }
            if (this::viewModel.isInitialized) viewModel.orientationAngles.postValue(
                lastOrientationText
            )
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
            if (dataBinding.degreeWithTurn.text != str) {
                viewModel.degreeWithTurn.postValue(str)
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
            "${String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))}:" +
                    "${String.format("%02d", calendar.get(Calendar.MINUTE))}:" +
                    "${String.format("%02d", calendar.get(Calendar.SECOND))}:" +
                    "${String.format("%03d", calendar.get(Calendar.MILLISECOND))}:\t " +
                    "$string\n"
        )
        if (commandTextList.size > Constant.CommandMaxLine) {
            commandTextList.removeAt(0)
            foldCommandTextNumber++
        }
        val tempArray = commandTextList.toArray()
        var temp = "${foldCommandTextNumber}条命令被折叠\n"
        for (i in tempArray) {
            temp += i
        }
        temp += "\n"

        if (this::viewModel.isInitialized) {
            viewModel.command.postValue(temp)
            dataBinding.scrollViewCommand.post { dataBinding.scrollViewCommand.fullScroll(ScrollView.FOCUS_DOWN) }
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
        debug("Width = $width, Height = $height")
        cameraFrameWidth = width
        cameraFrameHeight = height
//        camera2View.enableFpsMeter()
    }

    override fun onCameraViewStopped() {}

}
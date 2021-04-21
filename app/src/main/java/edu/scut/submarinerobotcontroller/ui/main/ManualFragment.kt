package edu.scut.submarinerobotcontroller.ui.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.R
import edu.scut.submarinerobotcontroller.databinding.FragmentManualBinding
import edu.scut.submarinerobotcontroller.opmode.ManualController
import edu.scut.submarinerobotcontroller.opmode.RobotControllerMode
import edu.scut.submarinerobotcontroller.opmode.Servo
import edu.scut.submarinerobotcontroller.tools.debug
import edu.scut.submarinerobotcontroller.tools.logRunOnUi
import edu.scut.submarinerobotcontroller.ui.view.GamepadStick
import edu.scut.submarinerobotcontroller.ui.view.MotorSideView
import edu.scut.submarinerobotcontroller.ui.view.MotorTopView
import edu.scut.submarinerobotcontroller.ui.viewmodel.ControllerSharedViewModel
import kotlin.math.sign

class ManualFragment : Fragment() {

    private lateinit var viewModel: ControllerSharedViewModel
    private lateinit var dataBinding: FragmentManualBinding

    private lateinit var leftStick: GamepadStick
    private lateinit var rightStick: GamepadStick
    private lateinit var resetDepthPowerButton: Button
    private lateinit var servoBoundSwitch: SwitchCompat
    private var motorSide: Array<MotorSideView> = arrayOf()
    private var motorTop: Array<MotorTopView> = arrayOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dataBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_manual, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(
            "ControllerSharedViewModel",
            ControllerSharedViewModel::class.java
        )
        dataBinding.data = viewModel
        dataBinding.lifecycleOwner = this

        motorSide = arrayOf(
            dataBinding.motor0WaterView,
            dataBinding.motor1WaterView,
            dataBinding.motor2WaterView,
            dataBinding.motor3WaterView
        )
        motorTop = arrayOf(
            dataBinding.motor4WaterView,
            dataBinding.motor5WaterView
        )

        viewModel.motorPower.observe(this, Observer {
            for (i in 0..3) {
                motorSide[i].motorPower = it[i].toFloat()
            }
            for (i in 4..5) {
                motorTop[i - 4].motorPower = it[i].toFloat()
            }
            viewModel.depthPowerText.value = (it[4] * 100).toInt().toString()
        })

        dataBinding.servo1Position.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (servoBoundSwitch.isChecked) dataBinding.servo2Position.progress = progress
                viewModel.leftServoText.value = "左伺服 $progress"
                (Connector.mainController as ManualController).leftServo.position =
                    progress / 180.0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        dataBinding.servo2Position.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (servoBoundSwitch.isChecked) dataBinding.servo1Position.progress = progress
                viewModel.rightServoText.value = "右伺服 $progress"
                (Connector.mainController as ManualController).rightServo.position =
                    progress / 180.0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        viewModel.leftServoText.value = "左伺服"
        viewModel.rightServoText.value = "右伺服"

        leftStick = dataBinding.gamepadLeftStick
        leftStick.isLimitIn4Direction = false
        leftStick.setOnNavAndSpeedListener(object : GamepadStick.OnDirectionAndSpeedListener {
            override fun onDirectionAndSpeed(x: Float, y: Float, direction: Float, speed: Float) {
                Connector.refreshLeftStickX(x)
                Connector.refreshLeftStickY(y)
//                debug("left x:$x y:$y & direction: $direction & speed: $speed")
            }
        })

        rightStick = dataBinding.gamepadRightStick
        rightStick.isLimitIn4Direction = true
        rightStick.setOnNavAndSpeedListener(object : GamepadStick.OnDirectionAndSpeedListener {
            override fun onDirectionAndSpeed(x: Float, y: Float, direction: Float, speed: Float) {
                Connector.refreshRightStickX(x)
                Connector.refreshRightStickY(y)
//                debug("right x:$x y:$y & direction: $direction & speed: $speed")
            }
        })

        resetDepthPowerButton = dataBinding.resetDepthPower
        resetDepthPowerButton.setOnClickListener {
            (Connector.mainController as ManualController).depthPower = 0.0
        }

        servoBoundSwitch = dataBinding.servoBound

        servoBoundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) dataBinding.servo2Position.progress = dataBinding.servo1Position.progress
        }

        return dataBinding.root
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var manualFragment: ManualFragment? = null

        @JvmStatic
        fun newInstance(): ManualFragment {
            if (manualFragment == null)
                manualFragment = ManualFragment()
            return manualFragment!!
        }

        @JvmStatic
        fun destroy() {
            manualFragment = null
        }
    }
}
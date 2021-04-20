package edu.scut.submarinerobotcontroller.ui.main

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
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.R
import edu.scut.submarinerobotcontroller.opmode.ManualController
import edu.scut.submarinerobotcontroller.opmode.RobotControllerMode
import edu.scut.submarinerobotcontroller.opmode.Servo
import edu.scut.submarinerobotcontroller.tools.debug
import edu.scut.submarinerobotcontroller.tools.logRunOnUi
import edu.scut.submarinerobotcontroller.ui.view.GamepadStick
import edu.scut.submarinerobotcontroller.ui.view.MotorSideView
import edu.scut.submarinerobotcontroller.ui.view.MotorTopView
import kotlin.math.sign

class ManualFragment : Fragment() {

    private lateinit var leftStick: GamepadStick
    private lateinit var rightStick: GamepadStick
    private lateinit var resetDepthPowerButton: Button
    private lateinit var servoBoundSwitch: SwitchCompat

    private var motorSide: Array<MotorSideView> = arrayOf()
    private var motorTop: Array<MotorTopView> = arrayOf()
    private var servoText: Array<TextView> = arrayOf()
    private var servoSeekBar: Array<SeekBar> = arrayOf()

    private lateinit var depthPowerText: TextView

    init {
        Connector.updateMotorPowerWater = ::updateMotorPowerWater
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_manual, container, false)!!

        motorSide = arrayOf(
            view.findViewById(R.id.motor_0_water_view),
            view.findViewById(R.id.motor_1_water_view),
            view.findViewById(R.id.motor_2_water_view),
            view.findViewById(R.id.motor_3_water_view)
        )
        motorTop = arrayOf(
            view.findViewById(R.id.motor_4_water_view),
            view.findViewById(R.id.motor_5_water_view)
        )

        servoText = arrayOf(view.findViewById(R.id.servo1Text), view.findViewById(R.id.servo2Text))
        servoSeekBar =
            arrayOf(view.findViewById(R.id.servo1Position), view.findViewById(R.id.servo2Position))

        servoSeekBar[0].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (servoBoundSwitch.isChecked) servoSeekBar[1].progress = progress
                servoText[0].text = kotlin.run { "左伺服 $progress" }
                (Connector.mainController as ManualController).leftServo.position =
                    progress / 180.0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        servoSeekBar[1].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (servoBoundSwitch.isChecked) servoSeekBar[0].progress = progress
                servoText[1].text = kotlin.run { "右伺服 $progress" }
                (Connector.mainController as ManualController).rightServo.position =
                    progress / 180.0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        leftStick = view.findViewById(R.id.gamepadLeftStick)
        leftStick.isLimitIn4Direction = false
        leftStick.setOnNavAndSpeedListener(object : GamepadStick.OnDirectionAndSpeedListener {
            override fun onDirectionAndSpeed(x: Float, y: Float, direction: Float, speed: Float) {
                Connector.refreshLeftStickX(x)
                Connector.refreshLeftStickY(y)
//                debug("left x:$x y:$y & direction: $direction & speed: $speed")
            }
        })

        rightStick = view.findViewById(R.id.gamepadRightStick)
        rightStick.isLimitIn4Direction = true
        rightStick.setOnNavAndSpeedListener(object : GamepadStick.OnDirectionAndSpeedListener {
            override fun onDirectionAndSpeed(x: Float, y: Float, direction: Float, speed: Float) {
                Connector.refreshRightStickX(x)
                Connector.refreshRightStickY(y)
//                debug("right x:$x y:$y & direction: $direction & speed: $speed")
            }
        })

        resetDepthPowerButton = view.findViewById(R.id.resetDepthPower)
        resetDepthPowerButton.setOnClickListener {
            (Connector.mainController as ManualController).depthPower = 0.0
        }

        depthPowerText = view.findViewById(R.id.text_depth_power)
        servoBoundSwitch = view.findViewById(R.id.servoBound)

        servoBoundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) servoSeekBar[1].progress = servoSeekBar[0].progress
        }

        return view
    }

    private fun updateMotorPowerWater(power: Array<Pair<Int, Double>>) {
        logRunOnUi("手动界面 更新马达功率")
        activity?.runOnUiThread {
            for ((port, motorPower) in power) {
                if (port in 0..3) {
                    motorSide[port].motorPower = motorPower.toFloat()
                }
                if (port in 4..5) {
                    motorTop[port - 4].motorPower = motorPower.toFloat()
                    if (depthPowerText.text != (motorPower * 100.0).toInt().toString()) {
                        depthPowerText.text = (motorPower * 100.0).toInt().toString()
                    }
                }
            }
        }
    }

    companion object {
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
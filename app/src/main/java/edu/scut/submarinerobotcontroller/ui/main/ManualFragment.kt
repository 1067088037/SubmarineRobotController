package edu.scut.submarinerobotcontroller.ui.main

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.R
import edu.scut.submarinerobotcontroller.opmode.ManualController
import edu.scut.submarinerobotcontroller.opmode.RobotControllerMode
import edu.scut.submarinerobotcontroller.tools.debug
import edu.scut.submarinerobotcontroller.tools.logRunOnUi
import edu.scut.submarinerobotcontroller.ui.view.GamepadStick
import edu.scut.submarinerobotcontroller.ui.view.MotorSideView
import edu.scut.submarinerobotcontroller.ui.view.MotorTopView
import kotlin.math.sign

class ManualFragment : Fragment() {

    private lateinit var leftStick: GamepadStick
    private lateinit var rightStick: GamepadStick

    private var motorSide: Array<MotorSideView> = arrayOf()
    private var motorTop: Array<MotorTopView> = arrayOf()

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

        depthPowerText = view.findViewById(R.id.text_depth_power)

        return view
    }

    private fun updateMotorPowerWater(port: Int, power: Double) {
        if (motorSide.size != 4 || motorTop.size != 2) return
        if (port in 0..3) {
            motorSide[port].motorPower = power.toFloat()
        }
        if (port in 4..5) {
            motorTop[port - 4].motorPower = power.toFloat()
            if (depthPowerText.text != (power * 100.0).toInt().toString()) {
                activity?.runOnUiThread {
                    logRunOnUi("更新深度功率")
                    depthPowerText.text = (power * 100.0).toInt().toString()
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
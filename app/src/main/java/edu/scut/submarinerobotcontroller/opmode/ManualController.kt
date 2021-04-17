package edu.scut.submarinerobotcontroller.opmode

import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.tools.Clock
import edu.scut.submarinerobotcontroller.tools.command
import edu.scut.submarinerobotcontroller.tools.debug
import edu.scut.submarinerobotcontroller.tools.limit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class ManualController : BaseController() {

    var depthPower = 0.0
    private var depthControlClock = Clock()

    override fun run() {
        while (robotMode() != RobotMode.Stop) {
            val loopStartTime = System.currentTimeMillis()
            if (robotMode() == RobotMode.Pause) {
                depthPower = 0.0
                setSidePower(0.0, 0.0, 0.0, 0.0)
                setTopPower(0.0)
            } else {
                val x = Connector.refreshLeftStickX().toDouble()
                val y = Connector.refreshLeftStickY().toDouble()
                val t = Connector.refreshRightStickX().toDouble()
                setSidePower(
                    y + x + t,
                    y - x - t,
                    y + x - t,
                    y - x + t
                )
                val rightStickY = Connector.refreshRightStickY().toDouble()
                if (rightStickY != 0.0) {
                    if (depthControlClock.getMillSeconds() >= 10) {
                        val changedPower = sign(rightStickY) * abs(rightStickY).pow(3) * 0.04
                        depthPower += changedPower
                        depthPower = limit(depthPower, -1.0, 1.0)
                        depthControlClock.reset()
                    }
                }
//                debug("DepthPower = $depthPower")
                setTopPower(depthPower)
            }
//            debug("One Loop Time = ${System.currentTimeMillis() - loopStartTime}")
            Thread.sleep(10)
        }
    }

    override fun onRobotModeChanged(robotMode: RobotMode) {

    }

}
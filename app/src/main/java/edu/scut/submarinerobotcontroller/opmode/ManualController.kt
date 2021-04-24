package edu.scut.submarinerobotcontroller.opmode

import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.tools.*
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class ManualController : BaseController() {

    var depthPower = 0.0
    private var depthControlClock = Clock()
    private var monitorClock = Clock()
    val monitor = Monitor()

    private val db = MyDatabase.getInstance()

    override fun run() {
        monitorClock.reset()
        while (robotMode() != RobotMode.Stop) {
            val loopStartTime = System.currentTimeMillis()
            if (robotMode() == RobotMode.Pause) {
                depthPower = 0.0
                setSidePower(0.0, 0.0, 0.0, 0.0)
                setTopPower(0.0)
            } else {
                val x = Connector.refreshLeftStickX().toDouble() / 3
                val y = Connector.refreshLeftStickY().toDouble() / 2
                val t = Connector.refreshRightStickX().toDouble() / 2
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
            Thread.sleep(20)

            monitor.record(
                monitorClock.getMillSeconds().toInt(),
                motorArray.map { it.power }.toTypedArray()
            )
        }

        val calendar: Calendar = Calendar.getInstance()
        val date = "${String.format("%02d", calendar.get(Calendar.DATE))} " +
                "${String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))}:" +
                "${String.format("%02d", calendar.get(Calendar.MINUTE))}:" +
                String.format("%02d", calendar.get(Calendar.SECOND))
        monitor.dataList.description = "共记录${monitorClock.getSeconds()}秒 $date"
        db.insertData(monitor.dataList)
    }

    override fun onRobotModeChanged(robotMode: RobotMode) {

    }

}
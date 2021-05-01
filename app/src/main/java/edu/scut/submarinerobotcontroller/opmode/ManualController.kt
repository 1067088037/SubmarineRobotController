package edu.scut.submarinerobotcontroller.opmode

import android.app.AlertDialog
import android.app.Application
import android.content.Context
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.tools.*
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class ManualController(val context: Context, val runOnUiThread: (runnable: Runnable) -> Unit) :
    BaseController() {

    var depthPower = 0.0
    private var depthControlClock = Clock()
    private var monitorClock = Clock()
    private val monitor = Monitor()

    private val db = MyDatabase.getInstance()

    override fun run() {
        monitorClock.reset()
        while (robotMode() != RobotMode.Stop) {
//            val loopStartTime = System.currentTimeMillis()
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

        runOnUiThread(Runnable {
            AlertDialog.Builder(context)
                .setTitle("监测器")
                .setMessage("是否记录本次的监测数据？\n${monitor.dataList.description}")
                .setPositiveButton("确定") { _, _ ->
                    db.insertData(monitor.dataList)
                }
                .setNegativeButton("取消", null)
                .show()
        })
    }

    override fun onRobotModeChanged(robotMode: RobotMode) {

    }

}
package edu.scut.submarinerobotcontroller.opmode

import android.os.SystemClock
import edu.scut.submarinerobotcontroller.tools.MyDatabase
import edu.scut.submarinerobotcontroller.tools.debug
import kotlin.math.abs

class Monitor {

    var dataList = MyDatabase.TimePowerDataList.input("", "")

    fun input(data: MyDatabase.TimePowerDataList) {
        dataList = data
    }

    fun record(time: Int, powers: Array<Double>) {
        record(MyDatabase.TimePowerData(time, powers))
    }

    private fun record(input: MyDatabase.TimePowerData) {
        dataList.dataList.add(input)
        debug("记录数据 = ${input.powerList} when ${input.time}")
    }

    fun execute(time: Int): Array<Double>? {
        dataList.dataList.forEach {
            if (abs(it.time - time) <= 20) {
                debug("执行 Time = ${it.time}")
                return it.powerList
            }
        }

        debug("执行结束")
        return null
    }

}
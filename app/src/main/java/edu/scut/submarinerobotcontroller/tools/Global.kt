package edu.scut.submarinerobotcontroller.tools

import android.util.Log
import edu.scut.submarinerobotcontroller.Connector

//全局调试
fun debug(any: Any) = Log.d("调试", any.toString())

fun logRunOnUi(any: Any) = debug("runOnUIThread = $any")

fun <T> limit(value: T, min: T, max: T): T {
    val valueNumber = (value as Number).toDouble()
    val minNumber = (min as Number).toDouble()
    val maxNumber = (max as Number).toDouble()
    return when (valueNumber) {
        in -Double.MAX_VALUE..minNumber -> min
        in maxNumber..Double.MAX_VALUE -> max
        else -> value
    }
}

//更新指令
fun command(string: String) {
    if (Connector.updateCommand != null)
        Connector.updateCommand!!(string)
}
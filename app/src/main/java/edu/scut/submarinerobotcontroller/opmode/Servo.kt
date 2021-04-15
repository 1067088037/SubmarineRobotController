package edu.scut.submarinerobotcontroller.opmode

import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.tools.limit
import java.util.*

/**
 * 伺服
 */
class Servo constructor(
    val controller: BaseController,
    val name: String,
    val port: Int,
    var direction: Direction = Direction.Forward
) {

    enum class Direction {
        Forward, //前进方向
        Reserve //反向后所有功率取相反数
    }

    var position: Double
        set(value) {
            if (controller.robotMode(null) == RobotMode.Running) {
                val inputPosition = limit(value, 0.0, 1.0)
                setOrGetPosition(inputPosition)
                val actualPosition =
                    if (direction == Direction.Forward) inputPosition else -inputPosition
            }
        }
        get() = setOrGetPosition(Double.NaN)

    private var internalPosition: Double = 0.0

    @Synchronized
    private fun setOrGetPosition(power: Double = Double.NaN): Double {
        if (power.isNaN().not()) internalPosition = power
        return internalPosition
    }

    //获取发送给硬件的功率
    fun getHardwarePosition(): Byte {
        val pos = (position * Constant.ServoMaxPosition).toInt()
        val position =
            if (direction == Direction.Forward) pos - 90 else Constant.ServoMaxPosition - pos - 90
        if (position !in -90..89) throw IllegalStateException("Servo 位置越界 位置=$position")
        return position.toByte()
    }
}

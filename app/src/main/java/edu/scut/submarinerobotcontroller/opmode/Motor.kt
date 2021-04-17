package edu.scut.submarinerobotcontroller.opmode

import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.Constant
import edu.scut.submarinerobotcontroller.tools.debug
import edu.scut.submarinerobotcontroller.tools.limit
import java.security.InvalidParameterException
import java.util.*
import kotlin.math.abs

/**
 * 马达
 */
class Motor constructor(
    val controller: BaseController,
    val name: String,
    val port: Int,
    var direction: Direction = Direction.Forward
) {

    enum class Direction {
        Forward, //前进方向
        Reserve //反向后所有功率取相反数
    }

    var power: Double
        set(value) {
            val inputPower =
                if (controller.robotMode(null) == RobotMode.Running)
                    limit(value, -1.0, 1.0)
                else 0.0
            setOrGetPower(inputPower)
            Connector.updateMotorPower(port, inputPower)
            Connector.updateMotorPowerWater(port, inputPower)
            val actualPower = if (direction == Direction.Forward) inputPower else -inputPower
        }
        get() = setOrGetPower(Double.NaN)

    private var internalPower: Double = 0.0

    @Synchronized
    private fun setOrGetPower(power: Double = Double.NaN): Double {
        if (power.isNaN().not()) internalPower = power
        return internalPower
    }

    //获取发送给硬件的功率
    fun getHardwarePower(): Byte {
        val power = (power * Constant.MotorMaxPower * if (direction == Direction.Forward) 1 else -1)
        if (power !in -100.0..100.0) throw IllegalStateException("Motor 功率越界 功率=$power")
        return power.toInt().toByte()
    }
}

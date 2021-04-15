package edu.scut.submarinerobotcontroller.opmode

enum class RobotMode {
    Running,
    Pause,
    Stop,
    WaitForStart
}

interface IRobotMode {
    fun onRobotModeChanged(robotMode: RobotMode)
}
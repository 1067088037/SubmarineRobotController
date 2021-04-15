package edu.scut.submarinerobotcontroller.tools

/**
 * 计时器
 */
class Clock {
    private val nanoToMill = 1000000
    private val millSecondsPerSecond = 1000
    private var startTime: Long = 0L
    private var pauseAtTime: Long = 0L
    private var pauseAllTime: Long = 0L

    init {
        reset()
    }

    fun reset() {
        startTime = System.nanoTime()
        pauseAllTime = 0L
    }

    fun getMillSeconds() = (System.nanoTime() - startTime - pauseAllTime) / nanoToMill

    fun getSeconds() = getMillSeconds() / millSecondsPerSecond

    fun start() {
        pauseAllTime += (System.nanoTime() - pauseAtTime)
        pauseAtTime = 0L
    }

    fun pause() {
        pauseAtTime = System.nanoTime()
    }
}
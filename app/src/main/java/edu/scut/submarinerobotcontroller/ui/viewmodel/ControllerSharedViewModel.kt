package edu.scut.submarinerobotcontroller.ui.viewmodel

import android.content.res.ColorStateList
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ControllerSharedViewModel : ViewModel() {

    val title = MutableLiveData("标题")
    val ping = MutableLiveData("Ping")
    val pingColor = MutableLiveData(0)
    val time = MutableLiveData("时间")
    val runAndPauseButtonColor = MutableLiveData(ColorStateList.valueOf(0))
    val runAndPauseButtonText = MutableLiveData("")

    //共享
    val motorPower = MutableLiveData(Array(6) { 0.0 })
    val motorPowerColor = MutableLiveData(Array(6) { 0 })

    //仅AutoFragment
    val motorPowerProgress = MutableLiveData(Array(6) { 0 })
    val orientationAngles = MutableLiveData(arrayOf("null", "null", "null"))
    val degreeWithTurn = MutableLiveData("null")
    val signal = MutableLiveData("Signal")
    val signalTextColor = MutableLiveData(0)
    val signalBackgroundColor = MutableLiveData(0)
    val command = MutableLiveData("System Command >")

    //仅ManualFragment
    val depthPowerText = MutableLiveData("0")
    val leftServoText = MutableLiveData("")
    val rightServoText = MutableLiveData("")

}
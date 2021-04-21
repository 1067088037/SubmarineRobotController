package edu.scut.submarinerobotcontroller.ui.viewmodel

import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    val startControllerActivityBtnEnable = MutableLiveData(false)
    val bluetoothWorkingProgressVisibility = MutableLiveData(View.INVISIBLE)
    val bluetoothStateText = MutableLiveData("")
    val trainingProgress = MutableLiveData(0)
    val trainingProgressColor = MutableLiveData(0)

}
package edu.scut.submarinerobotcontroller

import edu.scut.submarinerobotcontroller.ui.view.MotorTopView

object Constant {
    val NeedPermission = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val DefaultBluetoothName = arrayOf("ESP32_G91LrdEJ", "My Device")

    val TargetDepth = 180.0..260.0

    const val RequestPermission = 0x1
    const val ManualRequestPermission = 0x2
    const val StartBlueBoothRequestCode = 0x3

    const val LengthOfMessage = 32
    const val EmptyCode: Byte = 0
    const val TrueCode: Byte = 1
    const val FalseCode: Byte = 0
    const val CommandStartCode: Byte = -128
    const val MinValidCommandCode: Byte = -127

    const val MotorMaxPower: Byte = 100
    const val ServoMaxPosition: Int = 180
    const val MotorTopViewEps = 0.01

    const val CommandMaxLine = 40
    const val MinBlackObjArea = 3000
    const val TargetTrainLoss = 0.005
    const val NeedCoincidence = 0.88
    const val NeedPredictTimes = 5
    const val CylinderId = "2"
    const val CubeId = "4"

    val SystemStartTime = System.currentTimeMillis() //启动时的系统时间

    val TrainData2Array = arrayOf(
        R.mipmap.bm_2_1,
        R.mipmap.bm_2_2,
        R.mipmap.bm_2_3,
        R.mipmap.bm_2_4,
        R.mipmap.bm_2_5,
        R.mipmap.bm_2_6,
        R.mipmap.bm_2_7,
        R.mipmap.bm_2_8,
        R.mipmap.bm_2_9,
        R.mipmap.bm_2_10,
        R.mipmap.bm_2_11,
        R.mipmap.bm_2_12,
        R.mipmap.bm_2_13,
        R.mipmap.bm_2_14,
        R.mipmap.bm_2_15,
        R.mipmap.bm_2_16,
        R.mipmap.bm_2_17,
        R.mipmap.bm_2_18,
        R.mipmap.bm_2_19,
        R.mipmap.bm_2_20,
        R.mipmap.bm_2_21,
        R.mipmap.bm_2_22,
        R.mipmap.bm_2_23,
        R.mipmap.bm_2_24,
        R.mipmap.bm_2_25,
        R.mipmap.bm_2_26,
        R.mipmap.bm_2_27,
        R.mipmap.bm_2_28,
        R.mipmap.bm_2_29,
        R.mipmap.bm_2_30,
        R.mipmap.bm_2_31,
        R.mipmap.bm_2_32,
        R.mipmap.bm_2_33,
        R.mipmap.bm_2_34,
        R.mipmap.bm_2_35,
        R.mipmap.bm_2_36,
        R.mipmap.bm_2_37,
        R.mipmap.bm_2_38,
        R.mipmap.bm_2_39,
        R.mipmap.bm_2_40,
        R.mipmap.bm_2_41,
        R.mipmap.bm_2_42,
        R.mipmap.bm_2_43,
        R.mipmap.bm_2_44,
        R.mipmap.bm_2_45,
        R.mipmap.bm_2_46,
        R.mipmap.bm_2_47,
        R.mipmap.bm_2_48,
        R.mipmap.bm_2_49,
        R.mipmap.bm_2_50
    )

    val TrainData4Array = arrayOf(
        R.mipmap.bm_4_1,
        R.mipmap.bm_4_2,
        R.mipmap.bm_4_3,
        R.mipmap.bm_4_4,
        R.mipmap.bm_4_5,
        R.mipmap.bm_4_6,
        R.mipmap.bm_4_7,
        R.mipmap.bm_4_8,
        R.mipmap.bm_4_9,
        R.mipmap.bm_4_10,
        R.mipmap.bm_4_11,
        R.mipmap.bm_4_12,
        R.mipmap.bm_4_13,
        R.mipmap.bm_4_14,
        R.mipmap.bm_4_15,
        R.mipmap.bm_4_16,
        R.mipmap.bm_4_17,
        R.mipmap.bm_4_18,
        R.mipmap.bm_4_19,
        R.mipmap.bm_4_20,
        R.mipmap.bm_4_21,
        R.mipmap.bm_4_22,
        R.mipmap.bm_4_23,
        R.mipmap.bm_4_24,
        R.mipmap.bm_4_25,
        R.mipmap.bm_4_26,
        R.mipmap.bm_4_27,
        R.mipmap.bm_4_28,
        R.mipmap.bm_4_29,
        R.mipmap.bm_4_30,
        R.mipmap.bm_4_31,
        R.mipmap.bm_4_32,
        R.mipmap.bm_4_33,
        R.mipmap.bm_4_34,
        R.mipmap.bm_4_35,
        R.mipmap.bm_4_36,
        R.mipmap.bm_4_37,
        R.mipmap.bm_4_38,
        R.mipmap.bm_4_39,
        R.mipmap.bm_4_40,
        R.mipmap.bm_4_41,
        R.mipmap.bm_4_42,
        R.mipmap.bm_4_43,
        R.mipmap.bm_4_44,
        R.mipmap.bm_4_45,
        R.mipmap.bm_4_46,
        R.mipmap.bm_4_47,
        R.mipmap.bm_4_48,
        R.mipmap.bm_4_49,
        R.mipmap.bm_4_50
    )
}
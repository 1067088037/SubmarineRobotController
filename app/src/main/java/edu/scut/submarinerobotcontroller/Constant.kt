package edu.scut.submarinerobotcontroller

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

    const val CommandMaxLine = 50
    const val MinBlackObjArea = 10000
    const val TargetTrainLoss = 0.010
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
        R.mipmap.bm_2_11
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
        R.mipmap.bm_4_11
    )
}
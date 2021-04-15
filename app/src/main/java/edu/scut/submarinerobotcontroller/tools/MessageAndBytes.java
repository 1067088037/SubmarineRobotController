package edu.scut.submarinerobotcontroller.tools;

public class MessageAndBytes {
    /**
     * short 转 byte 数组
     *
     * @param aShort 一个short数
     * @return byte 数组
     */
    public static byte[] shortToBytes(short aShort) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((aShort >>> 8) & 0xFF);
        bytes[1] = (byte) (aShort & 0xFF);
        return bytes;
    }

    /**
     * byte 数组 转 short
     *
     * @param bytes byte 数组
     * @return 一个short数
     */
    public static short bytesToShort(byte[] bytes) {
        return (short) ((bytes[0] << 8) | (bytes[1] & 0x00FF));
    }

//    public static byte[] longToBytes(long aLong) {
//        byte[] bytes = new byte[8];
//        for (int i = 0; i < 8; ++i) {
//            bytes[i] = (byte) ((aLong >>> (i * 8)) & 0xFF);
//        }
//        return bytes;
//    }
//
//    public static long bytesToLong(byte[] bytes) {
//        long aLong = 0x0;
//        for (int i = 0; i < 8; ++i) {
//            aLong |= (bytes[i] << (8 * i));
//        }
//        return aLong;
//    }
}

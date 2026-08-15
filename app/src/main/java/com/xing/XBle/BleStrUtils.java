package com.xing.XBle;

/**
 * xing<br>
 * 2019/3/7<br>
 * byte,String 工具类
 */
public class BleStrUtils {


    /**
     * (16进制)
     * BLE蓝牙返回的byte[]
     * byte[]转字符串
     */
    public static String byte2HexStr(byte[] b) {
        if (b == null)
            return "";
        StringBuilder hs = new StringBuilder();
        String stmp;
        for (byte aB : b) {
            int a = aB & 0xFF;
            stmp = Integer.toHexString(a);
            if (stmp.length() == 1)
                hs.append("0").append(stmp);
            else
                hs.append(stmp);
            hs.append(",");
        }
        hs.charAt(hs.length()-1);
        return hs.toString();
    }

    /**
     * 十六进制字符串转 byte[]
     * 支持空格、逗号分隔；奇数长度时左侧补 0
     */
    public static byte[] hexStr2Bytes(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String s = hex.trim().replace(" ", "").replace(",", "");
        if (s.isEmpty()) {
            return new byte[0];
        }
        if ((s.length() & 1) != 0) {
            s = "0" + s;
        }
        int len = s.length() >> 1;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int start = i << 1;
            bytes[i] = (byte) Integer.parseInt(s.substring(start, start + 2), 16);
        }
        return bytes;
    }



    /**
     * b为传入的字节，start是起始位，length是长度，如要获取bit0-bit4的值，则start为0，length为5
     *
     * @param b      byte
     * @param start  取bit开始位置
     * @param length 取bit的长度
     * @return bit相加的值
     */
    public static int getBits(byte b, int start, int length) {
        return ((b >> start) & (0xFF >> (8 - length)));
    }


    /**
     * b为传入的字节,取出bit每个值
     * @param b 字节
     * @return bit数组
     */
    public static int[] getBits(byte b) {
        int[] bits = new int[8];
        for (byte i = 0; i < 8; i++) {
            bits[i] = (b >> i) & 0x01;
        }
        return bits;
    }
}

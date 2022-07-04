package com.xing.xblelibrary.utils;

import java.util.UUID;

import androidx.annotation.Nullable;

/**
 * xing<br>
 * 2022/7/4<br>
 * java类作用描述
 */
public class UuidUtils {


    /**
     * 字符串转UUID,返回NULL表示格式有误
     * @param uuidStr uuid字符串
     * @return UUID
     */
    @Nullable
    public static UUID getUuid(String uuidStr)  {
        //00002902-0000-1000-8000-00805f9b34fb
        //0000290200001000800000805f9b34fb
        if (uuidStr.length()<=8&&uuidStr.contains("-")){
            //格式有误
            return null;
        }
        UUID uuid = null;
        String uuidStrUpperCase=uuidStr.toUpperCase();
        if (uuidStrUpperCase.length() == 2) {
            uuid = UUID.fromString("000000" + uuidStrUpperCase.toUpperCase() + "-0000-1000-8000-00805F9B34FB");
        } else if (uuidStrUpperCase.length() == 4) {
            uuid = UUID.fromString("0000" + uuidStrUpperCase + "-0000-1000-8000-00805F9B34FB");
        } else if (uuidStrUpperCase.length() == 8) {
            uuid = UUID.fromString(uuidStrUpperCase + "-0000-1000-8000-00805F9B34FB");
        } else if (uuidStrUpperCase.contains("-")) {
            UUID.fromString(uuidStrUpperCase);
        } else {
            StringBuffer buffer = new StringBuffer(uuidStrUpperCase);
            buffer.append(uuidStrUpperCase.substring(0, 8)).append("-");
            buffer.append(uuidStrUpperCase.substring(8, 12)).append("-");
            buffer.append(uuidStrUpperCase.substring(12, 16)).append("-");
            buffer.append(uuidStrUpperCase.substring(16, 20)).append("-");
            buffer.append(uuidStrUpperCase.substring(20)).append("-");
            uuid = UUID.fromString(buffer.toString());
        }
        return uuid;
    }

}

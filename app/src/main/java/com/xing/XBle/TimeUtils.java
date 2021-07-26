package com.xing.XBle;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 时间格式化工具
 */
public class TimeUtils {




    /**
     * 格式化时间
     *
     * @return 例:2019-03-01 10:18:50:100
     */
    public static String getDateDefault(Long time) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.US);
        return simpleDateFormat.format(time)+"\n";
    }


    /**
     * 格式化时间
     *
     * @return 例:2019-03-01 10:18:50:100
     */
    public static String getCurrentTimeStr() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.US);
        return simpleDateFormat.format(System.currentTimeMillis())+"\n";
    }
}

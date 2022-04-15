package com.xing.xblelibrary.utils;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * xing<br>
 * 2022/4/15<br>
 * 蓝牙检测
 */
public class BleCheckUtils {


    private static BleCheckUtils instance;

    private BleCheckUtils() {
    }

    public static BleCheckUtils getInstance() {
        if (instance == null) {
            synchronized (BleCheckUtils.class) {
                if (instance == null) {
                    instance = new BleCheckUtils();
                }
            }
        }
        return instance;
    }

    /**
     * 判断手机是否支持低功率蓝牙
     * @param context Context
     * @return 是否支持
     */
    public  boolean getSupportBluetoothLe(Context context){
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

}

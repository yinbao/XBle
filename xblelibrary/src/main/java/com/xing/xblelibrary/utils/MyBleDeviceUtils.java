package com.xing.xblelibrary.utils;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 蓝牙设备工具
 */

public class MyBleDeviceUtils {

    private static final String TAG = MyBleDeviceUtils.class.getName();


    /**
     * 是否支持蓝牙
     *
     * @return
     */
    public static boolean isSupportBle(Context mContext) {
        boolean isBle = mContext.getApplicationContext()
            .getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        return isBle;
    }




    /**
     * 获取我们需要的服务 通过UUID判断
     *
     * @param mBluetoothGatt BluetoothGatt
     *
     * @return BluetoothGattService符合uuid的服务, 否则返回null
     */
    public static BluetoothGattService getService(BluetoothGatt mBluetoothGatt, UUID uuidService) {
        if (mBluetoothGatt != null) {
            BluetoothGattService mGattService = mBluetoothGatt.getService(uuidService);
            return mGattService;
        }
        return null;
    }


    /**
     * 从服务中获取符合要求的特征
     *
     * @param mBleGattService 服务
     * @param uuid            特征的uuid
     *
     * @return BluetoothGattCharacteristic符合uuid的特征, 否则返回null
     */
    public static BluetoothGattCharacteristic getServiceWrite(BluetoothGattService mBleGattService,
                                                              UUID uuid) {
        if (mBleGattService != null) {
            BluetoothGattCharacteristic mCharacteristic = mBleGattService.getCharacteristic(uuid);
            return mCharacteristic;
        }
        return null;
    }



    /**
     * Clears the internal cache and forces a refresh of the services from the remote device.
     */
    public static boolean refreshDeviceCache(BluetoothGatt mBluetoothGatt) {
        if (mBluetoothGatt != null) {
            try {
                Method localMethod = mBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
                if (localMethod != null) {
                    boolean bool = (Boolean) localMethod.invoke(mBluetoothGatt, new Object[0]);
                    return bool;
                }
            } catch (Exception localException) {
                Log.i(TAG, "An exception occured while refreshing device");
            }
        }
        return false;
    }


}

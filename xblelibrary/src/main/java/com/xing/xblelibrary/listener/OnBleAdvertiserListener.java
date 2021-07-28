package com.xing.xblelibrary.listener;

import android.bluetooth.le.AdvertiseSettings;

/**
 * xing<br>
 * 2021/07/22<br>
 * Ble作为外围广播监听
 */
public interface OnBleAdvertiserListener {


    /**
     * 发送广播成功
     */
    default void onStartSuccess(int adId,AdvertiseSettings advertiseSettings) {
    }

    /**
     * 发送广播失败
     *
     * @param errorCode 错误码:-1代表获取蓝牙对象为null
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_DATA_TOO_LARGE}//广播数据超过31 byte
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_TOO_MANY_ADVERTISERS}//没有装载广播对象
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_ALREADY_STARTED}//已经在广播了
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_INTERNAL_ERROR}//低层内部错误
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_FEATURE_UNSUPPORTED}//硬件不支持
     */
    default void onStartFailure(int adId,int errorCode) {
    }


    /**
     * 停止广播成功
     */
    default void onStopSuccess(int adId) {
    }

    /**
     * 停止广播失败
     *
     * @param errorCode 错误码:-1代表获取蓝牙对象为null
     */
    default void onStopFailure(int adId,int errorCode) {
    }


}

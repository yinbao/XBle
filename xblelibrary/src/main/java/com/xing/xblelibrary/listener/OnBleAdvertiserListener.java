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
     *
     */
    default void onStartSuccess(AdvertiseSettings advertiseSettings) {
    }

    /**
     * 发送广播失败
     *
     * @param errorCode 错误码{@link android.bluetooth.le.AdvertiseCallback}
     */
    default void onStartFailure(int errorCode) {
    }


    /**
     * 停止广播成功
     *
     */
    default void onStopSuccess(AdvertiseSettings advertiseSettings) {
    }

    /**
     * 停止广播失败
     *
     * @param errorCode 错误码{@link android.bluetooth.le.AdvertiseCallback}
     */
    default void onStopFailure(int errorCode) {
    }


}

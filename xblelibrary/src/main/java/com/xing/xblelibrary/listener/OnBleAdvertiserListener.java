package com.xing.xblelibrary.listener;

/**
 * xing<br>
 * 2021/07/22<br>
 * Ble作为外围广播监听
 */
public interface OnBleAdvertiserListener {


    /**
     * 广播成功
     *
     */
    default void onStartSuccess() {
    }

    /**
     * 广播失败
     *
     * @param errorCode 错误码{@link android.bluetooth.le.AdvertiseCallback}
     */
    default void onStartFailure(int errorCode) {
    }



}

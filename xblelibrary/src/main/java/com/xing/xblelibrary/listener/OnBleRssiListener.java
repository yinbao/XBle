package com.xing.xblelibrary.listener;

/**
 * xing<br>
 * 2019/5/21<br>
 * ble信号强度
 */
public interface OnBleRssiListener {

    /**
     * 信号强度
     * @param rssi
     */
    void OnRssi(int rssi);

}

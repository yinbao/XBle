package com.xing.xblelibrary.listener;


import android.bluetooth.BluetoothDevice;

import java.util.List;

/**
 * 蓝牙连接断开基类接口
 */

public interface OnBleConnectListener extends OnBleStatusListener{

    /**
     * 正在连接
     */
    default void onConnecting(String mac){}

    /**
     * 连接错误,当前连接已达系统限制最大值7个
     * @param list 当前已连接的设备对象列表
     */
    default void onConnectMaxErr(List<BluetoothDevice> list){}

    /**
     * 连接断开,在UI线程
     */
    default void onDisConnected(String mac, int code) {
    }

    /**
     * 连接成功,还没有获取到服务
     */
    default void onConnectionSuccess(String mac) {
    }

    /**
     * 连接成功(发现服务),在UI线程
     */
    default void onServicesDiscovered(String mac) {
    }



}

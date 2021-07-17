package com.xing.xblelibrary.listener;


/**
 * 蓝牙连接断开基类接口
 */

public interface OnBleConnectCallback {

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

    /**
     * 已开启蓝牙,在触发线程
     */
    default void bleOpen() {
    }

    /**
     * 未开启蓝牙,在触发线程
     */
    default void bleClose() {
    }

}

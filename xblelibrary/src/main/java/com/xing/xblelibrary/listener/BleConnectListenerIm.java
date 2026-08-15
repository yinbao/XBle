package com.xing.xblelibrary.listener;

import android.bluetooth.BluetoothDevice;

import java.util.List;

/**
 * Ble 连接状态的多观察者分发（弱引用，由 BaseListenerIm 持有）。
 */
public class BleConnectListenerIm extends BaseListenerIm<OnBleBaseListener> {

    private static class SingletonHolder {
        private static final BleConnectListenerIm INSTANCE = new BleConnectListenerIm();
    }

    public static BleConnectListenerIm getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 连接断开
     */
    public void onDisConnected(String mac, int code) {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleConnectListener) {
                ((OnBleConnectListener) observer).onDisConnected(mac, code);
            }
        }
    }

    /**
     * 连接成功(还未发现服务)
     */
    public void onConnectionSuccess(String mac) {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleConnectListener) {
                ((OnBleConnectListener) observer).onConnectionSuccess(mac);
            }
        }
    }

    /**
     * 连接成功(发现服务)
     */
    public void onServicesDiscovered(String mac) {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleConnectListener) {
                ((OnBleConnectListener) observer).onServicesDiscovered(mac);
            }
        }
    }

    /**
     * 正在连接
     */
    public void onConnecting(String mac) {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleConnectListener) {
                ((OnBleConnectListener) observer).onConnecting(mac);
            }
        }
    }

    /**
     * 连接错误,已达系统连接数量上限
     */
    public void onConnectMaxErr(List<BluetoothDevice> list) {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleConnectListener) {
                ((OnBleConnectListener) observer).onConnectMaxErr(list);
            }
        }
    }

    /**
     * 未开启蓝牙
     */
    public void bleClose() {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleStatusListener) {
                ((OnBleStatusListener) observer).bleClose();
            }
        }
    }

    /**
     * 已开启蓝牙
     */
    public void bleOpen() {
        for (OnBleBaseListener observer : getAliveListeners()) {
            if (observer instanceof OnBleStatusListener) {
                ((OnBleStatusListener) observer).bleOpen();
            }
        }
    }
}

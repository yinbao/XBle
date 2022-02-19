package com.xing.xblelibrary.listener;

import android.bluetooth.BluetoothDevice;

import java.util.List;

/**
 * 功能描述:  Ble连接状态的实现类
 * 1,连接断开
 * 2,连接成功
 * 3,正在连接
 * 4,蓝牙关闭
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
    public void onDisConnected(OnBleBaseListener callback, String mac, int code) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleConnectListener) {
                        ((OnBleConnectListener) observer).onDisConnected(mac,code);
                    }
            }
        }
    }

    /**
     * 连接成功(还未发现服务)
     */
    public void onConnectionSuccess(OnBleBaseListener callback, String mac) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleConnectListener) {
                        ((OnBleConnectListener) observer).onConnectionSuccess(mac);
                    }
            }
        }
    }

    /**
     * 连接成功(发现服务)
     */
    public void onServicesDiscovered(OnBleBaseListener callback, String mac) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleConnectListener) {
                        ((OnBleConnectListener) observer).onServicesDiscovered(mac);
                    }
            }
        }
    }

    /**
     * 正在连接
     */
    public void onConnecting(OnBleBaseListener callback, String mac) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleConnectListener) {
                        ((OnBleConnectListener) observer).onConnecting(mac);
                    }

            }
        }
    }
    /**
     * 连接错误,已达系统连接数量上限
     */
    public void onConnectMaxErr(OnBleBaseListener callback, List<BluetoothDevice> list) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleConnectListener) {
                        ((OnBleConnectListener) observer).onConnectMaxErr(list);
                    }

            }
        }
    }

    /**
     * 未开启蓝牙
     */
    public void bleClose(OnBleBaseListener callback) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleStatusListener) {
                        ((OnBleStatusListener) observer).bleClose();
                    }
            }
        }
    }

    /**
     * 已开启蓝牙
     */
    public void bleOpen(OnBleBaseListener callback) {
        synchronized (OnBleBaseListener.class) {
            for (OnBleBaseListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleStatusListener) {
                        ((OnBleStatusListener) observer).bleOpen();
                    }
            }
        }
    }

}

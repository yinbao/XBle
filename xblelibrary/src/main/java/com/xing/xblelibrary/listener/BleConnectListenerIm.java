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

public class BleConnectListenerIm extends BaseListenerIm<OnBleConnectListener> {

    private static class SingletonHolder {
        private static final BleConnectListenerIm INSTANCE = new BleConnectListenerIm();
    }

    public static BleConnectListenerIm getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 连接断开
     */
    public void onDisConnected(OnBleConnectListener callback, String mac, int code) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    observer.onDisConnected(mac, code);
            }
        }
    }

    /**
     * 连接成功(还未发现服务)
     */
    public void onConnectionSuccess(OnBleConnectListener callback, String mac) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    observer.onConnectionSuccess(mac);
            }
        }
    }

    /**
     * 连接成功(发现服务)
     */
    public void onServicesDiscovered(OnBleConnectListener callback, String mac) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    observer.onServicesDiscovered(mac);
            }
        }
    }

    /**
     * 正在连接
     */
    public void onConnecting(OnBleConnectListener callback, String mac) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleScanConnectListener) {
                        ((OnBleScanConnectListener) observer).onConnecting(mac);
                    }

            }
        }
    }
    /**
     * 正在连接
     */
    public void onConnectMaxErr(OnBleConnectListener callback, List<BluetoothDevice> list) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleScanConnectListener) {
                        ((OnBleScanConnectListener) observer).onConnectMaxErr(list);
                    }

            }
        }
    }

    /**
     * 未开启蓝牙
     */
    public void bleClose(OnBleConnectListener callback) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    observer.bleClose();
            }
        }
    }

    /**
     * 已开启蓝牙
     */
    public void bleOpen(OnBleConnectListener callback) {
        synchronized (OnBleConnectListener.class) {
            for (OnBleConnectListener observer : listListener) {
                if (observer != null && observer != callback)
                    observer.bleOpen();
            }
        }
    }

}

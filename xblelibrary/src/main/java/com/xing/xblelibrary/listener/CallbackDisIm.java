package com.xing.xblelibrary.listener;

/**
 * 功能描述:  Ble连接状态的实现类
 * 1,连接断开
 * 2,连接成功
 * 3,正在连接
 * 4,蓝牙关闭
 */

public class CallbackDisIm extends BaseListenerIm<OnBleConnectCallback> {

    private static class SingletonHolder {
        private static final CallbackDisIm INSTANCE = new CallbackDisIm();
    }

    public static CallbackDisIm getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 连接断开
     */
    public void onDisConnected(OnBleConnectCallback callback, String mac, int code) {
        synchronized (OnBleConnectCallback.class) {
            for (OnBleConnectCallback observer : listListener) {
                if (observer != null && observer != callback)
                    observer.onDisConnected(mac, code);
            }
        }
    }

    /**
     * 连接成功(还未发现服务)
     */
    public void onConnectionSuccess(OnBleConnectCallback callback, String mac) {
        synchronized (OnBleConnectCallback.class) {
            for (OnBleConnectCallback observer : listListener) {
                if (observer != null && observer != callback)
                    observer.onConnectionSuccess(mac);
            }
        }
    }

    /**
     * 连接成功(发现服务)
     */
    public void onServicesDiscovered(OnBleConnectCallback callback, String mac) {
        synchronized (OnBleConnectCallback.class) {
            for (OnBleConnectCallback observer : listListener) {
                if (observer != null && observer != callback)
                    observer.onServicesDiscovered(mac);
            }
        }
    }

    /**
     * 正在连接
     */
    public void onConnecting(OnBleConnectCallback callback, String mac) {
        synchronized (OnBleConnectCallback.class) {
            for (OnBleConnectCallback observer : listListener) {
                if (observer != null && observer != callback)
                    if (observer instanceof OnBleScanConnectCallback) {
                        ((OnBleScanConnectCallback) observer).onConnecting(mac);
                    }

            }
        }
    }

    /**
     * 未开启蓝牙
     */
    public void bleClose(OnBleConnectCallback callback) {
        synchronized (OnBleConnectCallback.class) {
            for (OnBleConnectCallback observer : listListener) {
                if (observer != null && observer != callback)
                    observer.bleClose();
            }
        }
    }

    /**
     * 已开启蓝牙
     */
    public void bleOpen(OnBleConnectCallback callback) {
        synchronized (OnBleConnectCallback.class) {
            for (OnBleConnectCallback observer : listListener) {
                if (observer != null && observer != callback)
                    observer.bleOpen();
            }
        }
    }

}

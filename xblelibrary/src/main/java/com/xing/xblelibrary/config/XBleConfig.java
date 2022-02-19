package com.xing.xblelibrary.config;


import com.xing.xblelibrary.XBleManager;
import com.xing.xblelibrary.utils.XBleL;

/**
 * xing<br>
 * 2021/07/21<br>
 * 蓝牙参数配置,uuid等信息
 */
public class XBleConfig {

    /**
     * 最大连接数
     */
    private int mConnectMax = 7;
    /**
     * 是否自动连接系统已连接的设备
     */
    private boolean mAutoConnectSystemBle = false;
    /**
     * 是否自动监听连接系统连接的设备,并在通用接口回调连接过程和结果
     */
    private boolean mAutoMonitorSystemConnectBle = false;

    private static XBleConfig instance;

    private XBleConfig() {
    }

    public static XBleConfig getInstance() {
        if (instance == null) {
            synchronized (XBleConfig.class) {
                if (instance == null) {
                    instance = new XBleConfig();
                }
            }
        }
        return instance;
    }


    public int getConnectMax() {
        return mConnectMax;
    }

    /**
     * 设置最大连接数据,系统最大支持,包含其他APP连接的BLE数量
     *
     * @param connectMax 最大连接数(1~7),默认值为:7
     */
    public XBleConfig setConnectMax(int connectMax) {
        if (connectMax > 7){
            XBleL.e("设置的连接数已超过系统限制,自动修改为最大值:7");
            connectMax = 7;
        }
        if (connectMax <= 0)
            connectMax = 1;
        this.mConnectMax = connectMax;
        XBleManager.getInstance().setConnectMax(connectMax);
        return this;
    }

    public boolean isAutoConnectSystemBle() {
        return mAutoConnectSystemBle;
    }

    /**
     * 是否自动连接系统已连接的设备
     *
     * @param autoConnectSystemBle 是否自动连接系统已连接的设备
     */
    public XBleConfig setAutoConnectSystemBle(boolean autoConnectSystemBle) {
        mAutoConnectSystemBle = autoConnectSystemBle;
        return this;
    }


    public boolean isAutoMonitorSystemConnectBle() {
        return mAutoMonitorSystemConnectBle;
    }

    /**
     * 是否自动监听连接系统连接的设备,并在通用接口回调连接过程和结果
     *
     * @param autoMonitorSystemConnectBle 是否自动监听连接系统连接的设备
     */
    public XBleConfig setAutoMonitorSystemConnectBle(boolean autoMonitorSystemConnectBle) {
        mAutoMonitorSystemConnectBle = autoMonitorSystemConnectBle;
        return this;
    }
}

package com.xing.xblelibrary.config;


import java.util.UUID;

/**
 * xing<br>
 * 2019/3/5<br>
 * 蓝牙参数配置,uuid等信息
 */
public class BleConfig {

    //-------------操作------------
    /**
     * 读(read)
     */
    public static final int READ_DATA = 1;
    /**
     * 写(write)
     */
    public static final int WRITE_DATA = 2;
    /**
     * 读取rssi(Read rssi)
     */
    public static final int RSSI_DATA = 3;
    /**
     * 通知(notify)
     */
    public static final int NOTICE_DATA = 4;

    //-----------------

    /**
     * 设置Notify的特征中的描述UUID
     */
    public static UUID UUID_NOTIFY_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * 加密的时间版本判断
     */
    public final static int ENCRYPTION_TIME = 20190813;


    /**
     * 1=已经在搜索了(如果没有回调,可能是权限问题)
     * Fails to start scan as BLE scan with the same settings is already started by the app.
     */
    public static final int SCAN_FAILED_ALREADY_STARTED = 1;

    /**
     * 注册搜索失败
     * Fails to start scan as app cannot be registered.
     */
    public static final int SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2;

    /**
     * 蓝牙低层内部错误
     * Fails to start scan due an internal error
     */
    public static final int SCAN_FAILED_INTERNAL_ERROR = 3;

    /**
     * 不支持此搜索方式
     * Fails to start power optimized scan as this feature is not supported.
     */
    public static final int SCAN_FAILED_FEATURE_UNSUPPORTED = 4;

    /**
     * 硬件不支持
     * Fails to start scan as it is out of hardware resources.
     */
    public static final int SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5;

    /**
     * 搜索过于频繁
     * Fails to start scan as application tries to scan too frequently.
     */
    public static final int SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6;


}

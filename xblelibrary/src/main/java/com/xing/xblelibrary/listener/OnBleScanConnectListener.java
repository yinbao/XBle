package com.xing.xblelibrary.listener;


import com.xing.xblelibrary.bean.BleValueBean;

/**
 * 蓝牙搜索,连接等操作接口
 */
public interface OnBleScanConnectListener extends OnBleConnectListener {

    /**
     * 开始扫描设备
     */
    default void onStartScan(){}

    /**
     *
     * 每扫描到一个设备就会回调一次
     *  如果需要长时间搜索,请每个20~30分钟重启一次搜索
     */
    default void onScanning(BleValueBean data){}

    /**
     * 扫描超时(完成)
     */
   default void onScanTimeOut(){}


    /**
     * 扫描异常
     * @param time 多少ms后才可以再次进行扫描
     */
    default void onScanErr(long time){}

    /**
     * 正在连接
     */
   default void onConnecting(String mac){}


}

package com.xing.xblelibrary.listener;


/**
 * 蓝牙状态接口
 */
public interface OnBleStatusListener  extends OnBleBaseListener{

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

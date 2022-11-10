package com.xing.xblelibrary.listener;


import com.xing.xblelibrary.bean.BleBroadcastBean;

import androidx.annotation.CallSuper;

/**
 * xing<br>
 * 2021/07/21<br>
 * 搜索和过滤接口
 */
public interface OnBleScanFilterListener {

    /**
     * 开始扫描设备
     */
    default void onStartScan(){}

    /**
     * 过滤计算->可以对广播数据进行帅选过滤
     *
     * @param bleBroadcastBean 蓝牙广播数据
     * @return 是否有效
     */
    default boolean onBleFilter(BleBroadcastBean bleBroadcastBean) {
        return true;
    }

    /**
     * 蓝牙广播数据-> 符合要求的广播数据对象返回
     *
     * @param bleBroadcastBean 搜索到的设备信息
     */
    default void onScanBleInfo(BleBroadcastBean bleBroadcastBean) {
    }

    /**
     * 扫描完成
     * 注:只有在扫描的时候传入了超时时间才会回调
     */
    default void onScanComplete(){}




    /**
     * 扫描异常
     *
     * @param time 多少ms后才可以再次进行扫描
     */
    @Deprecated
    default void onScanErr(long time) {

    }

    /**
     * 在扫描犯错
     * 扫描异常
     *
     * @param time 多少ms后才可以再次进行扫描
     * @param type 类型 1=连接太频繁,2=连续3次扫描失败,建议重启蓝牙
     */
    @CallSuper
    default void onScanErr(int type, long time) {
        if (type==1){
            onScanErr(time);
        }
    }
}

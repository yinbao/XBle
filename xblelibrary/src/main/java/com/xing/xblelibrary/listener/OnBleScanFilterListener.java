package com.xing.xblelibrary.listener;


import com.xing.xblelibrary.bean.BleValueBean;

/**
 * xing<br>
 * 2019/3/6<br>
 * 搜索过滤接口
 */
public interface OnBleScanFilterListener {

    /**
     * 过滤计算->可以对广播数据进行帅选过滤
     *
     * @param bleValueBean 蓝牙广播数据
     * @return 是否有效
     */
    default boolean onBleFilter(BleValueBean bleValueBean) {
        return true;
    }

    /**
     * 蓝牙广播数据-> 符合要求的广播数据对象返回
     *
     * @param bleValueBean 搜索到的设备信息
     */
    default void onScanBleInfo(BleValueBean bleValueBean) {
    }

}

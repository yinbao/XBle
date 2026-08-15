package com.xing.xblelibrary.listener;

import com.xing.xblelibrary.device.SendDataBean;

import java.util.UUID;

/**
 * xing<br>
 * 2021/07/21<br>
 * ble发送数据结果回调,回调不代表接收成功,只代表系统低层发送成功
 * 1,读结果回调
 * 2,写结果回调
 * 3,通知结果回调
 */
public interface OnBleSendResultListener {


    /**
     * 读取数据的回调
     *
     * @param result 结果
     */
    default void onReadResult(UUID uuid, boolean result) {
    }

    ;

    /**
     * 写入数据的回调
     *
     * @param result 结果
     */
    default void onWriteResult(UUID uuid, boolean result) {
    }

    ;

    /**
     * 写入并且多次重发失败的回调
     *
     * @param data        数据
     * @param reSendCount 重发次数
     */
    default void onWriteAndReSendFail(SendDataBean data, int reSendCount) {
    }

    ;

    /**
     * 设置通知的回调
     *
     * @param result 结果
     */
    default void onNotifyResult(UUID uuid, boolean result) {
    }

    ;


}

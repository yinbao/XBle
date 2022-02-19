package com.xing.xblelibrary.listener;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * xing<br>
 * 2019/11/18<br>
 * 透传数据接口
 */
public interface OnBleCharacteristicRequestListener {


    /**
     * 读数据请求接口
     *
     * @param device
     * @param requestId
     * @param offset
     * @param characteristic
     */
    default void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                             int offset, BluetoothGattCharacteristic characteristic) {
    }

    /**
     * 写数据请求接口
     *
     * @param device
     * @param requestId
     * @param characteristic
     * @param preparedWrite
     * @param responseNeeded 是否需要回复
     * @param offset
     * @param value
     */
    default void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                              BluetoothGattCharacteristic characteristic,
                                              boolean preparedWrite, boolean responseNeeded,
                                              int offset, byte[] value) {
    }


    /**
     * 写描述,一般用于开启通知回调
     *
     */
    default void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                         BluetoothGattDescriptor descriptor,
                                         boolean preparedWrite, boolean responseNeeded,
                                         int offset, byte[] value) {
    }

    /**
     * 发送消息结果回调
     */
    default  void onNotificationSent(BluetoothDevice device, int status) {
    }

    /**
     * Mtu请求
     *
     * @param device
     * @param mtu
     */
    default void onMtuChangedRequest(BluetoothDevice device, int mtu) {
    }


}

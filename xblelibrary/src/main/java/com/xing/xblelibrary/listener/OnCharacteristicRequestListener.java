package com.xing.xblelibrary.listener;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * xing<br>
 * 2019/11/18<br>
 * 透传数据接口
 */
public interface OnCharacteristicRequestListener {


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
     * notify数据请求
     *
     * @param characteristic
     */
    default void onCharacteristicChangedRequest(BluetoothGattCharacteristic characteristic) {
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

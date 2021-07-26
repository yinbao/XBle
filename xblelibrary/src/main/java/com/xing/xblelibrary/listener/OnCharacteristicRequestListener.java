package com.xing.xblelibrary.listener;

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
     * @param characteristic
     */
    default void onCharacteristicReadRequest(BluetoothGattCharacteristic characteristic) {
    }

    /**
     * 写数据请求接口
     *
     * @param characteristic
     */
    default void onCharacteristicWriteRequest(BluetoothGattCharacteristic characteristic) {
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
     * @param characteristic
     */
    default void onMtuChangedRequest(BluetoothGattCharacteristic characteristic) {
    }


}

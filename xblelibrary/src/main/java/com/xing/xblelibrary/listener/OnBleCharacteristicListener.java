package com.xing.xblelibrary.listener;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * xing<br>
 * 2021/07/21<br>
 * 透传数据接口
 */
public interface OnBleCharacteristicListener {


    /**
     * 读数据接口
     *
     * @param characteristic
     */
    default void onCharacteristicReadOK(BluetoothGattCharacteristic characteristic) {
    }

    /**
     * 写数据接口
     *
     * @param characteristic
     */
    default void onCharacteristicWriteOK(BluetoothGattCharacteristic characteristic) {
    }

    /**
     * 设置Notify成功的回调
     */
    default void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
    }

    /**
     * notify返回的数据
     *
     * @param characteristic
     */
    default void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
    }


}

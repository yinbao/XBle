package com.xing.xblelibrary.listener;

import android.bluetooth.BluetoothGattCharacteristic;

/**
 * xing<br>
 * 2021/07/21<br>
 * BLE Notify(Indication)透传数据接口
 */
public interface OnBleNotifyDataListener {


    /**
     * Notify(Indication)返回的数据
     *
     * @param characteristic BluetoothGattCharacteristic
     * @param data           byte[]
     */
    default void onNotifyData(BluetoothGattCharacteristic characteristic, byte[] data) {
    }


}

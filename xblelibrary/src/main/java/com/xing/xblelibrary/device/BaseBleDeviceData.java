package com.xing.xblelibrary.device;


import android.bluetooth.BluetoothGattCharacteristic;

import com.xing.xblelibrary.listener.OnBleNotifyDataListener;
import com.xing.xblelibrary.listener.onBleDisConnectedListener;

import java.util.UUID;

import androidx.annotation.CallSuper;


/**
 * xing<br>
 * 2021/07/21<br>
 * 设备对象抽象类
 */
public abstract class BaseBleDeviceData implements onBleDisConnectedListener, OnBleNotifyDataListener {
    private static String TAG = BaseBleDeviceData.class.getName();

    private BleDevice mBleDevice;
    private AdBleDevice mAdBleDevice;


    public BaseBleDeviceData(BleDevice bleDevice) {
        mBleDevice = bleDevice;
        mBleDevice.setOnDisConnectedListener(this);
        mBleDevice.setOnNotifyDataListener(this);
    }

    public BaseBleDeviceData(AdBleDevice bleDevice) {
        mAdBleDevice = bleDevice;
        mAdBleDevice.setOnDisConnectedListener(this);
        mAdBleDevice.setOnNotifyDataListener(this);
    }

    @Override
    public abstract void onNotifyData(BluetoothGattCharacteristic characteristic, byte[] data);

    /**
     * 连接断开的回调
     */
    @CallSuper
    @Override
    public void onDisConnected() {

    }

    /**
     * 发送信息
     *
     * @param hex  发送的内容
     * @param uuid 需要操作的特征uuid
     * @param type 操作类型(1=读,2=写,3=信号强度)
     */
    @CallSuper
    public void sendData(byte[] hex, UUID uuid, int type, UUID uuidService) {
        sendData(new SendDataBean(hex, uuid, type, uuidService));
    }


    @CallSuper
    public void sendData(SendDataBean sendDataBean) {
        if (mBleDevice != null) {
            mBleDevice.sendData(sendDataBean);
        }
        if (mAdBleDevice != null) {
            mAdBleDevice.sendData(sendDataBean);
        }
    }

    @CallSuper
    public void sendDataNow(SendDataBean sendDataBean) {
        if (mBleDevice != null) {
            mBleDevice.sendDataNow(sendDataBean);
        }
        if (mAdBleDevice != null) {
            mAdBleDevice.sendDataNow(sendDataBean);
        }
    }


}

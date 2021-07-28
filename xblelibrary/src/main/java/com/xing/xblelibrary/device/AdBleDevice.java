package com.xing.xblelibrary.device;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;

import com.xing.xblelibrary.config.BleConfig;
import com.xing.xblelibrary.listener.OnCharacteristicRequestListener;
import com.xing.xblelibrary.listener.onDisConnectedListener;
import com.xing.xblelibrary.utils.BleLog;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;

import java.util.LinkedList;
import java.util.UUID;

import androidx.annotation.CallSuper;

/**
 * xing<br>
 * 2021/07/22<br>
 * BLE设备对象
 * 手机作为外围设备(被其他手机或者设备连接生成的对象)
 */
public final class AdBleDevice implements OnCharacteristicRequestListener {
    protected static String TAG = AdBleDevice.class.getName();

    private final int SEND_DATA_KEY = 1;
    private int mSendDataInterval = 10;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothDevice mBluetoothDevice;
    /**
     * 是否连接成功
     */
    private boolean connectSuccess;
    /**
     * 设备mac地址
     */
    private String mac = null;
    /**
     * 设备名称
     */
    private String mName = null;

    /**
     * 信号强度
     */
    private int mRssi = 0;
    /**
     * 发送数据的队列
     */
    private LinkedList<SendDataBean> mLinkedList;

    private onDisConnectedListener mOnDisConnectedListener;



    public AdBleDevice(BluetoothDevice device, BluetoothGattServer bluetoothGattServer) {
        mBluetoothDevice=device;
        mBluetoothGattServer = bluetoothGattServer;
        this.mac = device.getAddress();
        this.mName = device.getName();
        connectSuccess = true;
        mLinkedList = new LinkedList<>();
        BleLog.i("连接成功:" + mac);
        init();
    }


    private void init() {
        //TODO 可进行所有模块都要进行的初始化操作
    }




    public boolean isConnectSuccess() {
        return connectSuccess;
    }


    /**
     * 断开连接
     *
     * @param notice 断开后是否需要系统回调通知
     */
    public final void disconnect(boolean notice) {
        if (mBluetoothGattServer != null) {
            synchronized (BluetoothGatt.class) {
                if (mBluetoothGattServer != null) {
                    if (!notice) {
//                        mBluetoothGatt.disconnect();
                        //close后系统将不会再回调onConnectionStateChange通知
                        mBluetoothGattServer.close();
                        mBluetoothGattServer = null;
                        return;
                    }
                    mBluetoothGattServer.close();
                    onDisConnected();
                }
            }
        }
        BleLog.e(TAG, "断开连接:" + mac);
    }

    /**
     * 断开连接
     */
    public final void disconnect() {
        disconnect(true);
    }

    /**
     * "@CallSuper"标记子类必须实现父类的注解
     * 1,断开连接,清空发送队列
     */
    @CallSuper
    public void onDisConnected() {
        BleLog.i("断开连接,清空发送队列");
        if (mOnDisConnectedListener != null) {
            mOnDisConnectedListener.onDisConnected();
        }
    }


    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
    }

    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset,
                                             byte[] value) {
        //通知
        characteristic.setValue(value);
        mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
    }

    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

    }

    @Override
    public void onMtuChangedRequest(BluetoothDevice device, int mtu) {

    }




    /**
     * 发送数据
     *
     * @param sendDataBean SendDataBean
     */
    public synchronized void sendData(SendDataBean sendDataBean) {
        if (sendDataBean == null)
            return;
        //消息是否需要置顶发送,默认false
        if (sendDataBean.isTop()) {
            mLinkedList.addLast(sendDataBean);
        } else {
            mLinkedList.addFirst(sendDataBean);
        }

    }


    /**
     * 马上发送数据,需要握手成功
     *
     * @param sendDataBean SendDataBean
     */
    public synchronized void sendDataNow(SendDataBean sendDataBean) {
        if (sendDataBean == null)
            return;
        sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
    }


    /**
     * 发送信息
     *
     * @param hex         发送的内容
     * @param uuid        需要操作的特征uuid
     * @param type        操作类型(1=读,2=写)
     * @param uuidService 服务的uuid
     */
    private synchronized void sendCmd(byte[] hex, UUID uuid, int type, UUID uuidService) {
        try {
            BluetoothGattService mGattService = mBluetoothGattServer.getService(uuidService);
            if (mGattService != null && uuid != null) {
                BluetoothGattCharacteristic mCharacteristic = MyBleDeviceUtils.getServiceWrite(mGattService, uuid);
                if (mCharacteristic != null) {
                    if (hex != null) {
                        mCharacteristic.setValue(hex);
                    }
                    mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    boolean sendOk = false;
                    switch (type) {
                        case BleConfig.READ_DATA:
//                            sendOk=mBluetoothGattServer.sendResponse(mBluetoothDevice, requestId, BluetoothGatt.GATT_SUCCESS, offset,mCharacteristic.getValue());
                            break;

                        case BleConfig.WRITE_DATA:
                            //通知
                            sendOk = mBluetoothGattServer.notifyCharacteristicChanged(mBluetoothDevice, mCharacteristic, false);
                            break;

                    }
                    BleLog.i(TAG, "type:" + type + " UUID=" + uuid + " || " + sendOk);
                }
            }
        } catch (Exception e) {
            BleLog.e(TAG, "读/写/异常:" + e.toString());
            e.printStackTrace();
        }
    }


    public String getMac() {
        return mac;
    }

    public String getName() {
        return mName;
    }




    public BluetoothGattServer getBluetoothGattServer() {
        return mBluetoothGattServer;
    }

    /**
     * 修改发送队列的间隔
     * 默认是10ms
     *
     * @param interval 单位(ms)
     */
    public void setSendDataInterval(int interval) {
        mSendDataInterval = interval;
    }

    //---------------


    public void setOnDisConnectedListener(onDisConnectedListener onDisConnectedListener) {
        mOnDisConnectedListener = onDisConnectedListener;
    }




}

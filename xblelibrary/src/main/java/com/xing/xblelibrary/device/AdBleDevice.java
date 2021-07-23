package com.xing.xblelibrary.device;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.CallSuper;

import com.xing.xblelibrary.config.BleConfig;
import com.xing.xblelibrary.listener.OnBleMtuListener;
import com.xing.xblelibrary.listener.OnBleRssiListener;
import com.xing.xblelibrary.listener.OnBleSendResultListener;
import com.xing.xblelibrary.listener.OnCharacteristicListener;
import com.xing.xblelibrary.utils.BleLog;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * xing<br>
 * 2021/07/22<br>
 * BLE设备对象
 * 手机作为外围设备(被其他手机或者设备连接生成的对象)
 */
public final class AdBleDevice {
    protected static String TAG = AdBleDevice.class.getName();

    private final int SEND_DATA_KEY = 1;
    private int mSendDataInterval = 200;
    private BluetoothGattServer mBluetoothGattServer;
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
    private LinkedList<SendDataBean> mLinkedListNotify;
    /**
     * 握手前的发送数据的队列
     */
    private LinkedList<SendDataBean> mLinkedListHandshake;
    private OnBleSendResultListener mOnBleSendResultListener;
    private onDisConnectedListener mOnDisConnectedListener;

    private OnBleRssiListener mOnBleRssiListener;
    private OnBleMtuListener mOnBleMtuListener;

    private OnCharacteristicListener mOnCharacteristicListener;


    public AdBleDevice(BluetoothDevice device,BluetoothGattServer bluetoothGattServer) {
        BleLog.i("连接成功:" + mac);
        mBluetoothGattServer = bluetoothGattServer;
        this.mac = device.getAddress();
        this.mName = device.getName();
        connectSuccess = true;
        mLinkedList = new LinkedList<>();
        mLinkedListNotify = new LinkedList<>();
        mLinkedListHandshake = new LinkedList<>();
        init();
    }


    /**
     * 判断当前对象是否包含某个服务UUID
     *
     * @param serviceUuid 服务UUID
     * @return 是否包含
     */
    public boolean containsServiceUuid(UUID serviceUuid) {
        if (mBluetoothGattServer != null) {
            List<BluetoothGattService> services = mBluetoothGattServer.getServices();
            for (BluetoothGattService service : services) {
                if (service.getUuid().toString().equalsIgnoreCase(serviceUuid.toString())) {
                    return true;
                }
            }
        }
        return false;
    }


    private void init() {
        //TODO 可进行所有模块都要进行的初始化操作
        readRssi();

    }


    public void readRssi() {
        sendDataNow(new SendDataBean(null, null, BleConfig.RSSI_DATA, null));
    }


    public boolean isConnectSuccess() {
        return connectSuccess;
    }


    /**
     * 开启多个Notify,如果多个服务,可重复调用
     *
     * @param uuidService uuidService
     * @param uuidNotify  uuidNotify
     */
    public void setNotify(UUID uuidService, UUID... uuidNotify) {
        for (UUID uuid : uuidNotify) {
            sendOpenNotify(uuidService, uuid);
        }
    }

    /**
     * 设置通知,有发送队列,不会马上生效,会等待系统回调设置成功后再会设置下一个,一般间隔在100ms左右,与固件性能有关
     */
    private void sendOpenNotify(UUID uuidService, UUID uuidNotify) {
        mLinkedListNotify.addFirst(new SendDataBean(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, uuidNotify, BleConfig.NOTICE_DATA, uuidService));
        if (mLinkedListNotify.size() <= 1) {
            SendDataBean sendDataBean = mLinkedListNotify.getLast();
            sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
            mHandler.removeMessages(SEND_DATA_KEY);
        }
    }

    public void setCloseNotify(UUID uuidService, UUID uuidNotify) {
        mLinkedListNotify.addFirst(new SendDataBean(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, uuidNotify, BleConfig.NOTICE_DATA, uuidService));
        if (mLinkedListNotify.size() <= 1) {
            SendDataBean sendDataBean = mLinkedListNotify.getLast();
            sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
        }
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
                    mHandler.removeCallbacksAndMessages(null);
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
        if (mHandler != null) {
            //清空发送队列
            mHandler.removeCallbacksAndMessages(null);
        }
    }


    /**
     * 通知返回数据
     */
    public final void notifyData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicChanged(characteristic);
        }
    }


    public final void setRssi(int rssi) {
        this.mRssi = rssi;
        if (mOnBleRssiListener != null) {
            mOnBleRssiListener.OnRssi(rssi);
        }
    }

    /**
     * 返回的Mtu
     *
     * @param mtu
     */
    public void getMtu(int mtu) {
        if (mOnBleMtuListener != null) {
            mOnBleMtuListener.OnMtu(mtu);
        }

    }




    @CallSuper
    public void readData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicReadOK(characteristic);
        }

    }


    @CallSuper
    public void writeData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicWriteOK(characteristic);
        }
    }

    @CallSuper
    public void descriptorWriteOk(BluetoothGattDescriptor descriptor) {
        if (descriptor != null) {
            UUID uuid = descriptor.getCharacteristic().getUuid();
            if (mOnCharacteristicListener != null) {
                mOnCharacteristicListener.onDescriptorWriteOK(descriptor);
            }
        }
        if (mLinkedListNotify != null && mLinkedListNotify.size() > 0) {
            mLinkedListNotify.removeLast();
            if (mLinkedListNotify.size() > 0) {
                SendDataBean sendDataBean = mLinkedListNotify.getLast();
                sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
                mHandler.removeMessages(SEND_DATA_KEY);
            } else {
                mHandler.removeMessages(SEND_DATA_KEY);
                mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);
            }
        }

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
        if (mLinkedList.size() <= 1 && mLinkedListNotify.size() <= 0) {
            mHandler.removeMessages(SEND_DATA_KEY);
            mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval / 2);
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
     * 发送所有数据
     */
    private void sendDataAll() {
        BleLog.i(TAG, "sendDataAll:" + mLinkedListHandshake.size());
        while (mLinkedListHandshake.size() > 0) {
            SendDataBean sendDataBean = mLinkedListHandshake.pollLast();
            if (sendDataBean != null) {
                sendData(sendDataBean);
            }
        }

    }


    private Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SEND_DATA_KEY) {
                if (mLinkedList.size() > 0) {
                    SendDataBean sendDataBean = mLinkedList.pollLast();
                    if (sendDataBean != null) {
                        sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
                        mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);//设置间隔,避免发送失败
                    } else {
                        mHandler.sendEmptyMessage(SEND_DATA_KEY);//没有需要发送的数据,不需要间隔
                    }
                }
            }
        }
    };

    /**
     * 发送信息
     *
     * @param hex         发送的内容
     * @param uuid        需要操作的特征uuid
     * @param type        操作类型(1=读,2=写,3=信号强度)
     * @param uuidService 服务的uuid
     */
    private synchronized void sendCmd(byte[] hex, UUID uuid, int type, UUID uuidService) {
        try {
            BluetoothGatt gatt =null;
            if (gatt != null) {
                BluetoothGattService mGattService = MyBleDeviceUtils.getService(gatt, uuidService);
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
                                sendOk = gatt.readCharacteristic(mCharacteristic);
                                if (mOnBleSendResultListener != null) {
                                    mOnBleSendResultListener.onReadResult(uuid, sendOk);
                                }
                                break;

                            case BleConfig.WRITE_DATA:
                                sendOk = gatt.writeCharacteristic(mCharacteristic);
                                if (mOnBleSendResultListener != null) {
                                    mOnBleSendResultListener.onWriteResult(uuid, sendOk);
                                }
                                break;

                            case BleConfig.RSSI_DATA:
                                sendOk = gatt.readRemoteRssi();
                                break;

                            case BleConfig.NOTICE_DATA:
                                mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);//部分手机Notify需要设置这个后才会生效
                                gatt.setCharacteristicNotification(mCharacteristic, true);
                                BluetoothGattDescriptor bluetoothGattDescriptor = mCharacteristic.getDescriptor(BleConfig.UUID_NOTIFY_DESCRIPTOR);
                                if (bluetoothGattDescriptor != null) {
                                    bluetoothGattDescriptor.setValue(hex);
                                    sendOk = gatt.writeDescriptor(bluetoothGattDescriptor);
                                    if (mOnBleSendResultListener != null) {
                                        mOnBleSendResultListener.onNotifyResult(uuid, sendOk);
                                    }
                                    if (!sendOk) {
                                        BleLog.e(TAG, "NOTICE_DATA:UUID=" + uuid + " || false");
                                        return;
                                    }
                                }

                                break;
                        }
                        BleLog.i(TAG, "type:" + type + " UUID=" + uuid + " || " + sendOk);
                    } else if (type == BleConfig.NOTICE_DATA) {
                        //不支持的uuid,回调设置下一个
                        descriptorWriteOk(null);
                    }
                } else if (type == BleConfig.RSSI_DATA) {
                    gatt.readRemoteRssi();
                } else if (type == BleConfig.NOTICE_DATA) {
                    //不支持的uuid,回调设置下一个
                    descriptorWriteOk(null);
                }
            } else if (type == BleConfig.NOTICE_DATA) {
                //不支持的uuid,回调设置下一个
                descriptorWriteOk(null);
            }
        } catch (Exception e) {
            BleLog.e(TAG, "读/写/设置通知,异常:" + e.toString());
            e.printStackTrace();
        }
    }


    public void setOnCharacteristicListener(OnCharacteristicListener onCharacteristicListener) {
        mOnCharacteristicListener = onCharacteristicListener;
    }

    public void setOnBleRssiListener(OnBleRssiListener onBleRssiListener) {
        mOnBleRssiListener = onBleRssiListener;
    }


    public void setOnBleMtuListener(OnBleMtuListener onBleMtuListener) {
        mOnBleMtuListener = onBleMtuListener;
    }

    public void setOnBleSendResultListener(OnBleSendResultListener onBleSendResultListener) {
        mOnBleSendResultListener = onBleSendResultListener;
    }

    public String getMac() {
        return mac;
    }

    public String getName() {
        return mName;
    }

    public int getRssi() {
        return mRssi;
    }


    public BluetoothGattServer getBluetoothGatt() {
        return mBluetoothGattServer;
    }

    /**
     * 修改发送队列的间隔
     * 默认是200ms
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

    /**
     * 断开接口通知
     */
    public interface onDisConnectedListener {

        void onDisConnected();

    }


}

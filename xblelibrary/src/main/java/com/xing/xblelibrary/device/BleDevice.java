package com.xing.xblelibrary.device;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.xing.xblelibrary.config.XBleStaticConfig;
import com.xing.xblelibrary.listener.OnBleCharacteristicListener;
import com.xing.xblelibrary.listener.OnBleMtuListener;
import com.xing.xblelibrary.listener.OnBleNotifyDataListener;
import com.xing.xblelibrary.listener.OnBleRssiListener;
import com.xing.xblelibrary.listener.OnBleSendResultListener;
import com.xing.xblelibrary.listener.onBleDisConnectedListener;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;
import com.xing.xblelibrary.utils.XBleL;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;

/**
 * xing<br>
 * 2021/07/21<br>
 * BLE设备对象
 * 手机作为中央设备(连接其他设备生成的对象)
 */
public final class BleDevice {
    protected static String TAG = BleDevice.class.getName();

    private final int SEND_DATA_KEY = 1;
    private int mSendDataInterval = 200;
    private BluetoothGatt mBluetoothGatt;
    /**
     * 是否连接成功
     */
    private boolean connectSuccess;
    /**
     * 设备mac地址
     */
    private String mac;
    /**
     * 设备名称
     */
    private String mName;

    /**
     * 信号强度
     */
    private int mRssi = 0;
    /**
     * 发送数据的队列
     */
    private final LinkedList<SendDataBean> mLinkedList = new LinkedList<>();

    private OnBleSendResultListener mOnBleSendResultListener;
    private onBleDisConnectedListener mOnDisConnectedListener;
    private OnBleNotifyDataListener mOnNotifyDataListener;
    private OnBleRssiListener mOnBleRssiListener;
    private OnBleMtuListener mOnBleMtuListener;
    private OnBleCharacteristicListener mOnCharacteristicListener;

    /**
     * 是否需要重发
     */
    private boolean mResend = false;
    /**
     * 重发次数
     */
    private int mResendNumber = 3;


    public BleDevice(BluetoothGatt bluetoothGatt, String mac) {
        XBleL.i("连接成功:" + mac);
        mBluetoothGatt = bluetoothGatt;
        this.mac = mac;
        this.mName = bluetoothGatt.getDevice().getName();
        connectSuccess = true;
        init();
    }


    /**
     * 判断当前对象是否包含某个服务UUID
     *
     * @param serviceUuid 服务UUID
     * @return 是否包含
     */
    public boolean containsServiceUuid(UUID serviceUuid) {
        if (mBluetoothGatt != null) {
            List<BluetoothGattService> services = mBluetoothGatt.getServices();
            for (BluetoothGattService service : services) {
                if (service.getUuid().toString().equalsIgnoreCase(serviceUuid.toString())) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 获取服务列表
     *
     * @return List<BluetoothGattService>
     */
    public List<BluetoothGattService> getBluetoothGattServiceList() {
        return mBluetoothGatt.getServices();
    }

    /**
     * 获取某个服务下面的特征
     *
     * @param bleGattService BluetoothGattService
     * @return List<BluetoothGattCharacteristic>
     */
    public List<BluetoothGattCharacteristic> getBluetoothGattCharacteristicList(BluetoothGattService bleGattService) {
        return bleGattService.getCharacteristics();
    }

    public void init() {
        //TODO 可进行所有模块都要进行的初始化操作

    }


    public void readRssi() {
        sendDataNow(new SendDataBean(null, null, XBleStaticConfig.RSSI_DATA, null));
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
     * 开启多个Indication,如果多个服务,可重复调用
     *
     * @param uuidService    uuidService
     * @param uuidIndication uuidIndication
     */
    public void setIndication(UUID uuidService, UUID... uuidIndication) {
        for (UUID uuid : uuidIndication) {
            sendOpenIndication(uuidService, uuid);
        }
    }


    /**
     * 开启所有的Notify
     */
    public void setNotifyAll() {
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        for (BluetoothGattService service : services) {
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                int properties = characteristic.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0x00) {
                    UUID uuid = characteristic.getUuid();
                    sendOpenNotify(service.getUuid(), uuid);
                }
            }
        }
    }


    /**
     * 开启所有的Indication
     */
    public void setIndicationAll() {
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        for (BluetoothGattService service : services) {
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                int properties = characteristic.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0x00) {
                    UUID uuid = characteristic.getUuid();
                    sendOpenIndication(service.getUuid(), uuid);
                }
            }
        }
    }

    /**
     * 设置通知,有发送队列,不会马上生效,会等待系统回调设置成功后再会设置下一个,一般间隔在100ms左右,与固件性能有关
     */
    private void sendOpenNotify(UUID uuidService, UUID uuidNotify) {
        mLinkedList.addFirst(new SendDataBean(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, uuidNotify, XBleStaticConfig.NOTICE_DATA, uuidService));
        if (mLinkedList.size() <= 1) {
            mHandler.removeMessages(SEND_DATA_KEY);
            SendDataBean sendDataBean = mLinkedList.getLast();
            sendData(sendDataBean);
        }
    }

    /**
     * 设置通知,有发送队列,不会马上生效,会等待系统回调设置成功后再会设置下一个,一般间隔在100ms左右,与固件性能有关
     */
    private void sendOpenIndication(UUID uuidService, UUID uuidNotify) {
        mLinkedList.addFirst(new SendDataBean(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE, uuidNotify, XBleStaticConfig.NOTICE_DATA, uuidService));
        if (mLinkedList.size() <= 1) {
            mHandler.removeMessages(SEND_DATA_KEY);
            SendDataBean sendDataBean = mLinkedList.getLast();
            sendData(sendDataBean);
        }
    }

    public void setCloseNotify(UUID uuidService, UUID uuidNotify) {
        mLinkedList.addFirst(new SendDataBean(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, uuidNotify, XBleStaticConfig.NOTICE_DATA, uuidService));
        if (mLinkedList.size() <= 1) {
            SendDataBean sendDataBean = mLinkedList.getLast();
            sendData(sendDataBean);
        }
    }


    /**
     * 断开连接
     *
     * @param notice 断开后是否需要系统回调通知
     */
    public final void disconnect(boolean notice) {
        if (mBluetoothGatt != null) {
            synchronized (BluetoothGatt.class) {
                if (mBluetoothGatt != null) {
                    mHandler.removeCallbacksAndMessages(null);
                    if (!notice) {
//                        mBluetoothGatt.disconnect();
                        //close后系统将不会再回调onConnectionStateChange通知
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        return;
                    }
                    mBluetoothGatt.disconnect();
                    onDisConnected();
                }
            }
        }
        XBleL.e(TAG, "断开连接:" + mac);
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
        XBleL.i("断开连接,清空发送队列");
        if (mOnDisConnectedListener != null) {
            mOnDisConnectedListener.onDisConnected();
        }

        if (mOnBleDisConnectedListeners!=null){
            for (onBleDisConnectedListener onBleDisConnectedListener : mOnBleDisConnectedListeners) {
                onBleDisConnectedListener.onDisConnected();
            }
        }

        //清空发送队列
        mHandler.removeCallbacksAndMessages(null);
        mOnBleCharacteristicListeners.clear();
        mOnBleNotifyDataListeners.clear();
        mOnBleDisConnectedListeners.clear();


    }


    /**
     * 通知返回数据
     */
    public final void notifyData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicChanged(characteristic);
        }

        if (mOnBleCharacteristicListeners!=null) {
            for (OnBleCharacteristicListener onBleCharacteristicListener : mOnBleCharacteristicListeners) {
                onBleCharacteristicListener.onCharacteristicChanged(characteristic);
            }
        }



        if (mOnNotifyDataListener != null) {
            byte[] value = characteristic.getValue();
            mOnNotifyDataListener.onNotifyData(characteristic, value);
        }
        if (mOnBleNotifyDataListeners != null) {
            for (OnBleNotifyDataListener onBleNotifyDataListener : mOnBleNotifyDataListeners) {
                byte[] value = characteristic.getValue();
                onBleNotifyDataListener.onNotifyData(characteristic, value);
            }
        }
    }


    public final void setRssi(int rssi) {
        this.mRssi = rssi;
        if (mOnBleRssiListener != null) {
            mOnBleRssiListener.OnRssi(rssi);
        }
    }

    /**
     * 返回的Mtu,系统返回setMtu后会触发,需要硬件支持设置才会生效
     *
     * @param mtu 吞吐量(23~517)
     */
    public void OnMtu(int mtu) {
        if (mOnBleMtuListener != null) {
            mOnBleMtuListener.OnMtu(mtu);
        }

    }

    /**
     * 更新的连接参数返回
     *
     * @param interval 间隔
     * @param latency  延迟
     * @param timeout  超时
     */
    public void getConnectionUpdated(int interval, int latency, int timeout) {
        XBleL.i("interval=" + interval + "  latency=" + latency + "   timeout=" + timeout);

    }


    /**
     * 返回的Mtu
     *
     * @param mtu 实际支持的最大字节数
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setMtu(int mtu) {
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.requestMtu(mtu);
        }
        return false;
    }


    /**
     * 设置连接参数
     *
     * @param connectionPriority 参数
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}默认
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}高功率,提高传输速度
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}低功率,传输速度减慢,更省电
     * @return 结果
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setConnectPriority(int connectionPriority) {
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.requestConnectionPriority(connectionPriority);
        }
        return false;
    }


    @CallSuper
    public void readData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicReadOK(characteristic);
        }

        if (mOnBleCharacteristicListeners!=null) {
            for (OnBleCharacteristicListener onBleCharacteristicListener : mOnBleCharacteristicListeners) {
                onBleCharacteristicListener.onCharacteristicReadOK(characteristic);
            }
        }
    }


    @CallSuper
    public void writeData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicWriteOK(characteristic);
        }

        if (mOnBleCharacteristicListeners!=null) {
            for (OnBleCharacteristicListener onBleCharacteristicListener : mOnBleCharacteristicListeners) {
                onBleCharacteristicListener.onCharacteristicWriteOK(characteristic);
            }
        }
    }

    @CallSuper
    public void descriptorWriteOk(BluetoothGattDescriptor descriptor) {
        if (descriptor != null) {
            if (mOnCharacteristicListener != null) {
                mOnCharacteristicListener.onDescriptorWriteOK(descriptor);
            }

            if (mOnBleCharacteristicListeners!=null) {
                for (OnBleCharacteristicListener onBleCharacteristicListener : mOnBleCharacteristicListeners) {
                    onBleCharacteristicListener.onDescriptorWriteOK(descriptor);
                }
            }
            mHandler.removeMessages(SEND_DATA_KEY);
            mHandler.sendEmptyMessage(SEND_DATA_KEY);
        }else {
            mHandler.removeMessages(SEND_DATA_KEY);
            mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);
        }

    }


    /**
     * 发送数据
     *
     * @param sendDataBean SendDataBean
     */
    public synchronized void sendData(SendDataBean sendDataBean) {
        if (sendDataBean == null) {
            return;
        }
        //消息是否需要置顶发送,默认false
        if (sendDataBean.isTop()) {
            mLinkedList.addLast(sendDataBean);
        } else {
            mLinkedList.addFirst(sendDataBean);
        }
        if (!mSendStatus) {
            mHandler.removeMessages(SEND_DATA_KEY);
            mHandler.sendEmptyMessage(SEND_DATA_KEY);
        }
    }


    /**
     * 马上发送数据,需要握手成功
     *
     * @param sendDataBean SendDataBean
     */
    public synchronized boolean sendDataNow(SendDataBean sendDataBean) {
        if (sendDataBean == null) {
            return false;
        }
        return sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
    }

    private volatile boolean mSendStatus = false;

    private final Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SEND_DATA_KEY) {
                if (mLinkedList.size() > 0) {
                    mSendStatus = true;
                    SendDataBean sendDataBean = mLinkedList.pollLast();
                    if (sendDataBean != null) {
                        mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);//设置间隔,避免发送失败
                        boolean result = sendCmd(sendDataBean.getHex(), sendDataBean.getUuid(), sendDataBean.getType(), sendDataBean.getUuidService());
                        if (mResend) {
                            if (!result) {
                                //发送失败
                                if (sendDataBean.getResendNumber() < mResendNumber) {
                                    sendDataBean.addResendNumber();
                                    sendDataBean.setTop(true);
                                    mLinkedList.addFirst(sendDataBean);
                                }
                            }
                        }
                    }else {
                        mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);//设置间隔,避免发送失败
                    }
                }else {
                    mSendStatus = false;
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
    private synchronized boolean sendCmd(byte[] hex, UUID uuid, int type, UUID uuidService) {
        boolean sendOk = true;
        try {
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                BluetoothGattService mGattService = MyBleDeviceUtils.getService(gatt, uuidService);
                if (mGattService != null && uuid != null) {
                    BluetoothGattCharacteristic mCharacteristic = MyBleDeviceUtils.getServiceWrite(mGattService, uuid);
                    if (mCharacteristic != null) {
                        if (hex != null) {
                            mCharacteristic.setValue(hex);
                        }
                        int properties = mCharacteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            //写,无回复 WRITE_TYPE_NO_RESPONSE
                            mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        } else {
                            mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        }
                        switch (type) {
                            case XBleStaticConfig.READ_DATA:
                                sendOk = gatt.readCharacteristic(mCharacteristic);
                                if (mOnBleSendResultListener != null) {
                                    mOnBleSendResultListener.onReadResult(uuid, sendOk);
                                }
                                break;

                            case XBleStaticConfig.WRITE_DATA:
                                sendOk = gatt.writeCharacteristic(mCharacteristic);
                                if (mOnBleSendResultListener != null) {
                                    mOnBleSendResultListener.onWriteResult(uuid, sendOk);
                                }
                                break;

                            case XBleStaticConfig.RSSI_DATA:
                                sendOk = gatt.readRemoteRssi();
                                break;

                            case XBleStaticConfig.NOTICE_DATA:
                            case XBleStaticConfig.INDICATION_DATA:
                                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0x00 || (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0x00) {
                                    gatt.setCharacteristicNotification(mCharacteristic, true);
                                    BluetoothGattDescriptor bluetoothGattDescriptor = mCharacteristic.getDescriptor(XBleStaticConfig.UUID_NOTIFY_DESCRIPTOR);
                                    if (bluetoothGattDescriptor != null) {
                                        bluetoothGattDescriptor.setValue(hex);
                                        sendOk = gatt.writeDescriptor(bluetoothGattDescriptor);
                                        if (mOnBleSendResultListener != null) {
                                            mOnBleSendResultListener.onNotifyResult(uuid, sendOk);
                                        }
                                        if (!sendOk) {
                                            XBleL.e(TAG, "NOTICE_DATA:UUID=" + uuid + " || false");
                                            descriptorWriteOk(null);
                                            return false;
                                        }else {
                                            mHandler.removeMessages(SEND_DATA_KEY);
                                        }
                                    } else {
                                        descriptorWriteOk(null);
                                    }
                                } else {
                                    descriptorWriteOk(null);
                                }
                                break;

                            default:

                                break;
                        }
                    } else if (type == XBleStaticConfig.NOTICE_DATA) {
                        //不支持的uuid,回调设置下一个
                        descriptorWriteOk(null);
                    }
                } else if (type == XBleStaticConfig.RSSI_DATA) {
                    gatt.readRemoteRssi();
                } else if (type == XBleStaticConfig.NOTICE_DATA) {
                    //不支持的uuid,回调设置下一个
                    descriptorWriteOk(null);
                }
            } else if (type == XBleStaticConfig.NOTICE_DATA) {
                //不支持的uuid,回调设置下一个
                descriptorWriteOk(null);
            }
        } catch (Exception e) {
            XBleL.e(TAG, "读/写/设置通知,异常:" + e.toString());
            e.printStackTrace();
        }
        return sendOk;
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


    public BluetoothGatt getBluetoothGatt() {
        return mBluetoothGatt;
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

    /**
     * 是否需要重发机制
     *
     * @param resend       默认false
     * @param resendNumber resend为false的时候无效重发次数,默认3
     */
    public void setResend(boolean resend, int resendNumber) {
        mResend = resend;
        mResendNumber = resendNumber;
    }


    //---------------onBleDisConnectedListener-------------

    /**
     * 设置监听断连状态
     * 将会在3.x的版本中移除
     * see {@link BleDevice#addOnDisConnectedListener} or {@link BleDevice#removeOnDisConnectedListener}
     */
    @Deprecated
    public void setOnDisConnectedListener(onBleDisConnectedListener onDisConnectedListener) {
        mOnDisConnectedListener = onDisConnectedListener;
    }

    private List<onBleDisConnectedListener> mOnBleDisConnectedListeners=new ArrayList<>();

    /**
     * 设置监听断连状态
     * @param listener 断连接口
     */
    public void addOnDisConnectedListener(onBleDisConnectedListener listener) {
        if (!mOnBleDisConnectedListeners.contains(listener)) {
            mOnBleDisConnectedListeners.add(listener);
        }
    }

    /**
     * 移除监听断连状态
     * @param listener 断连接口
     */
    public void removeOnDisConnectedListener(onBleDisConnectedListener listener) {
        if (mOnBleDisConnectedListeners!=null) {
            mOnBleDisConnectedListeners.remove(listener);
        }
    }

    //------------------OnBleCharacteristicListener--------------------------

    /**
     * 设置监听BLE返回的数据,包含read,Write,notify等
     * 将会在3.x的版本中移除
     * see {@link BleDevice#addOnCharacteristicListener} or {@link BleDevice#removeOnCharacteristicListener}
     */
    @Deprecated
    public void setOnCharacteristicListener(OnBleCharacteristicListener onCharacteristicListener) {
        mOnCharacteristicListener = onCharacteristicListener;
    }

    private List<OnBleCharacteristicListener> mOnBleCharacteristicListeners = new ArrayList<>();

    /**
     * 设置监听BLE返回的数据,包含read,Write,notify等
     * @param onCharacteristicListener BLE透传数据接口
     */
    public void addOnCharacteristicListener(OnBleCharacteristicListener onCharacteristicListener) {
        if (!mOnBleCharacteristicListeners.contains(onCharacteristicListener)) {
            mOnBleCharacteristicListeners.add(onCharacteristicListener);
        }
    }


    /**
     * 移除监听BLE返回的数据,包含read,Write,notify(Indication)等
     * @param onCharacteristicListener BLE透传数据接口
     */
    public void removeOnCharacteristicListener(OnBleCharacteristicListener onCharacteristicListener) {
        if (mOnBleCharacteristicListeners != null) {
            mOnBleCharacteristicListeners.remove(onCharacteristicListener);
        }
    }


    //------------------OnBleNotifyDataListener--------------------------

    /**
     * 设置监听notify和Indication数据
     * 将会在3.x的版本中移除
     * see {@link BleDevice#addOnNotifyDataListener} or {@link BleDevice#removeOnNotifyDataListener}
     */
    @Deprecated
    public void setOnNotifyDataListener(OnBleNotifyDataListener onNotifyDataListener) {
        mOnNotifyDataListener = onNotifyDataListener;
    }


    private List<OnBleNotifyDataListener> mOnBleNotifyDataListeners = new ArrayList<>();

    /**
     * 设置监听notify和Indication数据
     * @param onNotifyDataListener BLE Notify(Indication)透传数据接口
     */
    public void addOnNotifyDataListener(OnBleNotifyDataListener onNotifyDataListener) {
        if (!mOnBleNotifyDataListeners.contains(onNotifyDataListener)) {
            mOnBleNotifyDataListeners.add(onNotifyDataListener);
        }
    }

    /**
     * 移除监听notify和Indication数据
     * @param onNotifyDataListener BLE Notify(Indication)透传数据接口
     */
    public void removeOnNotifyDataListener(OnBleNotifyDataListener onNotifyDataListener) {
        if (mOnBleNotifyDataListeners != null) {
            mOnBleNotifyDataListeners.remove(onNotifyDataListener);
        }

    }

}

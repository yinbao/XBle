package com.xing.xblelibrary.device;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.xing.xblelibrary.config.XBleStaticConfig;
import com.xing.xblelibrary.listener.OnBleMtuListener;
import com.xing.xblelibrary.listener.OnBleRssiListener;
import com.xing.xblelibrary.listener.OnBleSendResultListener;
import com.xing.xblelibrary.listener.OnBleCharacteristicListener;
import com.xing.xblelibrary.listener.OnBleNotifyDataListener;
import com.xing.xblelibrary.listener.onBleDisConnectedListener;
import com.xing.xblelibrary.utils.XBleL;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;

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

    private final int WRITE_TIMEOUT = 50;
    private final int SEND_DATA_KEY = 1;
    private final int SEND_NOTIFY_KEY = 2;
    private final int SEND_DATA_KEY_TIME_OUT = 4;
    private int mSendDataInterval = 200;
    /**
     * Notify发送间隔,默认50ms
     */
    private int mSendNotifyInterval = 50;
    /**
     * 写数据超时时间
     */
    private int mWriteTimeOut = WRITE_TIMEOUT;
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
    /**
     * 实时发送队列
     */
    private final LinkedList<SendDataBean> mLinkedListNow = new LinkedList<>();
    private final LinkedList<SendDataBean> mLinkedListNotify = new LinkedList<>();
    /**
     * 实时发送队列优先级,默认优先实时队列
     */
    private boolean mLinkedListNowPriority = true;
    /**
     * 是否需要重发
     */
    private boolean mResend = false;
    /**
     * 重发次数
     */
    private int mResendNumber = 3;
    /**
     * 写入数据状态,true=正在等待系统回调
     */
    private volatile boolean mWriteStatus = false;

    private OnBleSendResultListener mOnBleSendResultListener;
    private onBleDisConnectedListener mOnDisConnectedListener;
    private OnBleNotifyDataListener mOnNotifyDataListener;

    private OnBleRssiListener mOnBleRssiListener;
    private OnBleMtuListener mOnBleMtuListener;
    private OnBleCharacteristicListener mOnCharacteristicListener;


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

    private void init() {
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
     * 设置通知,有发送队列,不会马上生效,会等待系统回调设置成功后再会设置下一个,一般间隔在100ms左右,与固件性能有关
     */
    private void sendOpenNotify(UUID uuidService, UUID uuidNotify) {
        mLinkedListNotify.addFirst(new SendDataBean(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, uuidNotify, XBleStaticConfig.NOTICE_DATA, uuidService));
        if (mLinkedListNotify.size() <= 1) {
            if (!mHandler.hasMessages(SEND_NOTIFY_KEY)) {
                mHandler.sendEmptyMessage(SEND_NOTIFY_KEY);
            }
        }
    }

    public void setCloseNotify(UUID uuidService, UUID uuidNotify) {
        mLinkedListNotify.addFirst(new SendDataBean(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, uuidNotify, XBleStaticConfig.NOTICE_DATA, uuidService));
        if (mLinkedListNotify.size() <= 1) {
            if (!mHandler.hasMessages(SEND_NOTIFY_KEY)) {
                mHandler.sendEmptyMessage(SEND_NOTIFY_KEY);
            }
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
        mLinkedList.clear();
        mLinkedListNow.clear();
        mLinkedListNotify.clear();
        mWriteStatus = false;
        //清空发送队列
        mHandler.removeCallbacksAndMessages(null);
        if (mOnDisConnectedListener != null) {
            mOnDisConnectedListener.onDisConnected();
        }
    }


    /**
     * 通知返回数据
     */
    public final void notifyData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicChanged(characteristic);
        }


        if (mOnNotifyDataListener != null) {
            UUID uuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            mOnNotifyDataListener.onNotifyData(characteristic, value);
        }
    }


    public final void setRssi(int rssi) {
        mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
        mWriteStatus = false;
        if (!mHandler.hasMessages(SEND_DATA_KEY)) {
            mHandler.sendEmptyMessage(SEND_DATA_KEY);
        }
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
        mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
        mWriteStatus = false;
        if (!mHandler.hasMessages(SEND_DATA_KEY)) {
            mHandler.sendEmptyMessage(SEND_DATA_KEY);
        }
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
     * 请求设置的Mtu
     *
     * @param mtu 实际支持的最大字节数
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setMtu(int mtu) {
        if (mBluetoothGatt != null) {
            boolean b = false;
            if (mtu > 20) {
                b = true;
                byte[] byteMtu = new byte[2];
                byteMtu[0] = (byte) (mtu >> 8);
                byteMtu[1] = (byte) mtu;
                SendDataBean sendDataBean = new SendDataBean(byteMtu, null, XBleStaticConfig.MTU_DATA, null);
                sendDataBean.setTop(true);
                sendData(sendDataBean);
            }
            return b;
        }
        return false;
    }


    /**
     * 设置首选物理层
     *
     * @param txPhy tx phy  {@link BluetoothDevice#PHY_LE_1M_MASK, BluetoothDevice#PHY_LE_2M_MASK,BluetoothDevice#PHY_LE_CODED_MASK}
     * @param rxPhy rx phy {@link BluetoothDevice#PHY_LE_1M_MASK, BluetoothDevice#PHY_LE_2M_MASK,BluetoothDevice#PHY_LE_CODED_MASK}
     * @return boolean
     */
    @SuppressLint({"NewApi", "MissingPermission"})
    public boolean setPreferredPhy(int txPhy, int rxPhy) {
        if (mBluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mBluetoothGatt.setPreferredPhy(txPhy, rxPhy, BluetoothDevice.PHY_OPTION_NO_PREFERRED);
            }
            return true;
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
        mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
        mWriteStatus = false;
        if (!mHandler.hasMessages(SEND_DATA_KEY)) {
            mHandler.sendEmptyMessage(SEND_DATA_KEY);
        }
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicReadOK(characteristic);
        }

    }


    @CallSuper
    public void writeData(BluetoothGattCharacteristic characteristic) {
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicWriteOK(characteristic);
        }
        mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
        if (!mLinkedListNow.isEmpty()) {
            SendDataBean sendDataBean = mLinkedListNow.pollLast();
            if (sendDataBean != null) {
                boolean result = sendCmd(sendDataBean);
                if (!result) {
                    handleSendFail(sendDataBean, true);
                }
            }
        } else {
            mWriteStatus = false;
            if (!mHandler.hasMessages(SEND_DATA_KEY)) {
                mHandler.sendEmptyMessage(SEND_DATA_KEY);
            }
        }
    }

    @CallSuper
    public void descriptorWriteOk(BluetoothGattDescriptor descriptor) {
        if (descriptor != null) {
            mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
            mWriteStatus = false;
            UUID uuid = descriptor.getCharacteristic().getUuid();
            XBleL.i(TAG, "notify成功:" + uuid.toString() + " " + mLinkedListNotify.size());
            if (mOnCharacteristicListener != null) {
                mOnCharacteristicListener.onDescriptorWriteOK(descriptor);
            }
        }
        if (mLinkedListNotify != null) {
            if (!mLinkedListNotify.isEmpty()) {
                if (mWriteStatus) {
                    //正在通知状态,需要等待系统回应结果
                    mHandler.removeMessages(SEND_DATA_KEY);
                    mHandler.removeMessages(SEND_NOTIFY_KEY);
                    mHandler.sendEmptyMessageDelayed(SEND_NOTIFY_KEY, mSendNotifyInterval);
                    return;
                }
                SendDataBean sendDataBean = mLinkedListNotify.pollLast();
                sendCmd(sendDataBean);
            } else {
                if (!mWriteStatus) {
                    XBleL.i(TAG, "notify成功:发送消息");
                    mHandler.removeMessages(SEND_DATA_KEY);
                    mHandler.sendEmptyMessage(SEND_DATA_KEY);
                }
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
        if (mLinkedList.size() <= 1 && mLinkedListNotify.isEmpty()) {
            if (!mHandler.hasMessages(SEND_DATA_KEY)) {
                mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval / 2);
            }
        }
    }


    /**
     * 马上发送数据(走实时队列,优先发送)
     *
     * @param sendDataBean SendDataBean
     */
    public synchronized void sendDataNow(SendDataBean sendDataBean) {
        if (sendDataBean == null) {
            return;
        }
        mLinkedListNow.addFirst(sendDataBean);
        if (mLinkedListNow.size() <= 1) {
            if (!mHandler.hasMessages(SEND_DATA_KEY)) {
                mHandler.sendEmptyMessage(SEND_DATA_KEY);
            }
        }
    }


    private final Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SEND_DATA_KEY_TIME_OUT) {
                //重置发送状态, 一般情况下写入数据只需要几ms,如果超过一定时间没有写入成功,则重置写入状态,避免影响其他指令无法发送
                if (mWriteStatus) {
                    int what = SEND_DATA_KEY;
                    mWriteStatus = false;
                    mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
                    if (!mLinkedListNotify.isEmpty()) {
                        what = SEND_NOTIFY_KEY;
                    }
                    if (!mHandler.hasMessages(what)) {
                        mHandler.sendEmptyMessage(what);
                    }
                    XBleL.i(TAG, "重置发送状态:" + mWriteStatus + "  what:" + what);
                }
            } else if (msg.what == SEND_NOTIFY_KEY) {
                descriptorWriteOk(null);
                if (mWriteStatus) {
                    return;
                }
                if (!mHandler.hasMessages(SEND_DATA_KEY_TIME_OUT)) {
                    //避免出现一直等待的情况,设置超时操作
                    mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY_TIME_OUT, mWriteTimeOut);
                }
            } else if (msg.what == SEND_DATA_KEY) {
                if (mWriteStatus) {
                    return;
                }
                if (mLinkedListNowPriority) {
                    if (!mLinkedListNow.isEmpty()) {
                        SendDataBean sendDataBean = mLinkedListNow.pollLast();
                        boolean result = sendCmd(sendDataBean);
                        if (!result) {
                            mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
                            handleSendFail(sendDataBean, true);
                        }
                    } else if (!mLinkedList.isEmpty()) {
                        SendDataBean sendDataBean = mLinkedList.pollLast();
                        boolean result = sendCmd(sendDataBean);
                        if (!result) {
                            mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
                            handleSendFail(sendDataBean, false);
                        }
                        mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);//设置间隔,避免发送失败
                    }
                } else {
                    if (!mLinkedList.isEmpty()) {
                        SendDataBean sendDataBean = mLinkedList.pollLast();
                        boolean result = sendCmd(sendDataBean);
                        if (!result) {
                            mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
                            handleSendFail(sendDataBean, false);
                        }
                        mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);//设置间隔,避免发送失败
                    } else if (!mLinkedListNow.isEmpty()) {
                        SendDataBean sendDataBean = mLinkedListNow.pollLast();
                        boolean result = sendCmd(sendDataBean);
                        if (!result) {
                            mHandler.removeMessages(SEND_DATA_KEY_TIME_OUT);
                            handleSendFail(sendDataBean, true);
                        }
                    }
                }
                if (!mHandler.hasMessages(SEND_DATA_KEY_TIME_OUT)) {
                    //避免出现一直等待的情况,设置超时操作
                    mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY_TIME_OUT, mWriteTimeOut);
                }
            }
        }
    };

    /**
     * 发送失败重发处理
     *
     * @param sendDataBean 发送对象
     * @param nowQueue     是否为实时队列
     */
    private void handleSendFail(SendDataBean sendDataBean, boolean nowQueue) {
        if (!mResend) {
            return;
        }
        if (sendDataBean != null && sendDataBean.getResendNumber() < mResendNumber) {
            sendDataBean.addResendNumber();
            sendDataBean.setTop(true);
            if (nowQueue) {
                mLinkedListNow.addFirst(sendDataBean);
                mHandler.sendEmptyMessageDelayed(SEND_DATA_KEY, mSendDataInterval);
            } else {
                mLinkedList.addFirst(sendDataBean);
            }
        } else {
            if (mOnBleSendResultListener != null) {
                mOnBleSendResultListener.onWriteAndReSendFail(sendDataBean, mResendNumber);
            }
        }
    }

    /**
     * 发送信息
     *
     * @param sendDataBean 发送对象
     * @return 操作结果
     */
    private synchronized boolean sendCmd(SendDataBean sendDataBean) {
        boolean sendOk = true;
        try {
            if (sendDataBean == null) {
                return true;
            }
            byte[] hex = sendDataBean.getHex();
            UUID uuid = sendDataBean.getUuid();
            int type = sendDataBean.getType();
            UUID uuidService = sendDataBean.getUuidService();
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
                            mWriteTimeOut = WRITE_TIMEOUT;
                            mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        } else {
                            mWriteTimeOut = WRITE_TIMEOUT * 10;
                            mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        }
                        switch (type) {
                            case XBleStaticConfig.READ_DATA:
                                mWriteStatus = true;
                                sendOk = gatt.readCharacteristic(mCharacteristic);
                                if (mOnBleSendResultListener != null) {
                                    mOnBleSendResultListener.onReadResult(uuid, sendOk);
                                }
                                break;

                            case XBleStaticConfig.WRITE_DATA:
                                mWriteStatus = true;
                                sendOk = gatt.writeCharacteristic(mCharacteristic);
                                if (mOnBleSendResultListener != null) {
                                    mOnBleSendResultListener.onWriteResult(uuid, sendOk);
                                }
                                break;

                            case XBleStaticConfig.RSSI_DATA:
                                sendOk = gatt.readRemoteRssi();
                                break;

                            case XBleStaticConfig.NOTICE_DATA:
                                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0x00) {
                                    //支持notify
                                    gatt.setCharacteristicNotification(mCharacteristic, true);
                                    BluetoothGattDescriptor bluetoothGattDescriptor = mCharacteristic.getDescriptor(XBleStaticConfig.UUID_NOTIFY_DESCRIPTOR);
                                    if (bluetoothGattDescriptor != null) {
                                        mWriteStatus = true;
                                        bluetoothGattDescriptor.setValue(hex);
                                        sendOk = gatt.writeDescriptor(bluetoothGattDescriptor);
                                        if (mOnBleSendResultListener != null) {
                                            mOnBleSendResultListener.onNotifyResult(uuid, sendOk);
                                        }
                                        if (!sendOk) {
                                            XBleL.e(TAG, "NOTICE_DATA:UUID=" + uuid + " || false");
                                            descriptorWriteOk(null);
                                            return false;
                                        }
                                    } else {
                                        descriptorWriteOk(null);
                                    }
                                } else {
                                    descriptorWriteOk(null);
                                }

                                break;

                            case XBleStaticConfig.MTU_DATA:
                                if (hex != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    int mtu = (((hex[0] & 0xFF) << 8) | (hex[1] & 0xFF));
                                    gatt.requestMtu(mtu);
                                }
                                break;
                        }
                        XBleL.i(TAG, "type:" + type + " UUID=" + uuid + " || " + sendOk);
                    } else if (type == XBleStaticConfig.NOTICE_DATA) {
                        //不支持的uuid,回调设置下一个
                        descriptorWriteOk(null);
                    }
                } else if (type == XBleStaticConfig.RSSI_DATA) {
                    sendOk = gatt.readRemoteRssi();
                } else if (type == XBleStaticConfig.MTU_DATA) {
                    if (hex != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        int mtu = (((hex[0] & 0xFF) << 8) | (hex[1] & 0xFF));
                        gatt.requestMtu(mtu);
                    }
                } else if (type == XBleStaticConfig.NOTICE_DATA) {
                    //不支持的uuid,回调设置下一个
                    descriptorWriteOk(null);
                }
            } else if (type == XBleStaticConfig.NOTICE_DATA) {
                //不支持的uuid,回调设置下一个
                descriptorWriteOk(null);
            }
        } catch (Exception e) {
            sendOk = false;
            XBleL.e(TAG, "读/写/设置通知,异常:" + e.toString());
            e.printStackTrace();
        }
        return sendOk;
    }


    public void setOnCharacteristicListener(OnBleCharacteristicListener onCharacteristicListener) {
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
     * 设置Notify发送间隔
     * 默认50ms,最小值20ms
     * 写入中等待系统回调时,按此间隔延迟继续发送下一个Notify
     *
     * @param sendNotifyInterval 单位(ms)
     */
    public void setSendNotifyInterval(int sendNotifyInterval) {
        if (sendNotifyInterval < 20) {
            sendNotifyInterval = 20;
        }
        mSendNotifyInterval = sendNotifyInterval;
    }

    /**
     * 设置实时发送队列和延迟发送队列的优先级
     *
     * @param linkedListNowPriority true-优先实时队列, false-优先延迟队列, 默认是true
     */
    public void setLinkedListNowPriority(boolean linkedListNowPriority) {
        mLinkedListNowPriority = linkedListNowPriority;
    }

    /**
     * 是否需要重发机制
     *
     * @param resend 默认false
     * @deprecated {@link BleDevice#setResend(boolean resend, int resendNumber)}
     */
    @Deprecated
    public void setResend(boolean resend) {
        setResend(resend, 3);
    }

    /**
     * 是否需要重发机制
     *
     * @param resend       默认false
     * @param resendNumber resend为false的时候无效,重发次数,默认3
     */
    public void setResend(boolean resend, int resendNumber) {
        mResend = resend;
        mResendNumber = resendNumber;
    }

    //---------------


    public void setOnDisConnectedListener(onBleDisConnectedListener onDisConnectedListener) {
        mOnDisConnectedListener = onDisConnectedListener;
    }

    public void setOnNotifyDataListener(OnBleNotifyDataListener onNotifyDataListener) {
        mOnNotifyDataListener = onNotifyDataListener;
    }


}

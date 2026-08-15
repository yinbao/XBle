package com.xing.xblelibrary.device;

import android.annotation.SuppressLint;
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
import androidx.annotation.Nullable;

import com.xing.xblelibrary.config.XBleStaticConfig;
import com.xing.xblelibrary.listener.OnBleCharacteristicRequestListener;
import com.xing.xblelibrary.listener.OnBleNotifyDataListener;
import com.xing.xblelibrary.listener.onBleDisConnectedListener;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;
import com.xing.xblelibrary.utils.XBleL;

import java.util.LinkedList;
import java.util.UUID;

/**
 * 手机作为<strong>外围设备（Peripheral / GATT Server）</strong>时，与某一个已连接<strong>中央设备（Central）</strong>
 * 之间的收发对象（由 {@link com.xing.xblelibrary.server.XBleServer} 在连接后创建）。
 * <p>
 * 职责（APP 外围 ↔ 对端中央）：
 * <ul>
 *   <li><b>返回</b>：响应中央的读请求（{@link #onCharacteristicReadRequest} → {@code sendResponse}）</li>
 *   <li><b>接收</b>：中央写入本机特征时回调 {@link #setOnNotifyDataListener}</li>
 *   <li><b>发送</b>：本机通过 Notify 主动把数据推给中央
 *       （{@link #sendData} / {@link #sendDataNow}，底层为
 *       {@link BluetoothGattServer#notifyCharacteristicChanged}，
 *       {@link SendDataBean} 类型使用 {@link XBleStaticConfig#WRITE_DATA}）</li>
 * </ul>
 * 对比 {@link BleDevice}：那是手机作为<strong>中央</strong>连接外设时的对象；本类是手机作为<strong>外围</strong>被连接时的对象。
 * <p>
 * 发送队列对齐 {@link BleDevice}（主线程 Handler 串行）：
 * <ul>
 *   <li>{@link #sendData}：普通队列，Notify 成功后再隔 {@code mSendDataInterval} 发下一条</li>
 *   <li>{@link #sendDataNow}：实时队列（默认优先），回调后立即发下一条</li>
 * </ul>
 * 断开时只 {@link BluetoothGattServer#cancelConnection}，<b>不会</b> close 整个 GattServer
 * （Server 由 XBleServer 持有，可能同时服务多个中央连接）。
 * <p>
 * 调用方须在运行时持有 {@code BLUETOOTH_CONNECT} 等权限；库内不做运行时校验。
 */
@SuppressLint("MissingPermission")
public final class AdBleDevice implements OnBleCharacteristicRequestListener {

    private static final String TAG = AdBleDevice.class.getSimpleName();

    /** 等待 onNotificationSent 的默认超时（ms） */
    private static final int NOTIFY_TIMEOUT_DEFAULT = 500;

    private static final int MSG_PROCESS = 1;
    private static final int MSG_TIMEOUT = 2;

    /** 普通队列包间隔（ms），默认 10 */
    private int mSendDataInterval = 10;
    private int mNotifyTimeOut = NOTIFY_TIMEOUT_DEFAULT;

    private BluetoothGattServer mBluetoothGattServer;
    private final BluetoothDevice mBluetoothDevice;
    private volatile boolean connectSuccess;
    private final String mac;
    private String mName;
    private int mMtu = 23;

    private final LinkedList<SendDataBean> mWriteQueue = new LinkedList<>();
    private final LinkedList<SendDataBean> mWriteNowQueue = new LinkedList<>();
    /** true：优先实时队列 */
    private boolean mWriteNowPriority = true;
    private boolean mLastWriteFromNow;
    private long mPendingProcessDelay;
    /** 已发出 Notify，等待 {@link #onNotificationSent} */
    private boolean mBusy;

    private boolean mResend;
    private int mResendNumber = 3;

    private OnBleNotifyDataListener mOnNotifyDataListener;
    private onBleDisConnectedListener mOnDisConnectedListener;

    /**
     * @param device               已连接的中央设备
     * @param bluetoothGattServer  本机 GattServer（由 XBleServer 管理生命周期）
     */
    public AdBleDevice(BluetoothDevice device, BluetoothGattServer bluetoothGattServer) {
        mBluetoothDevice = device;
        mBluetoothGattServer = bluetoothGattServer;
        this.mac = device.getAddress();
        this.mName = device.getName();
        connectSuccess = true;
        XBleL.i("外围连接成功:" + mac);
    }

    public boolean isConnectSuccess() {
        return connectSuccess;
    }

    /**
     * 断开与当前中央设备的连接。
     *
     * @param notice true：调用 {@link BluetoothGattServer#cancelConnection}，系统仍可能回调断开；
     *               false：仅本地清理，不主动 cancel（仍建议尽量 cancel）
     */
    public final void disconnect(boolean notice) {
        synchronized (this) {
            if (notice && mBluetoothGattServer != null && mBluetoothDevice != null) {
                try {
                    mBluetoothGattServer.cancelConnection(mBluetoothDevice);
                } catch (Exception e) {
                    XBleL.e(TAG, "cancelConnection 异常:" + e.getMessage());
                }
            }
            // 注意：不可 close GattServer，否则会影响其他连接与广播服务
            onDisConnected();
        }
        XBleL.e(TAG, "断开连接:" + mac);
    }

    /** 断开当前中央连接（默认需要系统回调） */
    public final void disconnect() {
        disconnect(true);
    }

    /**
     * 断开后清理：状态、队列、Handler、监听。
     * 可被系统断开路径多次调用，内部防重入。
     */
    @CallSuper
    public void onDisConnected() {
        if (!connectSuccess && mWriteQueue.isEmpty() && mWriteNowQueue.isEmpty()) {
            return;
        }
        XBleL.i("外围断开,清空发送队列:" + mac);
        connectSuccess = false;
        mBusy = false;
        mPendingProcessDelay = 0;
        mWriteQueue.clear();
        mWriteNowQueue.clear();
        mHandler.removeCallbacksAndMessages(null);

        onBleDisConnectedListener disConnectedListener = mOnDisConnectedListener;
        clearListeners();
        if (disConnectedListener != null) {
            disConnectedListener.onDisConnected();
        }
    }

    /** 清空业务监听，避免断开后仍持有 Activity */
    public void clearListeners() {
        mOnDisConnectedListener = null;
        mOnNotifyDataListener = null;
    }

    // -------------------- GATT Server：响应 / 接收中央请求 --------------------

    /**
     * 中央发起读特征：用当前特征值 {@code sendResponse} 返回给中央。
     */
    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattCharacteristic characteristic) {
        if (!isAlive() || characteristic == null) {
            return;
        }
        byte[] value = characteristic.getValue();
        if (value == null) {
            value = new byte[0];
        }
        if (offset > value.length) {
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
            return;
        }
        byte[] offsetValue = offset == 0 ? value : java.util.Arrays.copyOfRange(value, offset, value.length);
        mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, offsetValue);
    }

    /**
     * 中央写入本机特征：需要时 {@code sendResponse}，并通过 {@link #mOnNotifyDataListener} 把数据交给业务。
     */
    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
        if (!isAlive() || characteristic == null) {
            return;
        }
        if (value != null) {
            characteristic.setValue(value);
        }
        if (responseNeeded) {
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
        if (mOnNotifyDataListener != null) {
            mOnNotifyDataListener.onNotifyData(characteristic, value);
        }
    }

    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                         BluetoothGattDescriptor descriptor,
                                         boolean preparedWrite, boolean responseNeeded,
                                         int offset, byte[] value) {
        // 回复一般由 XBleServer 统一 sendResponse，此处无需重复回复
    }

    @Override
    public void onMtuChangedRequest(BluetoothDevice device, int mtu) {
        mMtu = mtu;
    }

    /**
     * Notify 发送结果：解除 busy 并按间隔继续调度。
     */
    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
        mHandler.post(() -> {
            if (device != null && mac != null && !mac.equalsIgnoreCase(device.getAddress())) {
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                XBleL.e(TAG, "onNotificationSent 失败 status=" + status);
            }
            finishBusyAndProcess();
        });
    }

    // -------------------- 向中央发送（Notify） --------------------

    /**
     * 向已连接的中央设备推送数据（限速队列）。
     * 底层 Notify；每条在 {@link #onNotificationSent} 后再等 {@link #mSendDataInterval}。
     * {@link SendDataBean#getType()} 请使用 {@link XBleStaticConfig#WRITE_DATA}。
     */
    public void sendData(SendDataBean sendDataBean) {
        if (sendDataBean == null || !isAlive()) {
            return;
        }
        final SendDataBean bean = sendDataBean;
        mHandler.post(() -> {
            if (!isAlive()) {
                return;
            }
            enqueueWriteLocked(bean, false);
            scheduleProcess(0);
        });
    }

    /**
     * 向中央推送数据（实时队列，不额外限速）。
     * 仍等待 {@link #onNotificationSent} 串行，回调后立即发下一条。
     */
    public void sendDataNow(SendDataBean sendDataBean) {
        if (sendDataBean == null || !isAlive()) {
            return;
        }
        final SendDataBean bean = sendDataBean;
        mHandler.post(() -> {
            if (!isAlive()) {
                return;
            }
            enqueueWriteLocked(bean, true);
            scheduleProcess(0);
        });
    }

    private void enqueueWriteLocked(SendDataBean bean, boolean nowQueue) {
        LinkedList<SendDataBean> queue = nowQueue ? mWriteNowQueue : mWriteQueue;
        if (bean.isTop()) {
            queue.addLast(bean);
        } else {
            queue.addFirst(bean);
        }
    }

    // -------------------- 调度 --------------------

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (!isAlive()) {
                return;
            }
            if (msg.what == MSG_TIMEOUT) {
                if (mBusy) {
                    XBleL.i(TAG, "Notify 等待超时,重置 busy 并继续调度");
                    mBusy = false;
                    processNext();
                }
            } else if (msg.what == MSG_PROCESS) {
                processNext();
            }
        }
    };

    private void scheduleProcess(long delayMs) {
        delayMs = Math.max(0, delayMs);
        if (delayMs == 0) {
            mHandler.removeMessages(MSG_PROCESS);
            mHandler.sendEmptyMessage(MSG_PROCESS);
            return;
        }
        if (!mHandler.hasMessages(MSG_PROCESS)) {
            mHandler.sendEmptyMessageDelayed(MSG_PROCESS, delayMs);
        }
    }

    private void finishBusyAndProcess() {
        mHandler.removeMessages(MSG_TIMEOUT);
        mBusy = false;
        long delay = mPendingProcessDelay;
        mPendingProcessDelay = 0;
        scheduleProcess(delay);
    }

    private void processNext() {
        if (!isAlive() || mBusy) {
            return;
        }
        SendDataBean bean = pollWrite();
        if (bean == null) {
            return;
        }
        boolean fromNow = mLastWriteFromNow;
        boolean ok = sendNotify(bean);
        if (!ok) {
            handleSendFail(bean, fromNow);
            scheduleProcess(fromNow ? 0 : mSendDataInterval);
            return;
        }
        mBusy = true;
        mPendingProcessDelay = fromNow ? 0 : mSendDataInterval;
        mHandler.removeMessages(MSG_TIMEOUT);
        mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, mNotifyTimeOut);
    }

    @Nullable
    private SendDataBean pollWrite() {
        if (mWriteNowPriority) {
            if (!mWriteNowQueue.isEmpty()) {
                mLastWriteFromNow = true;
                return mWriteNowQueue.pollLast();
            }
            if (!mWriteQueue.isEmpty()) {
                mLastWriteFromNow = false;
                return mWriteQueue.pollLast();
            }
        } else {
            if (!mWriteQueue.isEmpty()) {
                mLastWriteFromNow = false;
                return mWriteQueue.pollLast();
            }
            if (!mWriteNowQueue.isEmpty()) {
                mLastWriteFromNow = true;
                return mWriteNowQueue.pollLast();
            }
        }
        return null;
    }

    private void handleSendFail(SendDataBean bean, boolean fromNow) {
        if (!mResend || bean == null) {
            return;
        }
        if (bean.getResendNumber() < mResendNumber) {
            bean.addResendNumber();
            bean.setTop(true);
            if (fromNow) {
                mWriteNowQueue.addFirst(bean);
            } else {
                mWriteQueue.addFirst(bean);
            }
        }
    }

    /**
     * 向当前连接的中央设备发送 Notify。
     * 业务侧请使用 {@link XBleStaticConfig#WRITE_DATA} 表示「下发/推送」；其它 type 不支持。
     */
    private boolean sendNotify(SendDataBean bean) {
        try {
            if (bean == null || mBluetoothGattServer == null || mBluetoothDevice == null) {
                return false;
            }
            int type = bean.getType();
            if (type != XBleStaticConfig.WRITE_DATA) {
                XBleL.e(TAG, "外围发送仅支持 WRITE_DATA(Notify), type=" + type);
                return false;
            }
            UUID uuid = bean.getUuid();
            UUID uuidService = bean.getUuidService();
            byte[] hex = bean.getHex();
            BluetoothGattService gattService = mBluetoothGattServer.getService(uuidService);
            if (gattService == null || uuid == null) {
                return false;
            }
            BluetoothGattCharacteristic characteristic = MyBleDeviceUtils.getServiceWrite(gattService, uuid);
            if (characteristic == null) {
                return false;
            }
            if (hex != null) {
                characteristic.setValue(hex);
            }
            boolean ok = mBluetoothGattServer.notifyCharacteristicChanged(mBluetoothDevice, characteristic, false);
            XBleL.i(TAG, "NOTIFY UUID=" + uuid + " || " + ok);
            return ok;
        } catch (Exception e) {
            XBleL.e(TAG, "Notify 异常:" + e);
            e.printStackTrace();
            return false;
        }
    }

    private boolean isAlive() {
        return connectSuccess && mBluetoothGattServer != null && mBluetoothDevice != null;
    }

    // -------------------- getter / setter --------------------

    public String getMac() {
        return mac;
    }

    public String getName() {
        return mName;
    }

    public int getMtu() {
        return mMtu;
    }

    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    public BluetoothGattServer getBluetoothGattServer() {
        return mBluetoothGattServer;
    }

    /**
     * 普通队列包间隔，默认 10ms。仅作用于 {@link #sendData}。
     */
    public void setSendDataInterval(int interval) {
        if (interval < 0) {
            interval = 0;
        }
        mSendDataInterval = interval;
    }

    /**
     * 等待 {@link #onNotificationSent} 的超时，默认 500ms。
     */
    public void setNotifyTimeOut(int notifyTimeOut) {
        if (notifyTimeOut < 50) {
            notifyTimeOut = 50;
        }
        mNotifyTimeOut = notifyTimeOut;
    }

    /**
     * @param writeNowPriority true 优先实时队列（默认）
     */
    public void setLinkedListNowPriority(boolean writeNowPriority) {
        mWriteNowPriority = writeNowPriority;
    }

    /**
     * 发送失败是否重试。
     *
     * @param resend       是否开启
     * @param resendNumber 最大重发次数（不含首次）
     */
    public void setResend(boolean resend, int resendNumber) {
        mResend = resend;
        mResendNumber = Math.max(0, resendNumber);
    }

    /**
     * @deprecated 请使用 {@link #setResend(boolean, int)}
     */
    @Deprecated
    public void setErrRepeatTimes(int errRepeatTimes) {
        setResend(errRepeatTimes > 0, errRepeatTimes);
    }

    public void setOnDisConnectedListener(onBleDisConnectedListener onDisConnectedListener) {
        mOnDisConnectedListener = onDisConnectedListener;
    }

    /**
     * 中央写入本机特征时的数据回调（命名历史原因叫 Notify，实际是 Write Request 上来的数据）。
     */
    public void setOnNotifyDataListener(OnBleNotifyDataListener onNotifyDataListener) {
        mOnNotifyDataListener = onNotifyDataListener;
    }
}

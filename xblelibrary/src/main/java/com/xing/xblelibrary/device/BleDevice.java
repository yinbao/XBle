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

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

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

/**
 * BLE 中央设备连接对象（手机作为 Central，连接外设后由 {@link com.xing.xblelibrary.server.XBleServer} 创建）。
 * <p>
 * 发送模型（全部在主线程 Handler 上串行执行，避免多线程改队列）：
 * <ul>
 *   <li>控制队列：MTU / RSSI，优先级最高</li>
 *   <li>Notify 队列：开启/关闭通知，独立推进，避免与普通写互相插队混乱</li>
 *   <li>写队列：普通 {@link #sendData}（包间隔 {@code mSendDataInterval}）与实时 {@link #sendDataNow}（回调后立即发下一条）</li>
 * </ul>
 * 读写/Notify/控制指令进入 busy 后，等待系统回调或超时再发下一条。
 * WRITE_NO_RESPONSE 同样等待 {@code onCharacteristicWrite}，以保证 Android GATT 串行可靠。
 * <p>
 * 调用方须在运行时持有 {@code BLUETOOTH_CONNECT} 等权限；库内不做运行时校验。
 */
@SuppressLint("MissingPermission")
public final class BleDevice {

    private static final String TAG = BleDevice.class.getSimpleName();

    /**
     * 读写/Notify/控制等待系统回调的默认超时（ms）
     */
    private static final int WRITE_TIMEOUT_DEFAULT = 500;

    private static final int MSG_PROCESS = 1;
    private static final int MSG_TIMEOUT = 2;

    /**
     * 普通写队列发送间隔（ms）
     */
    private int mSendDataInterval = 200;
    /**
     * Notify 连续设置间隔（ms），最小值 20
     */
    private int mSendNotifyInterval = 50;
    /**
     * 当前指令等待系统回调的超时时间（ms）
     */
    private int mWriteTimeOut = WRITE_TIMEOUT_DEFAULT;

    private BluetoothGatt mBluetoothGatt;
    /**
     * 是否仍处于可用连接状态
     */
    private volatile boolean connectSuccess;
    private final String mac;
    private String mName;
    private int mRssi;

    /**
     * 普通写队列。入队规则配合 {@link #pollWrite}：
     * {@code isTop=false} → addFirst，FIFO；{@code isTop=true} → addLast，后入先出（插队）。
     */
    private final LinkedList<SendDataBean> mWriteQueue = new LinkedList<>();
    /**
     * 实时写队列，默认优先于普通写队列
     */
    private final LinkedList<SendDataBean> mWriteNowQueue = new LinkedList<>();
    /**
     * Notify 开启/关闭队列（FIFO：addLast + pollFirst）
     */
    private final LinkedList<SendDataBean> mNotifyQueue = new LinkedList<>();
    /**
     * 控制指令队列（MTU、RSSI），优先级最高
     */
    private final LinkedList<SendDataBean> mCtrlQueue = new LinkedList<>();

    /**
     * true：优先消费实时写队列；false：优先普通写队列
     */
    private boolean mWriteNowPriority = true;
    private boolean mResend;
    private int mResendNumber = 3;

    /**
     * true 表示已向系统提交需等待回调的操作（写/读/Notify/MTU/RSSI），
     * 在此期间不再下发下一条指令。
     */
    private boolean mBusy;

    /**
     * 最近一次从写队列取出的包是否来自实时队列（{@link #sendDataNow}）。
     * 用于回调后决定间隔：普通队列用 {@link #mSendDataInterval}，实时队列为 0。
     */
    private boolean mLastWriteFromNow;

    /**
     * 当前 busy 操作完成后，再调度 {@link #processNext()} 前的延迟（ms）。
     */
    private long mPendingProcessDelay;

    private OnBleSendResultListener mOnBleSendResultListener;
    private onBleDisConnectedListener mOnDisConnectedListener;
    private OnBleNotifyDataListener mOnNotifyDataListener;
    private OnBleRssiListener mOnBleRssiListener;
    private OnBleMtuListener mOnBleMtuListener;
    private OnBleCharacteristicListener mOnCharacteristicListener;

    /**
     * @param bluetoothGatt 已发现服务成功的 Gatt
     * @param mac           设备地址
     */
    public BleDevice(BluetoothGatt bluetoothGatt, String mac) {
        XBleL.i("连接成功:" + mac);
        mBluetoothGatt = bluetoothGatt;
        this.mac = mac;
        this.mName = bluetoothGatt.getDevice().getName();
        connectSuccess = true;
    }

    /**
     * 判断当前连接是否包含指定服务 UUID
     */
    public boolean containsServiceUuid(UUID serviceUuid) {
        if (mBluetoothGatt == null || serviceUuid == null) {
            return false;
        }
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        if (services == null) {
            return false;
        }
        for (BluetoothGattService service : services) {
            if (service.getUuid().toString().equalsIgnoreCase(serviceUuid.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取已发现的 GATT 服务列表；断开后返回空列表（不会 NPE）
     */
    public List<BluetoothGattService> getBluetoothGattServiceList() {
        if (mBluetoothGatt == null) {
            return new ArrayList<>();
        }
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        return services == null ? new ArrayList<>() : services;
    }

    /**
     * 获取某个服务下的特征列表
     */
    public List<BluetoothGattCharacteristic> getBluetoothGattCharacteristicList(BluetoothGattService bleGattService) {
        if (bleGattService == null) {
            return new ArrayList<>();
        }
        List<BluetoothGattCharacteristic> characteristics = bleGattService.getCharacteristics();
        return characteristics == null ? new ArrayList<>() : characteristics;
    }

    /**
     * 读取 RSSI（进入控制队列，优先级高于普通写）
     */
    public void readRssi() {
        enqueueCtrl(new SendDataBean(null, null, XBleStaticConfig.RSSI_DATA, null));
    }

    public boolean isConnectSuccess() {
        return connectSuccess;
    }

    /**
     * 开启多个 Notify（同一服务下可传多个特征 UUID）
     */
    public void setNotify(UUID uuidService, UUID... uuidNotify) {
        if (!isAlive() || uuidNotify == null) {
            return;
        }
        for (UUID uuid : uuidNotify) {
            enqueueNotify(new SendDataBean(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    uuid,
                    XBleStaticConfig.NOTICE_DATA,
                    uuidService));
        }
    }

    /**
     * 开启当前设备所有支持 Notify 的特征
     */
    public void setNotifyAll() {
        if (!isAlive()) {
            return;
        }
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        if (services == null) {
            return;
        }
        for (BluetoothGattService service : services) {
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            if (characteristics == null) {
                continue;
            }
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                int properties = characteristic.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    enqueueNotify(new SendDataBean(
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                            characteristic.getUuid(),
                            XBleStaticConfig.NOTICE_DATA,
                            service.getUuid()));
                }
            }
        }
    }

    /**
     * 关闭指定 Notify
     */
    public void setCloseNotify(UUID uuidService, UUID uuidNotify) {
        if (!isAlive()) {
            return;
        }
        enqueueNotify(new SendDataBean(
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                uuidNotify,
                XBleStaticConfig.NOTICE_DATA,
                uuidService));
    }

    /**
     * 断开连接
     *
     * @param notice true：调用 {@link BluetoothGatt#disconnect()}，系统仍可能回调连接状态；
     *               false：直接 {@link BluetoothGatt#close()}，系统不再回调连接状态变化
     */
    public final void disconnect(boolean notice) {
        if (mBluetoothGatt != null) {
            synchronized (this) {
                if (mBluetoothGatt != null) {
                    BluetoothGatt gatt = mBluetoothGatt;
                    if (!notice) {
                        try {
                            gatt.close();
                        } catch (Exception e) {
                            XBleL.e(TAG, "close gatt 异常:" + e.getMessage());
                        }
                        mBluetoothGatt = null;
                    } else {
                        try {
                            gatt.disconnect();
                        } catch (Exception e) {
                            XBleL.e(TAG, "disconnect gatt 异常:" + e.getMessage());
                        }
                    }
                    onDisConnected();
                }
            }
        } else {
            onDisConnected();
        }
        XBleL.e(TAG, "断开连接:" + mac);
    }

    /**
     * 断开连接（默认需要系统回调）
     */
    public final void disconnect() {
        disconnect(true);
    }

    /**
     * 断开后的统一清理：连接标记、四类队列、Handler、业务监听。
     * 可被系统断开路径多次调用，内部做了防重入。
     */
    @CallSuper
    public void onDisConnected() {
        if (!connectSuccess && mBluetoothGatt == null
                && mWriteQueue.isEmpty() && mWriteNowQueue.isEmpty()
                && mNotifyQueue.isEmpty() && mCtrlQueue.isEmpty()) {
            return;
        }
        XBleL.i("断开连接,清空发送队列");
        connectSuccess = false;
        mBusy = false;
        mPendingProcessDelay = 0;
        mLastWriteFromNow = false;
        mWriteQueue.clear();
        mWriteNowQueue.clear();
        mNotifyQueue.clear();
        mCtrlQueue.clear();
        mHandler.removeCallbacksAndMessages(null);

        onBleDisConnectedListener disConnectedListener = mOnDisConnectedListener;
        clearListeners();
        if (disConnectedListener != null) {
            disConnectedListener.onDisConnected();
        }
    }

    /**
     * 清空业务监听，避免断开后仍持有 Activity 等对象
     */
    public void clearListeners() {
        mOnDisConnectedListener = null;
        mOnNotifyDataListener = null;
        mOnBleSendResultListener = null;
        mOnBleRssiListener = null;
        mOnBleMtuListener = null;
        mOnCharacteristicListener = null;
    }

    /**
     * GATT Notify 数据回调入口（由 {@link com.xing.xblelibrary.server.XBleServer} 转发）
     */
    public final void notifyData(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        if (mOnCharacteristicListener != null) {
            mOnCharacteristicListener.onCharacteristicChanged(characteristic);
        }
        if (mOnNotifyDataListener != null) {
            mOnNotifyDataListener.onNotifyData(characteristic, characteristic.getValue());
        }
    }

    /**
     * 读取 RSSI 成功回调：解除 busy 并继续调度
     */
    public final void setRssi(int rssi) {
        this.mRssi = rssi;
        mHandler.post(() -> {
            finishBusyAndProcess();
            if (mOnBleRssiListener != null) {
                mOnBleRssiListener.OnRssi(rssi);
            }
        });
    }

    /**
     * MTU 协商结果回调。
     * 对外回调值为 {@code mtu - 3}（减去 ATT 头），表示单包可写有效载荷大约字节数。
     *
     * @param mtu 系统返回的 MTU（含 ATT 头，通常 23~517）
     */
    public void OnMtu(int mtu) {
        mHandler.post(() -> {
            finishBusyAndProcess();
            if (mOnBleMtuListener != null) {
                mOnBleMtuListener.OnMtu(mtu - 3);
            }
        });
    }

    /**
     * 连接参数更新回调（系统/厂商扩展），当前仅打日志
     */
    public void getConnectionUpdated(int interval, int latency, int timeout) {
        XBleL.i("interval=" + interval + "  latency=" + latency + "   timeout=" + timeout);
    }

    /**
     * 请求协商 MTU（进入控制队列）。仅当 mtu &gt; 20 时入队。
     *
     * @param mtu 期望 MTU（含 ATT 头），建议 23~517
     * @return 是否已接受请求（入队）
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setMtu(int mtu) {
        if (!isAlive() || mtu <= 20) {
            return false;
        }
        byte[] byteMtu = new byte[]{(byte) (mtu >> 8), (byte) mtu};
        SendDataBean bean = new SendDataBean(byteMtu, null, XBleStaticConfig.MTU_DATA, null);
        bean.setTop(true);
        enqueueCtrl(bean);
        return true;
    }

    /**
     * 设置首选物理层（立即执行，不走发送队列）
     */
    @SuppressLint({"NewApi", "MissingPermission"})
    public boolean setPreferredPhy(int txPhy, int rxPhy) {
        if (!isAlive()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBluetoothGatt.setPreferredPhy(txPhy, rxPhy, BluetoothDevice.PHY_OPTION_NO_PREFERRED);
            return true;
        }
        return false;
    }

    /**
     * 请求连接优先级（立即执行，不走发送队列）
     *
     * @see BluetoothGatt#CONNECTION_PRIORITY_BALANCED
     * @see BluetoothGatt#CONNECTION_PRIORITY_HIGH
     * @see BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setConnectPriority(int connectionPriority) {
        return isAlive() && mBluetoothGatt.requestConnectionPriority(connectionPriority);
    }

    /**
     * 特征读取成功回调：解除 busy 并继续调度
     */
    @CallSuper
    public void readData(BluetoothGattCharacteristic characteristic) {
        mHandler.post(() -> {
            finishBusyAndProcess();
            if (mOnCharacteristicListener != null) {
                mOnCharacteristicListener.onCharacteristicReadOK(characteristic);
            }
        });
    }

    /**
     * 特征写入成功回调（含 WRITE_NO_RESPONSE 的本地写完成回调）。
     * 普通队列会按 {@link #mSendDataInterval} 节流后再发下一条；实时队列立即继续。
     */
    @CallSuper
    public void writeData(BluetoothGattCharacteristic characteristic) {
        mHandler.post(() -> {
            if (mOnCharacteristicListener != null) {
                mOnCharacteristicListener.onCharacteristicWriteOK(characteristic);
            }
            finishBusyAndProcess();
        });
    }

    /**
     * Descriptor 写入成功（Notify 开关成功）回调
     */
    @CallSuper
    public void descriptorWriteOk(BluetoothGattDescriptor descriptor) {
        mHandler.post(() -> {
            if (descriptor != null) {
                UUID uuid = descriptor.getCharacteristic().getUuid();
                XBleL.i(TAG, "notify成功:" + uuid + " remain=" + mNotifyQueue.size());
                if (mOnCharacteristicListener != null) {
                    mOnCharacteristicListener.onDescriptorWriteOK(descriptor);
                }
            }
            finishBusyAndProcess();
        });
    }

    /**
     * 普通队列发送（限速）。
     * 每条写成功并收到 {@code onCharacteristicWrite} 后，再延迟 {@link #mSendDataInterval}
     * 才发下一条，避免外设处理不过来。{@link SendDataBean#isTop()} 为 true 时插队。
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
            if (isCtrlType(bean.getType())) {
                enqueueCtrlLocked(bean);
            } else if (bean.getType() == XBleStaticConfig.NOTICE_DATA) {
                enqueueNotifyLocked(bean);
            } else {
                enqueueWriteLocked(bean, false);
            }
            scheduleProcess(0);
        });
    }

    /**
     * 实时队列发送（不额外限速）。
     * 仍会等待 {@code onCharacteristicWrite} 以保证 GATT 串行，但回调后立即发下一条（间隔 0）。
     * 默认优先于 {@link #sendData} 普通队列。
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
            if (isCtrlType(bean.getType())) {
                enqueueCtrlLocked(bean);
            } else if (bean.getType() == XBleStaticConfig.NOTICE_DATA) {
                enqueueNotifyLocked(bean);
            } else {
                enqueueWriteLocked(bean, true);
            }
            // 实时包应尽快调度；若正处在普通队列的间隔等待中，用 0 打断延迟
            scheduleProcess(0);
        });
    }

    // -------------------- 队列入队（仅主线程） --------------------

    private void enqueueCtrl(SendDataBean bean) {
        mHandler.post(() -> {
            if (!isAlive()) {
                return;
            }
            enqueueCtrlLocked(bean);
            scheduleProcess(0);
        });
    }

    private void enqueueNotify(SendDataBean bean) {
        mHandler.post(() -> {
            if (!isAlive()) {
                return;
            }
            enqueueNotifyLocked(bean);
            scheduleProcess(0);
        });
    }

    private void enqueueCtrlLocked(SendDataBean bean) {
        if (bean.isTop()) {
            mCtrlQueue.addLast(bean);
        } else {
            mCtrlQueue.addFirst(bean);
        }
    }

    private void enqueueNotifyLocked(SendDataBean bean) {
        // Notify 固定 FIFO，保证开启顺序稳定
        mNotifyQueue.addLast(bean);
    }

    private void enqueueWriteLocked(SendDataBean bean, boolean nowQueue) {
        LinkedList<SendDataBean> queue = nowQueue ? mWriteNowQueue : mWriteQueue;
        if (bean.isTop()) {
            queue.addLast(bean);
        } else {
            queue.addFirst(bean);
        }
    }

    private static boolean isCtrlType(int type) {
        return type == XBleStaticConfig.MTU_DATA || type == XBleStaticConfig.RSSI_DATA;
    }

    // -------------------- 调度与发送 --------------------

    /**
     * 主线程串行调度器：统一处理超时与下一条指令选择。
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (!isAlive()) {
                return;
            }
            if (msg.what == MSG_TIMEOUT) {
                if (mBusy) {
                    XBleL.i(TAG, "发送等待超时,重置 busy 并继续调度");
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
            // 立即调度：取消可能正在等待的普通队列间隔
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

    /**
     * 选择下一条指令：控制 &gt; Notify &gt; 写（实时/普通按优先级）。
     */
    private void processNext() {
        if (!isAlive() || mBusy) {
            return;
        }

        SendDataBean bean = pollCtrl();
        QueueKind kind = QueueKind.CTRL;
        if (bean == null) {
            bean = pollNotify();
            kind = QueueKind.NOTIFY;
        }
        if (bean == null) {
            bean = pollWrite();
            kind = QueueKind.WRITE;
        }
        if (bean == null) {
            return;
        }

        SendResult result = sendCmd(bean);
        if (!result.accepted) {
            mHandler.removeMessages(MSG_TIMEOUT);
            handleSendFail(bean, kind);
            scheduleProcess(resolveProcessDelay(kind));
            return;
        }

        if (result.waitCallback) {
            mBusy = true;
            mPendingProcessDelay = resolveProcessDelay(kind);
            mHandler.removeMessages(MSG_TIMEOUT);
            mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, mWriteTimeOut);
        } else {
            scheduleProcess(resolveProcessDelay(kind));
        }
    }

    /**
     * 计算发完当前包后，距离下一条的间隔。
     * <ul>
     *   <li>普通写 {@link #sendData}：{@link #mSendDataInterval}（给外设喘息）</li>
     *   <li>实时写 {@link #sendDataNow}：0</li>
     *   <li>Notify：{@link #mSendNotifyInterval}</li>
     *   <li>控制：0</li>
     * </ul>
     */
    private long resolveProcessDelay(QueueKind kind) {
        switch (kind) {
            case WRITE:
                return mLastWriteFromNow ? 0 : mSendDataInterval;
            case NOTIFY:
                return mSendNotifyInterval;
            case CTRL:
            default:
                return 0;
        }
    }

    @Nullable
    private SendDataBean pollCtrl() {
        return mCtrlQueue.isEmpty() ? null : mCtrlQueue.pollLast();
    }

    @Nullable
    private SendDataBean pollNotify() {
        return mNotifyQueue.isEmpty() ? null : mNotifyQueue.pollFirst();
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

    private enum QueueKind {
        CTRL, NOTIFY, WRITE
    }

    /**
     * 单次发送结果
     */
    private static final class SendResult {
        final boolean accepted;
        /**
         * true：需要等待 GATT 回调或超时后才能发下一条
         */
        final boolean waitCallback;

        SendResult(boolean accepted, boolean waitCallback) {
            this.accepted = accepted;
            this.waitCallback = waitCallback;
        }

        static SendResult fail() {
            return new SendResult(false, false);
        }

        static SendResult okWait() {
            return new SendResult(true, true);
        }

        static SendResult okContinue() {
            return new SendResult(true, false);
        }
    }

    private void handleSendFail(SendDataBean sendDataBean, QueueKind kind) {
        if (!mResend || sendDataBean == null) {
            return;
        }
        if (sendDataBean.getResendNumber() < mResendNumber) {
            sendDataBean.addResendNumber();
            sendDataBean.setTop(true);
            switch (kind) {
                case CTRL:
                    mCtrlQueue.addFirst(sendDataBean);
                    break;
                case NOTIFY:
                    mNotifyQueue.addFirst(sendDataBean);
                    break;
                case WRITE:
                default:
                    // 失败重发优先回实时队列，尽快补发
                    mWriteNowQueue.addFirst(sendDataBean);
                    break;
            }
        } else if (mOnBleSendResultListener != null) {
            mOnBleSendResultListener.onWriteAndReSendFail(sendDataBean, mResendNumber);
        }
    }

    /**
     * 真正执行 GATT 读写/通知/控制。
     * 仅应在主线程 Handler 中调用。
     */
    private SendResult sendCmd(SendDataBean sendDataBean) {
        if (sendDataBean == null) {
            return SendResult.okContinue();
        }
        try {
            byte[] hex = sendDataBean.getHex();
            UUID uuid = sendDataBean.getUuid();
            int type = sendDataBean.getType();
            UUID uuidService = sendDataBean.getUuidService();
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt == null) {
                return SendResult.fail();
            }

            // ---------- 控制类：不依赖特征 UUID ----------
            if (type == XBleStaticConfig.RSSI_DATA) {
                boolean ok = gatt.readRemoteRssi();
                mWriteTimeOut = WRITE_TIMEOUT_DEFAULT;
                return ok ? SendResult.okWait() : SendResult.fail();
            }
            if (type == XBleStaticConfig.MTU_DATA) {
                if (hex != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    int mtu = (((hex[0] & 0xFF) << 8) | (hex[1] & 0xFF));
                    boolean ok = gatt.requestMtu(mtu);
                    mWriteTimeOut = WRITE_TIMEOUT_DEFAULT;
                    return ok ? SendResult.okWait() : SendResult.fail();
                }
                return SendResult.fail();
            }

            BluetoothGattService gattService = MyBleDeviceUtils.getService(gatt, uuidService);
            if (gattService == null || uuid == null) {
                if (type == XBleStaticConfig.NOTICE_DATA) {
                    // 无效 Notify：跳过并继续
                    return SendResult.okContinue();
                }
                return SendResult.fail();
            }
            BluetoothGattCharacteristic characteristic = MyBleDeviceUtils.getServiceWrite(gattService, uuid);
            if (characteristic == null) {
                if (type == XBleStaticConfig.NOTICE_DATA) {
                    return SendResult.okContinue();
                }
                return SendResult.fail();
            }
            if (hex != null) {
                characteristic.setValue(hex);
            }

            int properties = characteristic.getProperties();
            boolean noResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
            if (noResponse) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            mWriteTimeOut = WRITE_TIMEOUT_DEFAULT;

            switch (type) {
                case XBleStaticConfig.READ_DATA: {
                    boolean ok = gatt.readCharacteristic(characteristic);
                    if (mOnBleSendResultListener != null) {
                        mOnBleSendResultListener.onReadResult(uuid, ok);
                    }
                    XBleL.i(TAG, "READ UUID=" + uuid + " || " + ok);
                    return ok ? SendResult.okWait() : SendResult.fail();
                }
                case XBleStaticConfig.WRITE_DATA: {
                    boolean ok = gatt.writeCharacteristic(characteristic);
                    if (mOnBleSendResultListener != null) {
                        mOnBleSendResultListener.onWriteResult(uuid, ok);
                    }
                    XBleL.i(TAG, "WRITE UUID=" + uuid + " noResponse=" + noResponse + " || " + ok);
                    // 有/无响应写均等待 onCharacteristicWrite，再发下一条
                    return ok ? SendResult.okWait() : SendResult.fail();
                }
                case XBleStaticConfig.NOTICE_DATA: {
                    if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                        return SendResult.okContinue();
                    }
                    gatt.setCharacteristicNotification(characteristic, true);
                    BluetoothGattDescriptor descriptor =
                            characteristic.getDescriptor(XBleStaticConfig.UUID_NOTIFY_DESCRIPTOR);
                    if (descriptor == null) {
                        return SendResult.okContinue();
                    }
                    descriptor.setValue(hex);
                    boolean ok = gatt.writeDescriptor(descriptor);
                    if (mOnBleSendResultListener != null) {
                        mOnBleSendResultListener.onNotifyResult(uuid, ok);
                    }
                    XBleL.i(TAG, "NOTIFY UUID=" + uuid + " || " + ok);
                    return ok ? SendResult.okWait() : SendResult.fail();
                }
                default:
                    return SendResult.fail();
            }
        } catch (Exception e) {
            XBleL.e(TAG, "读/写/设置通知异常:" + e);
            e.printStackTrace();
            return SendResult.fail();
        }
    }

    private boolean isAlive() {
        return connectSuccess && mBluetoothGatt != null;
    }

    // -------------------- 监听与配置 --------------------

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

    public void setOnDisConnectedListener(onBleDisConnectedListener onDisConnectedListener) {
        mOnDisConnectedListener = onDisConnectedListener;
    }

    public void setOnNotifyDataListener(OnBleNotifyDataListener onNotifyDataListener) {
        mOnNotifyDataListener = onNotifyDataListener;
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
     * 普通写队列包间隔，默认 200ms。
     * 仅作用于 {@link #sendData}：上一条写回调成功后，再等该间隔才发下一条。
     * {@link #sendDataNow} 不受此间隔限制（回调后立即继续）。
     */
    public void setSendDataInterval(int interval) {
        if (interval < 0) {
            interval = 0;
        }
        mSendDataInterval = interval;
    }

    /**
     * Notify 连续设置间隔，默认 50ms，最小 20ms。
     */
    public void setSendNotifyInterval(int sendNotifyInterval) {
        if (sendNotifyInterval < 20) {
            sendNotifyInterval = 20;
        }
        mSendNotifyInterval = sendNotifyInterval;
    }

    /**
     * 设置实时写队列与普通写队列的相对优先级。
     *
     * @param writeNowPriority true 优先实时队列（默认）；false 优先普通队列
     */
    public void setLinkedListNowPriority(boolean writeNowPriority) {
        mWriteNowPriority = writeNowPriority;
    }

    /**
     * @deprecated 请使用 {@link #setResend(boolean, int)}
     */
    @Deprecated
    public void setResend(boolean resend) {
        setResend(resend, 3);
    }

    /**
     * 发送失败是否重试。
     *
     * @param resend       是否开启重发，默认 false
     * @param resendNumber 最大重发次数（不含首次），默认 3
     */
    public void setResend(boolean resend, int resendNumber) {
        mResend = resend;
        mResendNumber = Math.max(0, resendNumber);
    }
}

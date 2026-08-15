package com.xing.xblelibrary;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.xing.xblelibrary.bean.AdBleBroadcastBean;
import com.xing.xblelibrary.bean.BleBroadcastBean;
import com.xing.xblelibrary.config.XBleConfig;
import com.xing.xblelibrary.device.AdBleDevice;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.listener.BleConnectListenerIm;
import com.xing.xblelibrary.listener.BleScanFilterListenerIm;
import com.xing.xblelibrary.listener.OnBleAdvertiserConnectListener;
import com.xing.xblelibrary.listener.OnBleConnectListener;
import com.xing.xblelibrary.listener.OnBleScanFilterListener;
import com.xing.xblelibrary.listener.OnBleStatusListener;
import com.xing.xblelibrary.server.XBleServer;
import com.xing.xblelibrary.utils.XBleL;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BLE 对外门面单例。
 * <p>
 * 职责：
 * <ul>
 *   <li>绑定并持有后台 {@link XBleServer}</li>
 *   <li>向业务提供扫描、连接、广播、前台服务等统一 API</li>
 *   <li>在服务未就绪时对多数操作排队，就绪后自动执行</li>
 * </ul>
 * 典型用法：先 {@link #getXBleConfig()} 配参数，再 {@link #init(Context, onInitListener)}，
 * 在 {@link onInitListener#onInitSuccess()} 或 {@link #isReady()} 为 true 后进行扫描/连接。
 * <p>
 * 连接设备后的读写请通过 {@link #getBleDevice(String)} 获取 {@link BleDevice} 操作。
 *
 * @author xing
 * @since 2021/7/17
 */
public class XBleManager {

    /** Application Context，由 {@link #init} 赋值 */
    private Context mContext;
    /** 已绑定的蓝牙后台服务；null 表示尚未就绪 */
    protected XBleServer mXBleServer;
    /** start/bind Service 使用的 Intent */
    private Intent bindIntent;
    /** 初始化结果回调 */
    private onInitListener mOnInitListener;
    /** 是否已发起 bindService（防止重复绑定） */
    private boolean mServiceBinding;
    /**
     * 服务未就绪时暂存的待执行任务（扫描、连接等）。
     * 仅在已 {@link #init} 且 {@link #mXBleServer} 仍为 null 时入队。
     */
    private final List<Runnable> mPendingActions = new ArrayList<>();
    /**
     * 服务未就绪时暂存的蓝牙开关监听（弱引用），
     * 就绪后通过 {@link #applyPendingListeners()} 设置到 Server。
     */
    private WeakReference<OnBleStatusListener> mPendingStatusListenerRef;
    /** 外围广播相关监听（弱引用） */
    private WeakReference<OnBleAdvertiserConnectListener> mAdvertiserListenerRef;

    private static final XBleConfig mXBleConfig = XBleConfig.getInstance();
    private static volatile XBleManager sXBleManager;

    /**
     * 全局 BLE 参数配置（连接数、是否自动连接系统设备等）。
     * 可在 {@link #init} 之前调用；服务就绪时会自动应用到 {@link XBleServer}。
     */
    public static XBleConfig getXBleConfig() {
        return mXBleConfig;
    }

    /**
     * 获取单例。{@link #clear()} 之后再次调用会创建新实例。
     */
    public static XBleManager getInstance() {
        if (sXBleManager == null) {
            synchronized (XBleManager.class) {
                if (sXBleManager == null) {
                    sXBleManager = new XBleManager();
                }
            }
        }
        return sXBleManager;
    }

    private XBleManager() {
    }

    /**
     * 初始化并绑定 {@link XBleServer}（无回调）。
     *
     * @see #init(Context, onInitListener)
     */
    public void init(Context context) {
        init(context, null);
    }

    /**
     * 初始化并绑定 {@link XBleServer}（幂等）。
     * <p>
     * 始终使用 {@link Context#getApplicationContext()}，避免 Activity 泄漏。
     * 若服务已就绪，会立即 {@link onInitListener#onInitSuccess()} 并刷新配置。
     *
     * @param context  任意 Context（内部转 Application）
     * @param listener 初始化结果；可为 null
     */
    public synchronized void init(Context context, onInitListener listener) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为空");
        }
        mContext = context.getApplicationContext();
        mOnInitListener = listener;

        if (mXBleServer != null) {
            applyConfigToServer();
            if (mOnInitListener != null) {
                mOnInitListener.onInitSuccess();
            }
            return;
        }
        startService();
    }

    /**
     * {@link XBleServer} 是否已绑定成功（可执行扫描、连接等）。
     */
    public boolean isReady() {
        return mXBleServer != null;
    }

    /**
     * 是否已调用过 {@link #init}（Context 已持有）。
     * 注意：已初始化不等于已就绪，请结合 {@link #isReady()}。
     */
    public boolean isInitialized() {
        return mContext != null;
    }

    /**
     * 释放资源并清空单例。
     * <p>
     * 顺序：清空排队任务 → 停扫/断连/停广播 → {@link XBleServer#finish()} →
     * 清空连接/扫描观察者 → 解绑 Service → 置空单例。
     * 之后需重新 {@link #init} 才能继续使用。
     */
    public synchronized void clear() {
        clearPendingActions();
        mPendingStatusListenerRef = null;
        mAdvertiserListenerRef = null;
        mOnInitListener = null;

        if (mXBleServer != null) {
            try {
                mXBleServer.stopScan();
                mXBleServer.disconnectAll();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mXBleServer.stopAdvertiseData();
                }
            } catch (Exception e) {
                XBleL.e("clear 释放业务资源异常:" + e.getMessage());
            }
            try {
                mXBleServer.finish();
            } catch (Exception e) {
                XBleL.e("clear finish 异常:" + e.getMessage());
            }
            mXBleServer = null;
        }

        BleConnectListenerIm.getInstance().removeListenerAll();
        BleScanFilterListenerIm.getInstance().removeListenerAll();

        unbindService();
        mContext = null;
        sXBleManager = null;
    }

    /**
     * 设置或更换初始化回调。
     * 若服务此时已就绪，会立刻回调 {@link onInitListener#onInitSuccess()}。
     */
    public void setOnInitListener(onInitListener onInitListener) {
        mOnInitListener = onInitListener;
        if (mXBleServer != null && mOnInitListener != null) {
            mOnInitListener.onInitSuccess();
        }
    }

    /**
     * {@link XBleServer} 绑定结果回调。
     */
    public interface onInitListener {
        /** 服务绑定成功，可安全进行扫描/连接等操作 */
        void onInitSuccess();

        /** 绑定失败或服务异常断开 */
        default void onInitFailure() {
        }
    }

    /**
     * 启动并绑定 {@link XBleServer}；已绑定中则不会重复 bind。
     */
    private void startService() {
        if (mContext == null) {
            return;
        }
        try {
            if (bindIntent == null) {
                bindIntent = new Intent(mContext, XBleServer.class);
                mContext.startService(bindIntent);
            }
            if (!mServiceBinding) {
                mServiceBinding = true;
                mContext.bindService(bindIntent, mFhrSCon, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception e) {
            mServiceBinding = false;
            XBleL.e("绑定 XBleServer 失败:" + e.getMessage());
            e.printStackTrace();
            onServiceErr();
        }
    }

    /** 解绑 Service，并重置绑定相关状态 */
    private void unbindService() {
        try {
            if (mContext != null && mServiceBinding) {
                mContext.unbindService(mFhrSCon);
            }
        } catch (Exception e) {
            XBleL.e("解绑 XBleServer 失败:" + e.getMessage());
        } finally {
            mServiceBinding = false;
            bindIntent = null;
        }
    }

    /** Service 连接回调：成功则应用配置、补设监听并排空队列 */
    private final ServiceConnection mFhrSCon = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mXBleServer = ((XBleServer.BluetoothBinder) service).getService();
            onServiceSuccess();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mXBleServer = null;
            mServiceBinding = false;
            onServiceErr();
        }
    };

    /**
     * 执行需依赖 {@link XBleServer} 的操作。
     * <ul>
     *   <li>未 {@link #init}：抛出 {@link IllegalStateException}</li>
     *   <li>已 init 且服务就绪：立即执行</li>
     *   <li>已 init 但服务未就绪：加入 {@link #mPendingActions}，就绪后执行</li>
     * </ul>
     */
    private void runOrQueue(Runnable action) {
        if (mContext == null) {
            throw new IllegalStateException("请先调用 init() 初始化.");
        }
        synchronized (mPendingActions) {
            if (mXBleServer != null) {
                action.run();
            } else {
                mPendingActions.add(action);
            }
        }
    }

    /** 服务就绪后依次执行排队任务 */
    private void drainPendingActions() {
        List<Runnable> actions;
        synchronized (mPendingActions) {
            actions = new ArrayList<>(mPendingActions);
            mPendingActions.clear();
        }
        for (Runnable action : actions) {
            if (mXBleServer != null) {
                try {
                    action.run();
                } catch (Exception e) {
                    XBleL.e("执行排队任务失败:" + e.getMessage());
                }
            }
        }
    }

    private void clearPendingActions() {
        synchronized (mPendingActions) {
            mPendingActions.clear();
        }
    }

    /**
     * 将 {@link XBleConfig} 同步到已就绪的 Server
     * （最大连接数、系统连接监听、自动连接系统设备）。
     */
    private void applyConfigToServer() {
        if (mXBleServer == null) {
            return;
        }
        XBleConfig config = XBleConfig.getInstance();
        mXBleServer.setConnectMax(config.getConnectMax());
        mXBleServer.setAutoMonitorSystemConnectBle(config.isAutoMonitorSystemConnectBle());
        if (config.isAutoConnectSystemBle()) {
            mXBleServer.autoConnectSystemBle();
        }
    }

    /** 把 init 期间暂存的状态/广播监听设置到 Server */
    private void applyPendingListeners() {
        if (mXBleServer == null) {
            return;
        }
        if (mPendingStatusListenerRef != null) {
            mXBleServer.setOnBleStatusListener(mPendingStatusListenerRef.get());
            mPendingStatusListenerRef = null;
        }
        OnBleAdvertiserConnectListener advertiserListener = getAdvertiserListener();
        if (advertiserListener != null) {
            mXBleServer.setOnBleAdvertiserConnectListener(advertiserListener);
        }
    }

    /** 服务异常或断开：清空排队任务并回调失败 */
    private void onServiceErr() {
        clearPendingActions();
        if (mOnInitListener != null) {
            mOnInitListener.onInitFailure();
        }
    }

    /** 服务绑定成功：应用配置 → 补监听 → 排空队列 → 回调成功 */
    private void onServiceSuccess() {
        applyConfigToServer();
        applyPendingListeners();
        drainPendingActions();
        if (mOnInitListener != null) {
            mOnInitListener.onInitSuccess();
        }
    }

    // -------------------- 设备查询 --------------------

    /**
     * 获取当前所有已连接的中央侧 {@link BleDevice}。
     * 服务未就绪时返回空列表（不抛异常）。
     */
    public List<BleDevice> getBleDeviceAll() {
        if (mXBleServer != null) {
            return mXBleServer.getBleDeviceAll();
        }
        return new ArrayList<>();
    }

    /**
     * 按 MAC 获取中央侧已连接设备；未连接或服务未就绪返回 null。
     * 请在 {@link OnBleConnectListener#onServicesDiscovered(String)} 之后再取用。
     */
    @Nullable
    public BleDevice getBleDevice(String mac) {
        return mXBleServer == null ? null : mXBleServer.getBleDevice(mac);
    }

    /**
     * 按 MAC 获取外围侧已连接的中央设备对象 {@link AdBleDevice}。
     */
    @Nullable
    public AdBleDevice getAdBleDevice(String mac) {
        return mXBleServer == null ? null : mXBleServer.getAdBleDevice(mac);
    }

    /**
     * 获取系统 {@link BluetoothAdapter}；服务未就绪返回 null。
     */
    @Nullable
    public BluetoothAdapter getBluetoothAdapter() {
        return mXBleServer == null ? null : mXBleServer.getBluetoothAdapter();
    }

    // -------------------- 扫描 / 连接 --------------------

    /**
     * 开始 BLE 扫描。
     *
     * @param timeOut  超时毫秒；0 表示持续扫描（库内会定期重启扫描）
     * @param scanUUID 按 Service UUID 过滤；不传或空表示不过滤
     */
    public void startScan(long timeOut, UUID... scanUUID) {
        final UUID[] uuids = scanUUID;
        runOrQueue(() -> mXBleServer.scanLeDevice(timeOut, uuids));
    }

    /** 停止扫描 */
    public void stopScan() {
        runOrQueue(() -> mXBleServer.stopScan());
    }

    /**
     * 连接扫描到的设备（使用广播对象中的 MAC）。
     * 建议连接前先 {@link #stopScan()}。
     */
    public void connectDevice(BleBroadcastBean bleValueBean) {
        connectDevice(bleValueBean.getMac());
    }

    /**
     * 按 MAC 地址连接设备。
     * 连接过程与结果通过 {@link #addBleConnectListener(OnBleConnectListener)} 回调。
     */
    public void connectDevice(String mAddress) {
        runOrQueue(() -> mXBleServer.connectDevice(mAddress));
    }

    /** 断开所有中央侧连接 */
    public void disconnectAll() {
        runOrQueue(() -> mXBleServer.disconnectAll());
    }

    /**
     * 设置连接超时（发现服务超时等，单位 ms）。
     * 超时断开错误码见 {@link com.xing.xblelibrary.config.XBleStaticConfig}。
     */
    public void setConnectBleTimeout(long connectTimeout) {
        runOrQueue(() -> mXBleServer.setConnectBleTimeout(connectTimeout));
    }

    /**
     * 设置最大连接数（写入 {@link XBleConfig}；服务就绪时立即同步）。
     *
     * @param connectMax 1~7，超出范围由 Config 钳制
     */
    public void setConnectMax(int connectMax) {
        XBleConfig.getInstance().setConnectMax(connectMax);
    }

    /**
     * 将 Config 中的最大连接数同步到已就绪的 Server；未就绪时忽略。
     * 一般由 {@link XBleConfig#setConnectMax(int)} 内部调用。
     */
    public void syncConnectMaxToServer() {
        if (mXBleServer != null) {
            mXBleServer.setConnectMax(XBleConfig.getInstance().getConnectMax());
        }
    }

    // -------------------- 扫描 / 连接监听 --------------------

    /**
     * @deprecated 请使用 {@link #addBleScanFilterListener(OnBleScanFilterListener)}
     */
    @Deprecated
    public XBleManager setOnScanFilterListener(OnBleScanFilterListener onScanFilterListener) {
        addBleScanFilterListener(onScanFilterListener);
        return this;
    }

    /**
     * 添加扫描过滤/结果监听（弱引用，支持多处同时监听）。
     * 每个监听器独立执行 {@link OnBleScanFilterListener#onBleFilter}。
     * 建议在 Activity/Fragment {@code onDestroy} 中 {@link #removeBleScanFilterListener}。
     */
    public void addBleScanFilterListener(OnBleScanFilterListener listener) {
        BleScanFilterListenerIm.getInstance().addListListener(listener);
    }

    /** 移除扫描监听 */
    public void removeBleScanFilterListener(OnBleScanFilterListener listener) {
        BleScanFilterListenerIm.getInstance().removeListener(listener);
    }

    /** 清空全部扫描监听 */
    public void removeAllBleScanFilterListener() {
        BleScanFilterListenerIm.getInstance().removeListenerAll();
    }

    /**
     * @deprecated 请使用 {@link #addBleConnectListener(OnBleConnectListener)}
     */
    @Deprecated
    public XBleManager setOnBleConnectListener(OnBleConnectListener listener) {
        addBleConnectListener(listener);
        return this;
    }

    /**
     * 添加连接状态监听（弱引用，支持多处同时监听）。
     * 建议在 {@code onDestroy} 中 {@link #removeBleConnectListener}。
     */
    public void addBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().addListListener(listener);
    }

    /** 移除连接状态监听 */
    public void removeBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().removeListener(listener);
    }

    /** 清空全部连接状态监听 */
    public void removeAllBleConnectListener() {
        BleConnectListenerIm.getInstance().removeListenerAll();
    }

    /**
     * 设置系统蓝牙开关监听（弱引用，单监听）。
     * 服务未就绪时先弱引用暂存，就绪后自动设置；传 {@code null} 清除。
     */
    public void setOnBleStatusListener(OnBleStatusListener listener) {
        if (mContext == null) {
            throw new IllegalStateException("请先调用 init() 初始化.");
        }
        if (mXBleServer != null) {
            mXBleServer.setOnBleStatusListener(listener);
            mPendingStatusListenerRef = null;
        } else {
            mPendingStatusListenerRef = listener == null ? null : new WeakReference<>(listener);
        }
    }

    // -------------------- 前台服务 --------------------

    /**
     * 配置前台服务通知参数（需在 {@link #startForegroundService()} 之前调用）。
     *
     * @param id            通知 id
     * @param icon          通知图标
     * @param title         通知标题
     * @param activityClass 点击通知跳转的 Activity
     */
    public void initForegroundService(int id, @DrawableRes int icon, String title, Class<?> activityClass) {
        runOrQueue(() -> mXBleServer.initForegroundService(id, icon, title, activityClass));
    }

    /** 启动前台服务（保活，需已 {@link #initForegroundService}） */
    public void startForegroundService() {
        runOrQueue(() -> mXBleServer.startForeground());
    }

    /** 停止前台服务 */
    public void stopForegroundService() {
        runOrQueue(() -> mXBleServer.stopForeground());
    }

    // -------------------- 外围广播（API 21+） --------------------

    /**
     * 设置外围广播/被连接相关监听（弱引用）。
     * 服务未就绪时仅本地保存，就绪或 {@link #startAdvertiseData} 时再生效。
     */
    public void setOnBleAdvertiserConnectListener(OnBleAdvertiserConnectListener listener) {
        if (mContext == null) {
            throw new IllegalStateException("请先调用 init() 初始化.");
        }
        mAdvertiserListenerRef = listener == null ? null : new WeakReference<>(listener);
        if (mXBleServer != null) {
            mXBleServer.setOnBleAdvertiserConnectListener(listener);
        }
    }

    @Nullable
    private OnBleAdvertiserConnectListener getAdvertiserListener() {
        return mAdvertiserListenerRef == null ? null : mAdvertiserListenerRef.get();
    }

    /**
     * 开始 BLE 广播（手机作为外围设备）。
     * 会先回调 {@link OnBleAdvertiserConnectListener#onStartAdvertiser()}（若有监听）。
     *
     * @param adBleValueBean 广播与 GATT Service 配置，见 {@link AdBleBroadcastBean}
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startAdvertiseData(AdBleBroadcastBean adBleValueBean) {
        runOrQueue(() -> {
            OnBleAdvertiserConnectListener listener = getAdvertiserListener();
            if (listener != null) {
                listener.onStartAdvertiser();
            }
            mXBleServer.setOnBleAdvertiserConnectListener(listener);
            mXBleServer.startAdvertiseData(adBleValueBean, listener);
        });
    }

    /** 停止 BLE 广播 */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopAdvertiseData() {
        runOrQueue(() -> mXBleServer.stopAdvertiseData());
    }
}

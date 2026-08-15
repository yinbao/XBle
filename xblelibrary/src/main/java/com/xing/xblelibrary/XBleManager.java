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
 * xing<br>
 * 2021/7/17<br>
 * Ble 管理单例：负责绑定 {@link XBleServer}，并向业务暴露统一 API。
 */
public class XBleManager {

    private Context mContext;
    protected XBleServer mXBleServer;
    private Intent bindIntent;
    private onInitListener mOnInitListener;
    private boolean mServiceBinding;
    private final List<Runnable> mPendingActions = new ArrayList<>();
    private WeakReference<OnBleStatusListener> mPendingStatusListenerRef;
    private WeakReference<OnBleAdvertiserConnectListener> mAdvertiserListenerRef;

    private static final XBleConfig mXBleConfig = XBleConfig.getInstance();
    private static volatile XBleManager sXBleManager;

    public static XBleConfig getXBleConfig() {
        return mXBleConfig;
    }

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

    public void init(Context context) {
        init(context, null);
    }

    /**
     * 初始化并绑定 {@link XBleServer}（幂等）。始终使用 Application Context。
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
     * 服务是否已就绪（可执行扫描/连接等操作）
     */
    public boolean isReady() {
        return mXBleServer != null;
    }

    /**
     * 是否已调用过 init
     */
    public boolean isInitialized() {
        return mContext != null;
    }

    /**
     * 清空释放：停扫、断开、清监听、解绑并销毁服务
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

    public void setOnInitListener(onInitListener onInitListener) {
        mOnInitListener = onInitListener;
        if (mXBleServer != null && mOnInitListener != null) {
            mOnInitListener.onInitSuccess();
        }
    }

    public interface onInitListener {
        void onInitSuccess();

        default void onInitFailure() {
        }
    }

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
     * 未 init 时抛错；已 init 但未就绪时将任务排队，就绪后执行。
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

    private void onServiceErr() {
        clearPendingActions();
        if (mOnInitListener != null) {
            mOnInitListener.onInitFailure();
        }
    }

    private void onServiceSuccess() {
        applyConfigToServer();
        applyPendingListeners();
        drainPendingActions();
        if (mOnInitListener != null) {
            mOnInitListener.onInitSuccess();
        }
    }

    public List<BleDevice> getBleDeviceAll() {
        if (mXBleServer != null) {
            return mXBleServer.getBleDeviceAll();
        }
        return new ArrayList<>();
    }

    public void startScan(long timeOut, UUID... scanUUID) {
        final UUID[] uuids = scanUUID;
        runOrQueue(() -> mXBleServer.scanLeDevice(timeOut, uuids));
    }

    public void stopScan() {
        runOrQueue(() -> mXBleServer.stopScan());
    }

    public void connectDevice(BleBroadcastBean bleValueBean) {
        connectDevice(bleValueBean.getMac());
    }

    public void connectDevice(String mAddress) {
        runOrQueue(() -> mXBleServer.connectDevice(mAddress));
    }

    public void disconnectAll() {
        runOrQueue(() -> mXBleServer.disconnectAll());
    }

    @Nullable
    public BleDevice getBleDevice(String mac) {
        return mXBleServer == null ? null : mXBleServer.getBleDevice(mac);
    }

    @Nullable
    public AdBleDevice getAdBleDevice(String mac) {
        return mXBleServer == null ? null : mXBleServer.getAdBleDevice(mac);
    }

    public void setConnectBleTimeout(long connectTimeout) {
        runOrQueue(() -> mXBleServer.setConnectBleTimeout(connectTimeout));
    }

    @Deprecated
    public XBleManager setOnScanFilterListener(OnBleScanFilterListener onScanFilterListener) {
        addBleScanFilterListener(onScanFilterListener);
        return this;
    }

    public void addBleScanFilterListener(OnBleScanFilterListener listener) {
        BleScanFilterListenerIm.getInstance().addListListener(listener);
    }

    public void removeBleScanFilterListener(OnBleScanFilterListener listener) {
        BleScanFilterListenerIm.getInstance().removeListener(listener);
    }

    public void removeAllBleScanFilterListener() {
        BleScanFilterListenerIm.getInstance().removeListenerAll();
    }

    @Deprecated
    public XBleManager setOnBleConnectListener(OnBleConnectListener listener) {
        addBleConnectListener(listener);
        return this;
    }

    public void initForegroundService(int id, @DrawableRes int icon, String title, Class<?> activityClass) {
        runOrQueue(() -> mXBleServer.initForegroundService(id, icon, title, activityClass));
    }

    public void startForegroundService() {
        runOrQueue(() -> mXBleServer.startForeground());
    }

    public void stopForegroundService() {
        runOrQueue(() -> mXBleServer.stopForeground());
    }

    @Nullable
    public BluetoothAdapter getBluetoothAdapter() {
        return mXBleServer == null ? null : mXBleServer.getBluetoothAdapter();
    }

    /**
     * 监听蓝牙开关状态（弱引用；未就绪时先暂存，就绪后自动设置）
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

    public void addBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().addListListener(listener);
    }

    public void removeBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().removeListener(listener);
    }

    public void removeAllBleConnectListener() {
        BleConnectListenerIm.getInstance().removeListenerAll();
    }

    /**
     * 设置最大连接数（写入配置；服务就绪后立即生效，未就绪则随 applyConfig 生效）
     */
    public void setConnectMax(int connectMax) {
        XBleConfig.getInstance().setConnectMax(connectMax);
    }

    /**
     * 将配置中的最大连接数同步到已就绪的 Server（未就绪时忽略）
     */
    public void syncConnectMaxToServer() {
        if (mXBleServer != null) {
            mXBleServer.setConnectMax(XBleConfig.getInstance().getConnectMax());
        }
    }

    //----------------广播-------------------

    /**
     * 设置广播相关监听（弱引用持有）
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopAdvertiseData() {
        runOrQueue(() -> mXBleServer.stopAdvertiseData());
    }
}

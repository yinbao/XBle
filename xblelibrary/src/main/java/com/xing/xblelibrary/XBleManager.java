package com.xing.xblelibrary;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import com.xing.xblelibrary.bean.AdBleBroadcastBean;
import com.xing.xblelibrary.bean.BleBroadcastBean;
import com.xing.xblelibrary.config.XBleConfig;
import com.xing.xblelibrary.device.AdBleDevice;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.listener.BleConnectListenerIm;
import com.xing.xblelibrary.listener.OnBleAdvertiserConnectListener;
import com.xing.xblelibrary.listener.OnBleConnectListener;
import com.xing.xblelibrary.listener.OnBleScanFilterListener;
import com.xing.xblelibrary.listener.OnBleStatusListener;
import com.xing.xblelibrary.server.XBleServer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * xing<br>
 * 2021/7/17<br>
 * Ble管理对象
 */
public class XBleManager {

    private Context mContext;
    protected XBleServer mXBleServer;
    /**
     * 服务Intent
     */
    private Intent bindIntent;
    private onInitListener mOnInitListener;
    private static XBleConfig mXBleConfig = XBleConfig.getInstance();
    private static XBleManager sXBleManager;

    /**
     * 获取配置对象，可进行相关配置的修改
     *
     * @return XBleConfig
     */
    public static XBleConfig getXBleConfig() {
        return mXBleConfig;
    }

    public static synchronized XBleManager getInstance() {
        if (sXBleManager == null) {
            synchronized (XBleManager.class) {
                if (sXBleManager == null) {
                    sXBleManager = new XBleManager();
                }
            }
        }
        return sXBleManager;
    }

    public void init(Context context) {
        init(context, null);
    }

    public void init(Context context, onInitListener listener) {
        mContext = context;
        setOnInitListener(listener);
        startService();
    }

    /**
     * 清空释放内存
     */
    public void clear() {
        unbindService();
        mContext = null;
        sXBleManager = null;
        mOnInitListener = null;
        if (mXBleServer != null) {
            mXBleServer.finish();
        }
        mXBleServer = null;
    }

    private XBleManager() {

    }

    public void setOnInitListener(onInitListener onInitListener) {
        mOnInitListener = onInitListener;
        if (mXBleServer != null) {
            if (mOnInitListener != null) {
                mOnInitListener.onInitSuccess();
            }
        }

    }

    public interface onInitListener {
        void onInitSuccess();

        default void onInitFailure() {
        }
    }

    private void startService() {
        try {
            if (bindIntent == null) {
                bindIntent = new Intent(mContext, XBleServer.class);
                mContext.startService(bindIntent);
            }
            if (mFhrSCon != null)
                mContext.bindService(bindIntent, mFhrSCon, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void unbindService() {
        try {
            if (mFhrSCon != null)
                mContext.unbindService(mFhrSCon);
            bindIntent = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 服务连接与界面的连接
     */
    private ServiceConnection mFhrSCon = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //与服务建立连接
            mXBleServer = ((XBleServer.BluetoothBinder) service).getService();
            onServiceSuccess();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //与服务断开连接
            mXBleServer = null;
            onServiceErr();
        }
    };

    /**
     * 检查服务是否启动
     */
    private boolean checkBluetoothServiceStatus() {
        if (mContext == null) {
            throw new SecurityException("请先调用init()初始化.");
        }
        if (mXBleServer == null) {
            throw new SecurityException("请在初始化成功后,onInitListener.onInitSuccess()回调之后再执行其他操作.");
        }
        return true;
    }


    /**
     * 连接服务失败
     */
    private void onServiceErr() {
        if (mOnInitListener != null) {
            mOnInitListener.onInitFailure();
        }
    }

    /**
     * 连接服务成功
     */
    private void onServiceSuccess() {
        if (mOnInitListener != null) {
            mOnInitListener.onInitSuccess();
        }
        if (mXBleServer != null) {
            boolean autoConnectSystemBle = XBleConfig.getInstance().isAutoConnectSystemBle();
            if (autoConnectSystemBle) {
                mXBleServer.autoConnectSystemBle();
            }
            mXBleServer.setAutoMonitorSystemConnectBle(XBleConfig.getInstance().isAutoMonitorSystemConnectBle());
        }
    }


    /**
     * 获取所有的连接对象
     *
     * @return List<BleDevice>
     */
    public List<BleDevice> getBleDeviceAll() {
        if (mXBleServer != null) {
            return mXBleServer.getBleDeviceAll();
        }
        return new ArrayList<>();
    }


    //--------bleService相关方法---------


    /**
     * 搜索设备
     *
     * @param timeOut  超时时间,ms
     * @param scanUUID 扫描过滤的uuid
     */
    public void startScan(long timeOut, UUID... scanUUID) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.scanLeDevice(timeOut, scanUUID);
        }
    }


    /**
     * 停止搜索
     */
    public void stopScan() {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.stopScan();
        }
    }

    /**
     * 连接设备
     *
     * @param bleValueBean BleValueBean
     */
    public void connectDevice(BleBroadcastBean bleValueBean) {
        connectDevice(bleValueBean.getMac());
    }


    /**
     * 连接设备
     *
     * @param mAddress 设备mac地址
     */
    public void connectDevice(String mAddress) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.connectDevice(mAddress);
        }
    }


    /**
     * 断开所有的连接
     */
    public void disconnectAll() {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.disconnectAll();
        }
    }


    /**
     * 获取连接的设备对象
     *
     * @param mac 设备地址
     * @return BleDevice
     */
    @Nullable
    public BleDevice getBleDevice(String mac) {
        if (checkBluetoothServiceStatus()) {
            return mXBleServer.getBleDevice(mac);
        }
        return null;
    }


    /**
     * 获取外围的设备对象
     *
     * @param mac 设备地址
     * @return AdBleDevice
     */
    @Nullable
    public AdBleDevice getAdBleDevice(String mac) {
        if (checkBluetoothServiceStatus()) {
            return mXBleServer.getAdBleDevice(mac);
        }
        return null;
    }

    /**
     * 设备BLE连接超时时间(获取服务超时时间,超时后连接错误码返回-1)
     *
     * @param connectTimeout 时间ms
     */
    public void setConnectBleTimeout(long connectTimeout) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setConnectBleTimeout(connectTimeout);
        }
    }


    /**
     * 设置扫描过滤回调接口
     *
     * @param onScanFilterListener OnScanFilterListener
     */
    public XBleManager setOnScanFilterListener(OnBleScanFilterListener onScanFilterListener) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setOnBleScanFilterListener(new WeakReference<>(onScanFilterListener).get());
        }
        return this;
    }


    /**
     * 设置连接的接口
     *
     * @param listener OnCallbackBle
     */
    public XBleManager setOnBleConnectListener(OnBleConnectListener listener) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setOnBleConnectListener(new WeakReference<>(listener).get());
        }
        return this;
    }

    /**
     * 设置前台服务相关参数
     *
     * @param id            id
     * @param icon          logo
     * @param title         标题
     * @param activityClass 跳转的activity
     */
    public void initForegroundService(int id, @DrawableRes int icon, String title, Class<?> activityClass) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.initForegroundService(id, icon, title, activityClass);
        }
    }

    /**
     * 启动前台服务
     */
    public void startForegroundService() {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.startForeground();
        }
    }

    /**
     * 关闭前台服务
     */
    public void stopForegroundService() {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.stopForeground();
        }
    }

    /**
     * 获取BluetoothAdapter对象
     *
     * @return BluetoothAdapter
     */
    public BluetoothAdapter getBluetoothAdapter() {
        if (checkBluetoothServiceStatus()) {
            return mXBleServer.getBluetoothAdapter();
        }
        return null;
    }

    /**
     * 监听蓝牙状态
     */
    public void setOnBleStatusListener(OnBleStatusListener listener) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setOnBleStatusListener(new WeakReference<>(listener).get());
        }
    }

    /**
     * 以观察者模式监听蓝牙连接状态
     *
     * @param listener OnBleConnectListener
     */
    public void addBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().addListListener(new WeakReference<>(listener).get());
    }

    /**
     * 移除监听蓝牙连接状态
     *
     * @param listener OnBleConnectListener
     */
    public void removeBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().removeListener(new WeakReference<>(listener).get());
    }

    /**
     * 清空监听蓝牙连接状态的监听器
     */
    public void removeAllBleConnectListener() {
        BleConnectListenerIm.getInstance().removeListenerAll();
    }


    public void setConnectMax(int connectMax) {
        if (mXBleServer != null) {
            if (connectMax > 7) {
                connectMax = 7;
            }
            if (connectMax <= 0)
                connectMax = 1;
            mXBleServer.setConnectMax(connectMax);
        }
    }

    //----------------广播-------------------


    private OnBleAdvertiserConnectListener mOnBleAdvertiserConnectListener;

    public void setOnBleAdvertiserConnectListener(OnBleAdvertiserConnectListener onBleAdvertiserConnectListener) {
        mOnBleAdvertiserConnectListener = onBleAdvertiserConnectListener;
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setOnBleAdvertiserConnectListener(onBleAdvertiserConnectListener);
        }
    }

    /**
     * 广播
     *
     * @param adBleValueBean AdBleValueBean
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startAdvertiseData(AdBleBroadcastBean adBleValueBean) {
        if (mOnBleAdvertiserConnectListener != null) {
            mOnBleAdvertiserConnectListener.onStartAdvertiser();
        }
        if (mXBleServer != null) {
            mXBleServer.startAdvertiseData(adBleValueBean, mOnBleAdvertiserConnectListener);
        }
    }


    /**
     * 关闭广播
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopAdvertiseData() {
        if (mXBleServer != null)
            mXBleServer.stopAdvertiseData();

    }


}

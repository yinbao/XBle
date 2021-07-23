package com.xing.xblelibrary;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.xing.xblelibrary.bean.BleValueBean;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.listener.BleConnectListenerIm;
import com.xing.xblelibrary.listener.OnBleConnectListener;
import com.xing.xblelibrary.listener.OnBleScanConnectListener;
import com.xing.xblelibrary.listener.OnBleScanFilterListener;
import com.xing.xblelibrary.listener.OnBleStatusListener;
import com.xing.xblelibrary.server.XBleServer;

import java.util.Map;
import java.util.UUID;

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

    private static XBleManager sXBleManager;

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
        if (bindIntent == null) {
            bindIntent = new Intent(mContext, XBleServer.class);
            mContext.startService(bindIntent);
        }
        if (mFhrSCon != null)
            mContext.bindService(bindIntent, mFhrSCon, Context.BIND_AUTO_CREATE);

    }

    private void unbindService() {
        if (mFhrSCon != null)
            mContext.unbindService(mFhrSCon);
        bindIntent = null;
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
    }


    //--------bleService相关方法---------

    /**
     * 搜索设备
     *
     * @param timeOut  超时时间,ms
     * @param scanUUID 扫描过滤的uuid
     */
    public void startScan(long timeOut, UUID... scanUUID) {
        startScan(timeOut, null, scanUUID);
    }

    /**
     * 搜索设备
     *
     * @param timeOut  超时时间,ms
     * @param map      给指定uuid的设备设置cid,vid,pid  <"uuid","cid,vid,pid">
     * @param scanUUID 扫描过滤的uuid
     */
    public void startScan(long timeOut, Map<String, String> map, UUID... scanUUID) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.scanLeDevice(timeOut, map, scanUUID);
        }
    }

    /**
     * 搜索设备
     *
     * @param callback 扫描连接的回调接口
     * @param timeOut  超时时间,ms
     * @param map      给指定uuid的设备设置cid,vid,pid  <"uuid","cid,vid,pid">
     * @param scanUUID 扫描过滤的uuid
     */
    public void startScan(OnBleScanConnectListener callback, long timeOut, Map<String, String> map, UUID... scanUUID) {
        if (checkBluetoothServiceStatus()) {
            setOnBleScanConnectListener(callback);
            mXBleServer.scanLeDevice(timeOut, map, scanUUID);
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
    public void connectDevice(BleValueBean bleValueBean) {
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
     * 设备监听,监听指定的mac地址的设备,发现连接成功后马上连接获取操作的对象
     *
     * @param mAddress 设备地址,null或者空字符串可以监听所有的地址
     */
    public void deviceConnectListener() {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.deviceConnectListener();
        }
    }

    /**
     * 设置扫描过滤回调接口
     *
     * @param onScanFilterListener OnScanFilterListener
     */
    public void setOnScanFilterListener(OnBleScanFilterListener onScanFilterListener) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setOnScanFilterListener(onScanFilterListener);
        }
    }


    /**
     * 设置扫描连接的接口
     *
     * @param listener OnCallbackBle
     */
    public void setOnBleScanConnectListener(OnBleScanConnectListener listener) {
        if (checkBluetoothServiceStatus()) {
            mXBleServer.setOnBleScanConnectListener(listener);
        }
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
            mXBleServer.setOnBleStatusListener(listener);
        }
    }

    /**
     * 以观察者模式监听蓝牙连接状态
     *
     * @param listener OnBleConnectListener
     */
    public void addBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().addListListener(listener);
    }

    /**
     * 移除监听蓝牙连接状态
     *
     * @param listener OnBleConnectListener
     */
    public void removeBleConnectListener(OnBleConnectListener listener) {
        BleConnectListenerIm.getInstance().removeListener(listener);
    }

    /**
     * 清空监听蓝牙连接状态的监听器
     */
    public void removeAllBleConnectListener() {
        BleConnectListenerIm.getInstance().removeListenerAll();
    }


}

package com.xing.xblelibrary.server;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.text.TextUtils;

import com.xing.xblelibrary.bean.BleValueBean;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.listener.BleConnectListenerIm;
import com.xing.xblelibrary.listener.OnBleScanConnectListener;
import com.xing.xblelibrary.listener.OnBleScanFilterListener;
import com.xing.xblelibrary.listener.OnBleStatusListener;
import com.xing.xblelibrary.utils.BleLog;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;


/**
 * xing<br>
 * 2021/07/21<br>
 * 蓝牙服务
 */
public class XBleServer extends Service {

    /**
     * 开始搜索
     */
    private final int SCAN_BLE_DEVICE = 1;
    /**
     * 读取搜索结果
     */
    private final int STOP_BLE_DEVICE = 2;
    /**
     * 搜索频繁
     */
    private final int SCAN_BLE_DEVICE_FREQUENTLY = 3;
    /**
     * 获取蓝牙服务的通知
     */
    private final int GET_BLE_SERVICE = 5;

    /**
     * 蓝牙连接超时,获取服务超时
     */
    private final int CONNECT_BLE_TIMEOUT = 7;

    /**
     * 停止服务
     */
    private static final int STOP_SERVER = 9;

    private static String TAG = XBleServer.class.getName();

    /**
     * 绑定服务
     */
    private BluetoothBinder mBinder;
    private XBleServer mBluMainService;
    /**
     * 蓝牙控制对象
     */
    private BluetoothManager mBleManager;
    private BluetoothAdapter mBluetoothAdapter;
    /**
     * 连接接口回调
     */
    private OnBleScanConnectListener mBleScanConnectListener = null;
    /**
     * 蓝牙状态接口
     */
    private OnBleStatusListener mOnBleStatusListener = null;
    /**
     * 是否为扫描状态
     */
    private boolean mScanStatus = false;

    /**
     * 蓝牙连接超时
     */
    private int mConnectBleTimeout = 10 * 1000;
    /**
     * 扫描次数,用于判断30S内是否调用扫描次数超过5次,避免被系统判定为扫描太频繁
     */
    private volatile int mScanNumber = 0;
    /**
     * 第一次扫描时间
     */
    private long mFirstScanTime = 0;
    /**
     * 连接后的蓝牙对象
     */
    private Map<String, BleDevice> mBleObjectMap;

    /**
     * 需要过滤的uuid
     */
    private UUID[] mScanUUID = null;
    /**
     * 搜索的时间,默认一直扫描
     */
    private long mTimeOut = 0;

    private Handler mHandler = new Handler(Looper.myLooper()) {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                //停止,退出服务
                case STOP_SERVER:
                    try {
                        finish();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                //开始搜索
                case SCAN_BLE_DEVICE:
                    //一直搜索
                    stopScan();
                    scanLeDevice(mTimeOut, mScanUUID);
                    break;
                //重置扫描
                case SCAN_BLE_DEVICE_FREQUENTLY:
                    mScanNumber = 0;
                    BleLog.i(TAG, "已重置扫描次数");
                    break;
                //停止搜索
                case STOP_BLE_DEVICE:
                    int timeOut = msg.arg1;
                    if (timeOut > 0) {
                        if (mBleScanConnectListener != null) {
                            mBleScanConnectListener.onScanTimeOut();
                        }
                        stopScan();//停止搜索
                    } else if (timeOut == 0) {
                        //代表一直搜索,由于系统限制,一直搜索的时候需要每隔20分钟重启一次,避免出现异常
                        mHandler.removeMessages(SCAN_BLE_DEVICE);
                        mHandler.sendEmptyMessageDelayed(SCAN_BLE_DEVICE, 20 * 60 * 1000);
                    }
                    break;

                //蓝牙连接成功,获取服务
                case GET_BLE_SERVICE:
                    BluetoothGatt gatt = (BluetoothGatt) msg.obj;
                    if (gatt != null) {
                        gatt.discoverServices();//搜索服务
                        BleLog.i(TAG, "获取蓝牙服务");
                        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
                        mHandler.sendEmptyMessageDelayed(CONNECT_BLE_TIMEOUT, mConnectBleTimeout);
                    }

                    break;

                //蓝牙连接超时
                case CONNECT_BLE_TIMEOUT:
                    if (mConnectGatt != null) {
                        String address = mConnectGatt.getDevice().getAddress();
                        BleLog.e(TAG, "连接超时:" + mConnectGatt + "||mac:" + address);
                        if (mConnectGatt != null)
                            mConnectGatt.disconnect();
                        if (mConnectGatt != null)
                            mConnectGatt.close();
                        mConnectGatt = null;
                        gattOld = null;
                        runOnMainThread(() -> {
                            if (mBleScanConnectListener != null) {
                                mBleScanConnectListener.onDisConnected(address, -1);
                            }
                            BleConnectListenerIm.getInstance().onDisConnected(mBleScanConnectListener, address, -1);
                        });
                    } else {
                        BleLog.e(TAG, "蓝牙连接超时:mConnectGatt=null");
                        mHandler.sendEmptyMessage(STOP_BLE_DEVICE);

                    }
                    break;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public class BluetoothBinder extends Binder {
        public XBleServer getService() {
            return XBleServer.this;
        }
    }

    /**
     * 它与startService()对应，当服务启动后调用。如果你重写了该方法，你就有责任自己去
     * 当任务结束以后，调用stopSelf()或者stopService()来停止服务。如果你是绑定的服务，就不需重新该方法了
     * 服务被启动
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //进入后台后设置前台服务
//        startForeground(startId);
        return START_STICKY_COMPATIBILITY;//服务就会在资源紧张的时候被杀掉，然后在资源足够的时候再恢复
    }

    private int mId;
    @DrawableRes
    private int mIcon;
    private String mTitle;
    private Class<?> mActivityClass;

    /**
     * 初始化前台服务需要的参数
     *
     * @param id            id
     * @param icon          icon
     * @param title         title
     * @param activityClass activity
     */
    public void initForegroundService(int id, @DrawableRes int icon, String title, Class<?> activityClass) {
        mId = id;
        mIcon = icon;
        mTitle = title;
        mActivityClass = activityClass;
    }


    /**
     * 启动前台服务
     */
    @RequiresPermission(allOf = {Manifest.permission.FOREGROUND_SERVICE})
    public void startForeground() {
        Intent intent = new Intent(this, mActivityClass);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "channel_id";
            String channelName = "channel_name";
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN);
            notificationManager.createNotificationChannel(notificationChannel);
            notification = new Notification.Builder(this, channelId).setContentIntent(pendingIntent) //设置通知栏点击意图
                    .setSmallIcon(mIcon).setLargeIcon(Icon.createWithResource(this, mIcon)).setContentTitle(mTitle).setOngoing(true).build();
        } else {
            notification = new Notification.Builder(this).setContentIntent(pendingIntent) //设置通知栏点击意图
                    .setSmallIcon(mIcon).setContentTitle(mTitle).setOngoing(true).build();
        }
        startForeground(mId, notification);
    }


    /**
     * 停止前台服务
     */
    @RequiresPermission(allOf = {Manifest.permission.FOREGROUND_SERVICE})
    public void stopForeground() {
        stopForeground(true);
    }

    //----------------------初始化-------------------

    @Override
    public void onCreate() {
        super.onCreate();
        BleLog.i(TAG, "onCreate");
        //注册广播
        initStart();
    }


    /**
     * 初始化启动信息
     */
    private void initStart() {
        BleLog.i(TAG, "初始化启动信息");
        if (mBinder == null)
            mBinder = new BluetoothBinder();
        if (mBleManager == null)
            mBleManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothAdapter == null && mBleManager != null) {
            mBluetoothAdapter = mBleManager.getAdapter();
        } else {
            mHandler.sendEmptyMessage(STOP_SERVER);
            return;
        }
        mBluMainService = this;
        mBleObjectMap = new HashMap<>();
        mGattCallback = new MyBluetoothGattCallback();
        bleState();
    }

    //------------------------搜索设备-----------------------------------

    private OnBleScanFilterListener mOnScanFilterListener;
    /**
     * 给指定的uuid设置cid,vid,pid
     */
    private volatile Map<String, String> mMap = null;

    /**
     * 设置扫描过滤回调接口
     *
     * @param onScanFilterListener OnScanFilterListener
     */
    public void setOnScanFilterListener(OnBleScanFilterListener onScanFilterListener) {
        mOnScanFilterListener = onScanFilterListener;
    }


    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
    public void scanLeDevice(long timeOut, UUID... scanUUID) {
        this.scanLeDevice(timeOut, null, scanUUID);
    }


    /**
     * 搜索设备
     * 扫描过于频繁会导致扫描失败
     * 需要保证5次扫描总时长超过30s
     *
     * @param timeOut  超时时间,毫秒(搜索多久去取数据,0代表一直搜索)
     * @param scanUUID 过滤的UUID(空/null代码不过滤)
     */
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
    public void scanLeDevice(long timeOut, Map<String, String> map, UUID... scanUUID) {
        mMap = map;
        BleLog.i(TAG, "搜索设备timeOut=" + timeOut);
        mTimeOut = timeOut;
        mHandler.removeMessages(STOP_BLE_DEVICE);
        if (mScanStatus) {
            BleLog.i(TAG, "是扫描状态就重置定时");
            Message message = Message.obtain();
            message.what = STOP_BLE_DEVICE;
            message.arg1 = (int) timeOut;
            mHandler.sendMessageDelayed(message, timeOut);
            runOnMainThread(() -> {
                if (mBleScanConnectListener != null) {
                    mBleScanConnectListener.onStartScan();
                }
            });
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            BleLog.e(TAG, "蓝牙未开启.");
            bleClose();
            return;
        }
        mScanNumber++;
        if (mScanNumber == 1) {
            mFirstScanTime = System.currentTimeMillis();
            mHandler.sendEmptyMessageDelayed(SCAN_BLE_DEVICE_FREQUENTLY, 30 * 1000);
        } else if (mScanNumber >= 6) {
            if (mScanStatus)
                stopScan();
            long timeMillis = System.currentTimeMillis();
            long l = 30100 - (timeMillis - mFirstScanTime);
            runOnMainThread(() -> {
                if (mBleScanConnectListener != null) {
                    mBleScanConnectListener.onScanErr(l);
                }
            });
            return;
        }


        try {
            if (scanUUID != null && scanUUID.length > 0) {
                mScanUUID = scanUUID;
            } else {
                mScanUUID = null;
            }
            //扫描过于频繁会导致扫描失败
            //需要保证5次扫描总时长超过30s
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mScanCallback == null)
                    mScanCallback = new MyScanCallback();
                ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                mBluetoothAdapter.getBluetoothLeScanner().startScan(null, settings, mScanCallback);
            } else {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
            mScanStatus = true;

            runOnMainThread(() -> {
                if (mBleScanConnectListener != null) {
                    mBleScanConnectListener.onStartScan();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        Message message = Message.obtain();
        message.what = STOP_BLE_DEVICE;
        message.arg1 = (int) timeOut;
        mHandler.sendMessageDelayed(message, timeOut);

    }

    /**
     * 取消扫描
     */
    public void stopScan() {
        try {
            if (mHandler != null) {
                mHandler.removeMessages(SCAN_BLE_DEVICE);
                mHandler.removeMessages(STOP_BLE_DEVICE);
            }

            if (mBluetoothAdapter != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (mBluetoothAdapter.getBluetoothLeScanner() != null) {
                        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
                    }
                } else {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }
            mScanStatus = false;
            mMap = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 搜索结果的回调
     */
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = (device, rssi, scanRecord) -> {
        if (device == null || scanRecord == null)
            return;
        BleValueBean mBle = new BleValueBean(device, rssi, scanRecord, mMap);

        if (mScanUUID != null && mScanUUID.length > 0) {
            boolean uuidOk = false;
            List<ParcelUuid> parcelUuids = mBle.getParcelUuids();
            if (parcelUuids == null)
                return;
            for (ParcelUuid uuid : parcelUuids) {
                for (UUID uuid1 : mScanUUID) {
                    if (uuid.toString().equalsIgnoreCase(uuid1.toString())) {
                        uuidOk = true;
                        break;
                    }
                }

            }
            if (!uuidOk) {
                return;
            }
        }

        saveScanData(mBle);
    };

    private MyScanCallback mScanCallback;


    private int mScanErr = 0;

    /**
     * 搜索结果的回调 5.0以上
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private class MyScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            mScanErr = 0;
            BleValueBean mBle = new BleValueBean(result, mMap);
            if (mScanUUID != null && mScanUUID.length > 0) {
                boolean uuidOk = false;
                if (result.getScanRecord() == null)
                    return;
                List<ParcelUuid> parcelUuids = mBle.getParcelUuids();
                if (parcelUuids == null)
                    return;
                for (ParcelUuid uuid : parcelUuids) {
                    for (UUID uuid1 : mScanUUID) {
                        if (uuid.toString().equalsIgnoreCase(uuid1.toString())) {
                            uuidOk = true;
                            break;
                        }
                    }
                }
                if (!uuidOk) {
                    return;
                }
            }
            saveScanData(mBle);

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            mScanErr = 0;
            if (mBleScanConnectListener != null) {
                mBleScanConnectListener.onScanTimeOut();
            }

        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            BleLog.e(TAG, "扫描失败:" + errorCode);
            if (mScanErr < 3) {
                mScanErr++;
                stopScan();
                mHandler.removeMessages(SCAN_BLE_DEVICE);
                mHandler.sendEmptyMessageDelayed(SCAN_BLE_DEVICE, 2000);
            } else {
                //搜索失败多次后关闭蓝牙
                boolean status = mBluetoothAdapter.disable();
                if (status) {
                    bleClose();
                }
            }
        }
    }

    /**
     * 保存搜索到的数据到列表
     *
     * @param mBle 数据
     */
    private synchronized void saveScanData(final BleValueBean mBle) {
        boolean isMeBle = true;
        if (mOnScanFilterListener != null) {
            //过滤,是否是
            isMeBle = mOnScanFilterListener.onBleFilter(mBle);
        }
        if (isMeBle) {
            runOnMainThread(() -> {
                if (mOnScanFilterListener != null) {
                    //广播数据
                    mOnScanFilterListener.onScanBleInfo(mBle);
                }
                if (mBleScanConnectListener != null) {
                    mBleScanConnectListener.onScanning(mBle);
                }
            });
        }
    }


    //----------------------连接,操作-------------------------------------------------------------

    /**
     * 当前连接的对象
     */
    private volatile BluetoothGatt mConnectGatt = null;
    /**
     * 监听连接的蓝牙总线服务
     */
    private BluetoothGattServer mBluetoothGattServer;

    /**
     * 设备监听,监听指定的mac地址的设备,发现连接成功后马上连接获取操作的对象
     *
     * @param mAddress 设备地址,null或者空字符串可以监听所有的地址
     * @param status   是否开启监听
     */
    public void deviceConnectListener(String mAddress, boolean status) {
        if (status) {
            //设备已连接
            mBluetoothGattServer = mBleManager.openGattServer(mBluMainService, new BluetoothGattServerCallback() {//BluetoothGattCallback
                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                    super.onConnectionStateChange(device, status, newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        //系统监听到连接成功
                        if (mConnectGatt == null) {
                            //当前不在连接状态
                            String address = device.getAddress();
                            if (TextUtils.isEmpty(mAddress)) {
                                connectDevice(address);
                            } else if (mAddress.equalsIgnoreCase(device.getAddress())) {
                                connectDevice(mAddress);
                            }
                        }
                    }
                }
            });
        } else {
            if (mBluetoothGattServer != null) {
                mBluetoothGattServer.close();
            }
        }


    }


    public void connectDevice(String mAddress) {
        connectBleDevice(mAddress);
    }

    /**
     * 连接蓝牙设备
     *
     * @param mAddress 连接的设备地址
     */
    private synchronized void connectBleDevice(String mAddress) {
        if (!mBluetoothAdapter.isEnabled()) {
            BleLog.e(TAG, "蓝牙未开启.");
            bleClose();
            return;
        }

        if (mConnectGatt != null) {
            BleLog.e(TAG, "已经在连接状态了,当前连接的设备:" + mConnectGatt.getDevice().getAddress());
            return;
        }
        BluetoothDevice device;
        try {
            device = mBluetoothAdapter.getRemoteDevice(mAddress);
            if (device == null) {
                BleLog.e(TAG, "找不到需要连接的设备:" + mAddress);
                return;
            }
        } catch (IllegalArgumentException e) {
            BleLog.e(TAG, "连接的设备地址无效:" + mAddress);
            e.printStackTrace();
            return;
        }
        if (mBleObjectMap.size() > 5) {
            //连接超过5个就断开所有的
            disconnectAll();
        }
        mConnectGatt = device.connectGatt(mBluMainService, false, mGattCallback);//连接操作
        runOnMainThread(() -> {
            if (mBleScanConnectListener != null) {
                mBleScanConnectListener.onConnecting(mAddress);
            }
            BleConnectListenerIm.getInstance().onConnecting(mBleScanConnectListener, mAddress);
        });

        BleLog.i(TAG, "开始连接:" + mConnectGatt);
        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
        mHandler.sendEmptyMessageDelayed(CONNECT_BLE_TIMEOUT, mConnectBleTimeout);

    }


    /**
     * 返回指定的地址是否连接上
     *
     * @param mac 连接的地址
     * @return 是否连接成功
     */
    public boolean getConnectStatus(String mac) {
        BleDevice mConnectBleObject = mBleObjectMap.get(mac.toUpperCase());
        if (mConnectBleObject != null) {
            return mConnectBleObject.isConnectSuccess();
        }
        return false;
    }


    /**
     * 获取操作对象
     *
     * @param mac 设备地址
     * @return DeviceObject
     */
    @Nullable
    public BleDevice getBleDevice(String mac) {
        if (mac != null && !mac.isEmpty()) {
            return mBleObjectMap.get(mac.toUpperCase());
        } else {
            return null;
        }
    }


    //------------------------------------------------------------------------------------------


    /**
     * 避免连续收到断开的消息
     */
    private long discoverServicesTime = 0;
    /**
     * 连接ble的回调操作类
     */
    private MyBluetoothGattCallback mGattCallback;
    private BluetoothGatt gattOld;

    /**
     * 连接ble的操作回调类
     */
    private class MyBluetoothGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            try {
                BleLog.e(TAG, "连接返回的状态status:" + status + "||newState:" + newState + "||mac:" + gatt.getDevice().getAddress());
                mHandler.removeMessages(GET_BLE_SERVICE);
                mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BleLog.i(TAG, "通知连接成功");
                    if (newState == BluetoothProfile.STATE_CONNECTED && gattOld != gatt) {
                        runOnMainThread(() -> {
                            String mac = gatt.getDevice().getAddress();

                            if (mBleScanConnectListener != null) {
                                mBleScanConnectListener.onConnectionSuccess(mac);
                            }
                            BleConnectListenerIm.getInstance().onConnectionSuccess(mBleScanConnectListener, mac);
                        });

                        gattOld = gatt;
                        BleLog.i(TAG, "连接成功的对象:" + gatt);
                        Message message = Message.obtain();
                        message.what = GET_BLE_SERVICE;
                        message.obj = gatt;
                        mHandler.sendMessageDelayed(message, 100);//延迟一下才去获取服务,避免系统多次回调连接成功
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        //避免系统多次回调断开连接的消息
                        if (System.currentTimeMillis() - discoverServicesTime > 500) {
                            String mAddress = gatt.getDevice().getAddress().toUpperCase();
                            if (mConnectGatt != null && mAddress.equals(mConnectGatt.getDevice().getAddress())) {
                                mConnectGatt = null;
                            }
                            discoverServicesTime = System.currentTimeMillis();
                            disconnect(mAddress, status, gatt);
                        } else {
                            BleLog.e(TAG, "连接断开间隔过短");
                        }
                    }
                } else {
                    String mAddress = gatt.getDevice().getAddress().toUpperCase();
                    if (mConnectGatt != null && mAddress.equals(mConnectGatt.getDevice().getAddress().toUpperCase())) {
                        mConnectGatt = null;
                    }
                    disconnect(mAddress, status, gatt);
                    MyBleDeviceUtils.refreshDeviceCache(gatt);
                }


            } catch (NullPointerException e) {
                BleLog.e(TAG, "连接/断开异常:" + e.toString());
                e.printStackTrace();
            }

        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gattOld = null;
                runOnMainThread(() -> {
                    //发现新服务
                    discoverServicesTime = 0;
                    mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
                    mHandler.removeMessages(GET_BLE_SERVICE);
                    String mac = gatt.getDevice().getAddress().toUpperCase();
                    synchronized (mBleObjectMap) {
                        if (mBleObjectMap.containsKey(mac)) {
                            BleDevice mConnectBleObject = mBleObjectMap.get(mac);
                            if (mConnectBleObject != null) {
                                mConnectBleObject.disconnect(false);
                            }
                        }
                        BleDevice mDevice = new BleDevice(gatt, mac);
                        mBleObjectMap.put(mac, mDevice);
                        if (mBleScanConnectListener != null) {
                            mBleScanConnectListener.onServicesDiscovered(mac);
                        }
                        BleConnectListenerIm.getInstance().onServicesDiscovered(mBleScanConnectListener, mac);
                        mConnectGatt = null;

                    }
                });
            } else {
                BleLog.e(TAG, "连接失败:服务读取失败:");
                String mac = gatt.getDevice().getAddress();
                disconnect(mac, -2, gatt);
                gatt.disconnect();
                gatt.close();
                MyBleDeviceUtils.refreshDeviceCache(gatt);
                mConnectGatt = null;
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //读取数据
            BleLog.i(TAG, "回调读操作:onCharacteristicRead:" + status);
            String mac = gatt.getDevice().getAddress().toUpperCase();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleDevice mConnectBleObject = mBleObjectMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.readData(characteristic);
            }

        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //写入数据(可用于校验,拼包,重发等操作)
            BleLog.i(TAG, "回调写操作:onCharacteristicWrite:" + status);
            String mac = gatt.getDevice().getAddress().toUpperCase();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleDevice mConnectBleObject = mBleObjectMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.writeData(characteristic);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            String mac = gatt.getDevice().getAddress().toUpperCase();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleDevice mConnectBleObject = mBleObjectMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.descriptorWriteOk(descriptor);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //通知返回的数据
            String mac = gatt.getDevice().getAddress().toUpperCase();
            BleDevice mConnectBleObject = mBleObjectMap.get(mac);
            if (mConnectBleObject != null)
                mConnectBleObject.notifyData(characteristic);
        }


        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            //回调信号强度
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String mac = gatt.getDevice().getAddress().toUpperCase();
                BleDevice mConnectBleObject = mBleObjectMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.setRssi(rssi);

            }
        }


        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            //TODO Mtu改变(一般用不上,可改变传送效率,兼容性待验)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String mac = gatt.getDevice().getAddress().toUpperCase();
                BleDevice mConnectBleObject = mBleObjectMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.getMtu(mtu);

            }
        }


    }
    //----------------------断开-------------------------------------------------------------


    /**
     * 断开连接,清除保存的对象,传入gatt,确保断开
     *
     * @param mac  mac地址
     * @param code 状态码
     * @param gatt BluetoothGatt
     */
    private void disconnect(final String mac, int code, BluetoothGatt gatt) {
        if (gatt != null)
            gatt.close();
        runOnMainThread(() -> {
            BleLog.i(TAG, "通知连接断开:" + code);
            if (mBleScanConnectListener != null) {
                mBleScanConnectListener.onDisConnected(mac, code);
            }
            BleConnectListenerIm.getInstance().onDisConnected(mBleScanConnectListener, mac, code);
            BleDevice bleDevice = mBleObjectMap.get(mac);
            if (bleDevice != null) {
                bleDevice.onDisConnected();
            }
            removeConnect(mac);
        });

        discoverServicesTime = 0;
    }


    /**
     * 删除已连接的对象
     *
     * @param mac 需要移除的地址
     */
    public void removeConnect(String mac) {
        if (mBleObjectMap != null) {
            mBleObjectMap.remove(mac);
        }
    }


    /**
     * （断开所有蓝牙连接）
     */
    public void disconnectAll() {
        BleLog.i(TAG, "disconnectAll:断开所有蓝牙连接");
        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
        if (mConnectGatt != null) {
            mConnectGatt.disconnect();
            if (mConnectGatt != null)
                mConnectGatt.close();
            mConnectGatt = null;
        }
        if (mBleObjectMap != null) {
            synchronized (mBleObjectMap) {
                if (mBleObjectMap != null) {
                    for (String mAddress : mBleObjectMap.keySet()) {
                        BleDevice mConnectBleObject = mBleObjectMap.get(mAddress);
                        if (mConnectBleObject != null)
                            mConnectBleObject.disconnect();
                    }
                    mBleObjectMap.clear();
                }
            }
        }
    }


    //---------------------广播------------------------------------------------------------
    /**
     * 蓝牙状态的广播
     */
    private BleStateReceiver mBleStateReceiver = null;

    /**
     * 注册蓝牙广播(监听蓝牙打开关闭等状态)
     */
    private void bleState() {
        try {
            if (mBleStateReceiver == null) {
                IntentFilter intentFilter = new IntentFilter();
                mBleStateReceiver = new BleStateReceiver();
                intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//蓝牙状态广播
                registerReceiver(mBleStateReceiver, intentFilter);
                BleLog.i(TAG, "注册广播成功");
            }
        } catch (IllegalArgumentException e) {
            BleLog.e(TAG, "注册广播失败:" + e.getMessage());
            e.printStackTrace();
        }
    }

    private class BleStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        BleLog.i(TAG, "STATE_OFF 手机蓝牙关闭");
                        bleClose();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        BleLog.i(TAG, "STATE_TURNING_OFF 手机蓝牙正在关闭");

                        break;
                    case BluetoothAdapter.STATE_ON:
                        BleLog.i(TAG, "STATE_ON 手机蓝牙开启");
                        bleOpen();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        BleLog.d(TAG, "STATE_TURNING_ON 手机蓝牙正在开启");
                        break;
                }
            }
        }
    }

    //-------------------------------- 蓝牙开关状态接口实现---------------------------------------------------


    private void bleOpen() {
        BleLog.i(TAG, "蓝牙打开");
        runOnMainThread(() -> {
            if (mBleScanConnectListener != null) {
                mBleScanConnectListener.bleOpen();
            }
            if (mOnBleStatusListener != null) {
                mOnBleStatusListener.bleOpen();
            }
            BleConnectListenerIm.getInstance().bleOpen(mBleScanConnectListener);
        });

    }

    private void bleClose() {
        BleLog.i(TAG, "蓝牙关闭");
        stopScan();
        runOnMainThread(() -> {
            if (mBleScanConnectListener != null) {
                mBleScanConnectListener.bleClose();
            }
            if (mOnBleStatusListener != null) {
                mOnBleStatusListener.bleClose();
            }
            BleConnectListenerIm.getInstance().bleClose(mBleScanConnectListener);
        });

        mScanStatus = false;
        mHandler.removeMessages(STOP_BLE_DEVICE);
    }


    // -----------------------------------------------------------------------------------

    private Handler mThreadHandler = new Handler(Looper.getMainLooper());

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mThreadHandler.post(runnable);
        }
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }


    //-----------------------------------------------------------------------------------


    @Override
    public boolean stopService(Intent name) {
        return super.stopService(name);
        //        STOP_BLE_DEVICE
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        finish();
        BleLog.i(TAG, "onDestroy");
    }

    /**
     * 退出释放资源
     */
    public final void finish() {
        BleLog.i(TAG, "退出释放资源");
        //停止搜索
        stopScan();
        disconnectAll();//断开所有连接
        mBleManager = null;
        mBleScanConnectListener = null;
        if (mBleStateReceiver != null) {
            unregisterReceiver(mBleStateReceiver);
            BleLog.i(TAG, "注销蓝牙广播");
            mBleStateReceiver = null;
        }
        stopSelf();
    }


//--------------------------------set/get-------------------------------------------

    /**
     * 是否为扫描状态
     */
    public boolean isScanStatus() {
        return mScanStatus;
    }

    /**
     * 设置连接超时时间
     */
    public void setConnectBleTimeout(int connectBleTimeout) {
        this.mConnectBleTimeout = connectBleTimeout;
    }


    public void setBleStateReceiver(BleStateReceiver bleStateReceiver) {
        mBleStateReceiver = bleStateReceiver;
    }

    public void setOnBleScanConnectListener(OnBleScanConnectListener listener) {
        mBleScanConnectListener = listener;
    }

    public void setOnBleStatusListener(OnBleStatusListener onBleStatusListener) {
        mOnBleStatusListener = onBleStatusListener;
    }
}

package com.xing.xblelibrary.server;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
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
import android.os.SystemClock;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.xing.xblelibrary.bean.AdBleBroadcastBean;
import com.xing.xblelibrary.bean.BleBroadcastBean;
import com.xing.xblelibrary.config.XBleConfig;
import com.xing.xblelibrary.config.XBleStaticConfig;
import com.xing.xblelibrary.device.AdBleDevice;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.listener.BleConnectListenerIm;
import com.xing.xblelibrary.listener.OnBleAdvertiserConnectListener;
import com.xing.xblelibrary.listener.OnBleConnectListener;
import com.xing.xblelibrary.listener.OnBleScanFilterListener;
import com.xing.xblelibrary.listener.OnBleStatusListener;
import com.xing.xblelibrary.utils.BleCheckUtils;
import com.xing.xblelibrary.utils.MyBleDeviceUtils;
import com.xing.xblelibrary.utils.XBleL;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * xing<br>
 * 2021/07/21<br>
 * ????????????
 */
public class XBleServer extends Service {

    /**
     * ????????????
     */
    private final int SCAN_BLE_DEVICE = 1;
    /**
     * ??????????????????
     */
    private final int STOP_BLE_DEVICE = 2;
    /**
     * ????????????
     */
    private final int SCAN_BLE_DEVICE_FREQUENTLY = 3;
    /**
     * ???????????????????????????
     */
    private final int GET_BLE_SERVICE = 5;

    /**
     * ??????????????????,??????????????????
     */
    private final int CONNECT_BLE_TIMEOUT = 7;

    /**
     * ????????????
     */
    private static final int STOP_SERVER = 9;

    private static String TAG = XBleServer.class.getName();

    /**
     * ????????????
     */
    private BluetoothBinder mBinder;
    private XBleServer mBluMainService;
    /**
     * ??????????????????
     */
    private BluetoothManager mBleManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothA2dp mBluetoothA2dp;


    /**
     * ????????????
     */
    private OnBleConnectListener mOnBleConnectListener = null;
    /**
     * ??????????????????
     */
    private OnBleAdvertiserConnectListener mOnBleAdvertiserConnectListener = null;

    /**
     * ??????????????????
     */
    private OnBleScanFilterListener mOnBleScanFilterListener = null;
    /**
     * ??????????????????
     */
    private OnBleStatusListener mOnBleStatusListener = null;
    /**
     * ?????????????????????
     */
    private boolean mScanStatus = false;

    /**
     * ??????????????????
     */
    private long mConnectBleTimeout = 10 * 1000;
    /**
     * ????????????,????????????30S?????????????????????????????????5???,???????????????????????????????????????
     */
    private volatile int mScanNumber = 0;
    /**
     * ?????????????????????
     */
    private long mFirstScanTime = 0;
    /**
     * ????????????????????????
     */
    private volatile Map<String, BleDevice> mBleDeviceMap = new HashMap<>();
    private volatile Map<String, AdBleDevice> mAdBleDeviceMap = new HashMap<>();

    /**
     * ???????????????uuid
     */
    private UUID[] mScanUUID = null;
    /**
     * ???????????????,??????????????????
     */
    private long mTimeOut = 0;
    /**
     * ???????????????
     */
    private int mConnectMax = 7;

    private Handler mHandler = new Handler(Looper.myLooper()) {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                //??????,????????????
                case STOP_SERVER:
                    try {
                        finish();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                //????????????
                case SCAN_BLE_DEVICE:
                    //????????????
                    stopScan();
                    scanLeDevice(mTimeOut, mScanUUID);
                    break;
                //????????????
                case SCAN_BLE_DEVICE_FREQUENTLY:
                    mScanNumber = 0;
                    XBleL.i(TAG, "?????????????????????");
                    break;
                //????????????
                case STOP_BLE_DEVICE:
                    int timeOut = msg.arg1;
                    if (timeOut > 0) {
                        if (mOnBleScanFilterListener != null) {
                            mOnBleScanFilterListener.onScanComplete();
                        }
                        stopScan();//????????????
                    } else if (timeOut == 0) {
                        //??????????????????,??????????????????,?????????????????????????????????20??????????????????,??????????????????
                        mHandler.removeMessages(SCAN_BLE_DEVICE);
                        mHandler.sendEmptyMessageDelayed(SCAN_BLE_DEVICE, 20 * 60 * 1000);
                    }
                    break;

                //??????????????????,????????????
                case GET_BLE_SERVICE:
                    BluetoothGatt gatt = (BluetoothGatt) msg.obj;
                    if (gatt != null) {
                        gatt.discoverServices();//????????????
                        XBleL.i(TAG, "??????????????????");
                        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
                        mHandler.sendEmptyMessageDelayed(CONNECT_BLE_TIMEOUT, mConnectBleTimeout);
                    }

                    break;

                //??????????????????
                case CONNECT_BLE_TIMEOUT:
                    if (mConnectGatt != null) {
                        String address = mConnectGatt.getDevice().getAddress();
                        XBleL.e(TAG, "????????????:" + mConnectGatt + "||mac:" + address);
                        if (mConnectGatt != null)
                            mConnectGatt.disconnect();
                        if (mConnectGatt != null)
                            mConnectGatt.close();
                        mConnectGatt = null;
                        gattOld = null;
                        runOnMainThread(() -> {
                            if (mOnBleConnectListener != null) {
                                mOnBleConnectListener.onDisConnected(address, XBleStaticConfig.DISCONNECT_CODE_ERR_TIMEOUT);
                            }

                            BleConnectListenerIm.getInstance()
                                    .onDisConnected(mOnBleConnectListener, address, XBleStaticConfig.DISCONNECT_CODE_ERR_TIMEOUT);
                        });
                    } else {
                        XBleL.e(TAG, "??????????????????:mConnectGatt=null");
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
     * ??????startService()??????????????????????????????????????????????????????????????????????????????????????????
     * ??????????????????????????????stopSelf()??????stopService()???????????????????????????????????????????????????????????????????????????
     * ???????????????
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //?????????????????????????????????
//        startForeground(startId);
        return START_STICKY_COMPATIBILITY;//???????????????????????????????????????????????????????????????????????????????????????
    }

    private int mId;
    @DrawableRes
    private int mIcon;
    private String mTitle;
    private Class<?> mActivityClass;

    /**
     * ????????????????????????????????????
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
     * ??????????????????
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
            notification = new Notification.Builder(this, channelId).setContentIntent(pendingIntent) //???????????????????????????
                    .setSmallIcon(mIcon)
                    .setLargeIcon(Icon.createWithResource(this, mIcon))
                    .setContentTitle(mTitle)
                    .setOngoing(true)
                    .build();
        } else {
            notification = new Notification.Builder(this).setContentIntent(pendingIntent) //???????????????????????????
                    .setSmallIcon(mIcon).setContentTitle(mTitle).setOngoing(true).build();
        }
        startForeground(mId, notification);
    }


    /**
     * ??????????????????
     */
    @RequiresPermission(allOf = {Manifest.permission.FOREGROUND_SERVICE})
    public void stopForeground() {
        stopForeground(true);
    }

    //----------------------?????????-------------------

    @Override
    public void onCreate() {
        super.onCreate();
        XBleL.i(TAG, "onCreate");
        //????????????
        initStart();
    }


    /**
     * ?????????????????????
     */
    private void initStart() {
        XBleL.i(TAG, "?????????????????????");

        try {
            // ??????????????????????????????ble
            if (!BleCheckUtils.getInstance().getSupportBluetoothLe(this)) {
                XBleL.e("?????????????????????????????????(This device does not support Bluetooth Low Power)");
                mHandler.sendEmptyMessage(STOP_SERVER);
                return;
            }

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
        } catch (Exception e) {
            e.printStackTrace();
            XBleL.e("?????????????????????????????????(This device does not support Bluetooth Low Power)");
            mHandler.sendEmptyMessage(STOP_SERVER);
            return;
        }

        mBluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) {
                    mBluetoothA2dp = (BluetoothA2dp) proxy;
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.A2DP) {
                    mBluetoothA2dp = null;
                }
            }
        }, BluetoothProfile.A2DP);

        mBluMainService = this;
        mBleDeviceMap.clear();
        mGattCallback = new MyBluetoothGattCallback();
        mConnectMax = XBleConfig.getInstance().getConnectMax();
        bleState();
        deviceAdConnectListener();
    }

    //------------------------????????????-----------------------------------


    /**
     * ??????????????????????????????
     *
     * @param onBleScanFilterListener OnScanFilterListener
     */
    public void setOnBleScanFilterListener(OnBleScanFilterListener onBleScanFilterListener) {
        mOnBleScanFilterListener = onBleScanFilterListener;
    }


    /**
     * ????????????
     * ???????????????????????????????????????
     * ????????????5????????????????????????30s
     *
     * @param timeOut  ????????????,??????(????????????????????????,0??????????????????)
     * @param scanUUID ?????????UUID(???/null???????????????)
     */
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
    public void scanLeDevice(long timeOut, UUID... scanUUID) {
        XBleL.i(TAG, "????????????timeOut=" + timeOut);
        mTimeOut = timeOut;
        mHandler.removeMessages(STOP_BLE_DEVICE);
        if (mScanStatus) {
            XBleL.i(TAG, "??????????????????????????????");
            Message message = Message.obtain();
            message.what = STOP_BLE_DEVICE;
            message.arg1 = (int) timeOut;
            mHandler.sendMessageDelayed(message, timeOut);
            runOnMainThread(() -> {
                if (mOnBleScanFilterListener != null) {
                    mOnBleScanFilterListener.onStartScan();
                }
            });
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            XBleL.e(TAG, "???????????????.");
            bleClose();
            return;
        }
        //?????????,??????????????????????????????????????????
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
                if (mOnBleScanFilterListener != null) {
                    mOnBleScanFilterListener.onScanErr(l);
                }
            });
            return;
        }


        try {
            if (scanUUID != null && scanUUID.length > 0) {
                mScanUUID = scanUUID;
                for (UUID uuid : scanUUID) {
                    if (uuid == null) {
                        mScanUUID = null;
                        break;
                    }
                }
            } else {
                mScanUUID = null;
            }
            //???????????????????????????????????????
            //????????????5????????????????????????30s
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mScanCallback == null)
                    mScanCallback = new MyScanCallback();
                ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                mBluetoothAdapter.getBluetoothLeScanner().startScan(null, settings, mScanCallback);
//                mBluetoothAdapter.startDiscovery(); //??????????????????,????????????
            } else {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
            mScanStatus = true;

            runOnMainThread(() -> {

                if (mOnBleScanFilterListener != null) {
                    mOnBleScanFilterListener.onStartScan();
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
     * ????????????
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * ?????????????????????
     */
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = (device, rssi, scanRecord) -> {
        if (device == null || scanRecord == null)
            return;
        BleBroadcastBean mBle = new BleBroadcastBean(device, rssi, scanRecord);

        if (mScanUUID != null && mScanUUID.length > 0) {
            boolean uuidOk = false;
            List<ParcelUuid> parcelUuids = mBle.getParcelUuids();
            if (parcelUuids == null)
                return;
            for (ParcelUuid uuid : parcelUuids) {
                for (UUID uuid1 : mScanUUID) {
                    if (uuid1 == null || (uuid != null && uuid.toString()
                            .equalsIgnoreCase(uuid1.toString()))) {
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
     * ????????????????????? 5.0??????
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private class MyScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            mScanErr = 0;
            BleBroadcastBean mBle = new BleBroadcastBean(result);
            if (mScanUUID != null && mScanUUID.length > 0) {
                boolean uuidOk = false;
                if (result.getScanRecord() == null)
                    return;
                List<ParcelUuid> parcelUuids = mBle.getParcelUuids();
                if (parcelUuids == null)
                    return;
                for (ParcelUuid uuid : parcelUuids) {
                    for (UUID uuid1 : mScanUUID) {
                        if (uuid1 == null || (uuid != null && uuid.toString()
                                .equalsIgnoreCase(uuid1.toString()))) {
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


        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            XBleL.e(TAG, "????????????:" + errorCode);
            if (mScanErr < 3) {
                mScanErr++;
                stopScan();
                mHandler.removeMessages(SCAN_BLE_DEVICE);
                mHandler.sendEmptyMessageDelayed(SCAN_BLE_DEVICE, 2000);
            } else {
                //?????????????????????????????????
                boolean status = mBluetoothAdapter.disable();
                if (status) {
                    bleClose();
                }
            }
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param mBle ??????
     */
    private synchronized void saveScanData(final BleBroadcastBean mBle) {
        boolean isMeBle = true;
        if (mOnBleScanFilterListener != null) {
            //??????,?????????
            isMeBle = mOnBleScanFilterListener.onBleFilter(mBle);
        }
        if (isMeBle) {
            runOnMainThread(() -> {
                if (mOnBleScanFilterListener != null) {
                    //????????????
                    mOnBleScanFilterListener.onScanBleInfo(mBle);
                }

            });
        }
    }


    //----------------------??????,??????-------------------------------------------------------------

    /**
     * ?????????????????????
     */
    private volatile BluetoothGatt mConnectGatt = null;


    /**
     * @param mAddress mac??????
     */
    public void connectDevice(String mAddress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectBleDevice(mAddress, true, BluetoothDevice.TRANSPORT_AUTO);
        } else {
            connectBleDevice(mAddress, true, 0);
        }
    }

    /**
     * @param mAddress  mac??????
     * @param transport {@link BluetoothDevice#TRANSPORT_AUTO} or {@link BluetoothDevice#TRANSPORT_BREDR} or {@link  BluetoothDevice#TRANSPORT_LE}
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void connectDevice(String mAddress, int transport) {
        connectBleDevice(mAddress, true, BluetoothDevice.TRANSPORT_AUTO);
    }

    /**
     * @param bleValueBean ????????????
     */
    public void connectDevice(BleBroadcastBean bleValueBean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectBleDevice(bleValueBean.getMac(), bleValueBean.isConnectBle(), BluetoothDevice.TRANSPORT_AUTO);
        } else {
            connectBleDevice(bleValueBean.getMac(), bleValueBean.isConnectBle(), 0);
        }
    }


    /**
     * @param bleValueBean ????????????
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void connectDevice(BleBroadcastBean bleValueBean, int transport) {
        connectBleDevice(bleValueBean.getMac(), bleValueBean.isConnectBle(), transport);
    }

    /**
     * ??????????????????
     *
     * @param address    ?????????????????????
     * @param connectBle ??????????????????????????????
     * @param transport  {@link BluetoothDevice#TRANSPORT_AUTO} or {@link BluetoothDevice#TRANSPORT_BREDR} or {@link  BluetoothDevice#TRANSPORT_LE}
     */
    private synchronized void connectBleDevice(String address, boolean connectBle, int transport) {
        if (!mBluetoothAdapter.isEnabled()) {
            XBleL.e(TAG, "???????????????.");
            bleClose();
            return;
        }

        if (mConnectGatt != null) {
            XBleL.e(TAG, "????????????????????????,?????????????????????:" + mConnectGatt.getDevice().getAddress());
            return;
        }

        if (!connectBle) {
            runOnMainThread(() -> {
                if (mOnBleConnectListener != null) {
                    mOnBleConnectListener.onDisConnected(address, XBleStaticConfig.DISCONNECT_CODE_ERR_NO_CONNECT);
                }
                BleConnectListenerIm.getInstance()
                        .onDisConnected(mOnBleConnectListener, address, XBleStaticConfig.DISCONNECT_CODE_ERR_NO_CONNECT);
            });
            return;
        }
        BluetoothDevice device;
        try {
            device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                XBleL.e(TAG, "??????????????????????????????:" + address);
                return;
            }
        } catch (IllegalArgumentException e) {
            XBleL.e(TAG, "???????????????????????????:" + address);
            e.printStackTrace();
            return;
        }
        List<BluetoothDevice> connectList = getSystemConnectDevice();
        if (connectList.size() >= mConnectMax) {
            runOnMainThread(() -> {

                if (mOnBleConnectListener != null) {
                    mOnBleConnectListener.onConnectMaxErr(connectList);
                }
                BleConnectListenerIm.getInstance()
                        .onConnectMaxErr(mOnBleConnectListener, connectList);
            });
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mConnectGatt = device.connectGatt(mBluMainService, false, mGattCallback, transport);//????????????
        } else {
            mConnectGatt = device.connectGatt(mBluMainService, false, mGattCallback);//????????????
        }
        runOnMainThread(() -> {

            if (mOnBleConnectListener != null) {
                mOnBleConnectListener.onConnecting(address);
            }
            BleConnectListenerIm.getInstance().onConnecting(mOnBleConnectListener, address);
        });

        XBleL.i(TAG, "????????????:" + mConnectGatt);
        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
        mHandler.sendEmptyMessageDelayed(CONNECT_BLE_TIMEOUT, mConnectBleTimeout);

    }


    /**
     * ????????????????????????????????????
     *
     * @param mac ???????????????
     * @return ??????????????????
     */
    public boolean getConnectStatus(String mac) {
        BleDevice mConnectBleObject = mBleDeviceMap.get(mac.toUpperCase());
        if (mConnectBleObject != null) {
            return mConnectBleObject.isConnectSuccess();
        }
        return false;
    }


    /**
     * ??????????????????
     *
     * @param mac ????????????
     * @return DeviceObject
     */
    @Nullable
    public BleDevice getBleDevice(String mac) {
        if (mac != null && !mac.isEmpty()) {
            return mBleDeviceMap.get(mac.toUpperCase());
        } else {
            return null;
        }
    }


    @Nullable
    public AdBleDevice getAdBleDevice(String mac) {
        if (mac != null && !mac.isEmpty()) {
            return mAdBleDeviceMap.get(mac.toUpperCase());
        } else {
            return null;
        }
    }


    /**
     * ???????????????????????????
     *
     * @return List<BleDevice>
     */
    public List<BleDevice> getBleDeviceAll() {
        return new ArrayList<>(mBleDeviceMap.values());
    }

    //------------------------------------------------------------------------------------------


    /**
     * ?????????????????????????????????
     */
    private long discoverServicesTime = 0;
    /**
     * ??????ble??????????????????
     */
    private MyBluetoothGattCallback mGattCallback;
    private BluetoothGatt gattOld;

    /**
     * ??????ble??????????????????
     */
    private class MyBluetoothGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            try {
                XBleL.e(TAG, "?????????????????????status:" + status + "||newState:" + newState + "||mac:" + gatt.getDevice()
                        .getAddress());
                mHandler.removeMessages(GET_BLE_SERVICE);
                mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    XBleL.i(TAG, "??????????????????");
                    if (newState == BluetoothProfile.STATE_CONNECTED && gattOld != gatt) {
                        runOnMainThread(() -> {
                            String mac = gatt.getDevice().getAddress();

                            if (mOnBleConnectListener != null) {
                                mOnBleConnectListener.onConnectionSuccess(mac);
                            }
                            BleConnectListenerIm.getInstance()
                                    .onConnectionSuccess(mOnBleConnectListener, mac);
                        });
//                        boolean b = gatt.discoverServices();//????????????
//                        BleLog.i("????????????:" + b);
                        gattOld = gatt;
                        XBleL.i(TAG, "?????????????????????:" + gatt);
                        Message message = Message.obtain();
                        message.what = GET_BLE_SERVICE;
                        message.obj = gatt;
                        mHandler.sendMessageDelayed(message, 100);//??????????????????????????????,????????????????????????????????????
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        //?????????????????????????????????????????????
                        if (System.currentTimeMillis() - discoverServicesTime > 500) {
                            String mAddress = gatt.getDevice().getAddress().toUpperCase();
                            if (mConnectGatt != null && mAddress.equals(mConnectGatt.getDevice()
                                    .getAddress())) {
                                mConnectGatt = null;
                            }
                            discoverServicesTime = System.currentTimeMillis();
                            disconnect(mAddress, status, gatt);
                        } else {
                            XBleL.e(TAG, "????????????????????????");
                        }
                    }
                } else {
                    String mAddress = gatt.getDevice().getAddress().toUpperCase();
                    XBleL.e(TAG, "????????????:" + mAddress);
                    if (mConnectGatt != null && mAddress.equals(mConnectGatt.getDevice()
                            .getAddress()
                            .toUpperCase())) {
                        mConnectGatt = null;
                    }
                    disconnect(mAddress, status, gatt);
                    MyBleDeviceUtils.refreshDeviceCache(gatt);
                }


            } catch (NullPointerException e) {
                XBleL.e(TAG, "??????/????????????:" + e.toString());
                e.printStackTrace();
            }

        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gattOld = null;
                runOnMainThread(() -> {
                    //???????????????
                    discoverServicesTime = 0;
                    mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
                    mHandler.removeMessages(GET_BLE_SERVICE);
                    String mac = gatt.getDevice().getAddress().toUpperCase();
                    synchronized (mBleDeviceMap) {
                        if (mBleDeviceMap.containsKey(mac)) {
                            BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                            if (mConnectBleObject != null) {
                                mConnectBleObject.disconnect(false);
                            }
                        }
                        BleDevice mDevice = new BleDevice(gatt, mac);
                        mBleDeviceMap.put(mac, mDevice);

                        if (mOnBleConnectListener != null) {
                            mOnBleConnectListener.onServicesDiscovered(mac);
                        }
                        BleConnectListenerIm.getInstance()
                                .onServicesDiscovered(mOnBleConnectListener, mac);
                        mConnectGatt = null;

                    }
                });
            } else {
                XBleL.e(TAG, "????????????:??????????????????:");

                String mac = gatt.getDevice().getAddress();
                disconnect(mac, XBleStaticConfig.DISCONNECT_CODE_ERR_SERVICE_FAIL, gatt);
                gatt.disconnect();
                gatt.close();
                MyBleDeviceUtils.refreshDeviceCache(gatt);
                mConnectGatt = null;
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //????????????
            XBleL.i(TAG, "???????????????:onCharacteristicRead:" + status);
            String mac = gatt.getDevice().getAddress().toUpperCase();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.readData(characteristic);
            }

        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //????????????(???????????????,??????,???????????????)
            XBleL.i(TAG, "???????????????:onCharacteristicWrite:" + status);
            String mac = gatt.getDevice().getAddress().toUpperCase();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
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
                BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.descriptorWriteOk(descriptor);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //?????????????????????
            String mac = gatt.getDevice().getAddress().toUpperCase();
            BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
            if (mConnectBleObject != null)
                mConnectBleObject.notifyData(characteristic);
        }


        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            //??????????????????
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String mac = gatt.getDevice().getAddress().toUpperCase();
                BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.setRssi(rssi);

            }
        }


        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String mac = gatt.getDevice().getAddress().toUpperCase();
                BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.OnMtu(mtu);

            }
        }

        public void onConnectionUpdated(BluetoothGatt gatt, int interval, int latency, int timeout, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String mac = gatt.getDevice().getAddress().toUpperCase();
                BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                if (mConnectBleObject != null)
                    mConnectBleObject.getConnectionUpdated(interval, latency, timeout);

            }
        }

    }
    //----------------------??????-------------------------------------------------------------


    /**
     * ????????????,?????????????????????,??????gatt,????????????
     *
     * @param mac  mac??????
     * @param code ?????????
     * @param gatt BluetoothGatt
     */
    private void disconnect(final String mac, int code, BluetoothGatt gatt) {
        if (gatt != null)
            gatt.close();
        runOnMainThread(() -> {
            XBleL.i(TAG, "??????????????????:" + code);

            BleDevice bleDevice = mBleDeviceMap.get(mac);
            if (bleDevice != null) {
                bleDevice.onDisConnected();

                if (mOnBleConnectListener != null) {
                    mOnBleConnectListener.onDisConnected(mac, code);
                }
                BleConnectListenerIm.getInstance().onDisConnected(mOnBleConnectListener, mac, code);
            }
            AdBleDevice adBleDevice = mAdBleDeviceMap.get(mac);
            if (adBleDevice != null) {
                adBleDevice.disconnect();
                if (mOnBleAdvertiserConnectListener != null) {
                    mOnBleAdvertiserConnectListener.onAdDisConnected(mac, code);
                }
            }
            removeConnect(mac);
        });

        discoverServicesTime = 0;
    }


    /**
     * ????????????????????????
     *
     * @param mac ?????????????????????
     */
    public void removeConnect(String mac) {
        if (mBleDeviceMap != null) {
            mBleDeviceMap.remove(mac);
        }
        if (mAdBleDeviceMap != null) {
            mAdBleDeviceMap.remove(mac);
        }
    }

    /**
     * ????????????mac?????????????????????,????????????????????????????????????
     *
     * @param mac mac??????
     */
    public void disconnect(String mac) {
        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
        if (mConnectGatt != null) {
            mConnectGatt.disconnect();
            if (mConnectGatt != null)
                mConnectGatt.close();
            mConnectGatt = null;
        }
        if (mBleDeviceMap != null) {
            synchronized (mBleDeviceMap) {
                if (mBleDeviceMap != null) {
                    BleDevice mConnectBleObject = mBleDeviceMap.get(mac);
                    if (mConnectBleObject != null)
                        mConnectBleObject.disconnect();
                }
            }
        }
    }


    /**
     * ????????????????????????,????????????????????????
     */
    public void disconnectAll() {
        XBleL.i(TAG, "disconnectAll:????????????????????????");
        mHandler.removeMessages(CONNECT_BLE_TIMEOUT);
        if (mConnectGatt != null) {
            mConnectGatt.disconnect();
            if (mConnectGatt != null)
                mConnectGatt.close();
            mConnectGatt = null;
        }
        if (mBleDeviceMap != null) {
            synchronized (mBleDeviceMap) {
                if (mBleDeviceMap != null) {
                    for (String mAddress : mBleDeviceMap.keySet()) {
                        BleDevice mConnectBleObject = mBleDeviceMap.get(mAddress);
                        if (mConnectBleObject != null)
                            mConnectBleObject.disconnect();
                    }
                    mBleDeviceMap.clear();
                }
            }
        }
    }


    //---------------------??????------------------------------------------------------------
    /**
     * ?????????????????????
     */
    private BleStateReceiver mBleStateReceiver = null;

    /**
     * ??????????????????(?????????????????????????????????)
     */
    private void bleState() {
        try {
            if (mBleStateReceiver == null) {
                IntentFilter intentFilter = new IntentFilter();
                mBleStateReceiver = new BleStateReceiver();
                intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//??????????????????
                intentFilter.addAction(BluetoothDevice.ACTION_FOUND);//??????????????????
                intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);//???????????????????????????
                intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//???????????????????????????
                intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//?????????
                intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//????????????
                intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//????????????
                registerReceiver(mBleStateReceiver, intentFilter);
                XBleL.i(TAG, "??????????????????");
            }
        } catch (IllegalArgumentException e) {
            XBleL.e(TAG, "??????????????????:" + e.getMessage());
            e.printStackTrace();
        }
    }

    private class BleStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        XBleL.i(TAG, "STATE_OFF ??????????????????");
                        bleClose();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        XBleL.i(TAG, "STATE_TURNING_OFF ????????????????????????");

                        break;
                    case BluetoothAdapter.STATE_ON:
                        XBleL.i(TAG, "STATE_ON ??????????????????");
                        bleOpen();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        XBleL.d(TAG, "STATE_TURNING_ON ????????????????????????");
                        break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                //???A2DP?????????
//                if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE) {
//                    XBleL.i("??????????????????:" + device.getName() + " ??????:" + device.getAddress() + " ???????????????" + device.getBondState() + " ?????????????????????:" + device.getType());
//
//                }
//                }
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                XBleL.i("???????????????????????????");
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                XBleL.i("???????????????????????????");
            } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int status = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (status == BluetoothDevice.BOND_BONDED) {
                    XBleL.i("?????????");
                } else if (status == BluetoothDevice.BOND_NONE) {
                    XBleL.i("?????????");
                } else if (status == BluetoothDevice.BOND_BONDING) {
                    XBleL.i("?????????");
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                XBleL.i(TAG, "??????????????????:" + device.getAddress());
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                XBleL.i(TAG, "??????????????????:" + device.getAddress());
            }
        }
    }


    /**
     * ??????a2dp??????
     *
     * @param device BluetoothDevice
     */
    private void connectA2dp(BluetoothDevice device) {
        if (mBluetoothA2dp == null || device == null) {
            return;
        }
//        setPriority(bluetoothDevice, 100);
        try {

            if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                //btDevice.createBond();
                try {
                    Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
                    createBondMethod.invoke(device);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            while (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                SystemClock.sleep(100);
            }

            Method connectMethod = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
            connectMethod.invoke(mBluetoothA2dp, device);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????a2dp??????
     *
     * @param device BluetoothDevice
     */
    public void disconnectA2dp(BluetoothDevice device) {
        if (mBluetoothA2dp == null || device == null) {
            return;
        }
        try {
            Method method = BluetoothA2dp.class.getMethod("disconnect", BluetoothDevice.class);
            method.invoke(mBluetoothA2dp, device);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }


    //-------------------------------- ??????????????????????????????---------------------------------------------------


    private void bleOpen() {
        XBleL.i(TAG, "????????????");
        runOnMainThread(() -> {
            if (mOnBleConnectListener != null) {
                mOnBleConnectListener.bleOpen();
            } else if (mOnBleStatusListener != null) {
                mOnBleStatusListener.bleOpen();
            }
            BleConnectListenerIm.getInstance().bleOpen(mOnBleConnectListener);
        });

    }

    private void bleClose() {
        XBleL.i(TAG, "????????????");
        stopScan();
        runOnMainThread(() -> {

            if (mOnBleConnectListener != null) {
                mOnBleConnectListener.bleClose();
            } else if (mOnBleStatusListener != null) {
                mOnBleStatusListener.bleClose();
            }
            BleConnectListenerIm.getInstance().bleClose(mOnBleConnectListener);
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
        XBleL.i(TAG, "onDestroy");
    }

    /**
     * ??????????????????
     */
    public final void finish() {
        XBleL.i(TAG, "??????????????????");
        //????????????
        stopScan();
        disconnectAll();//??????????????????
        mBleManager = null;
        mOnBleConnectListener = null;
        if (mBleStateReceiver != null) {
            unregisterReceiver(mBleStateReceiver);
            XBleL.i(TAG, "??????????????????");
            mBleStateReceiver = null;
        }
        stopSelf();
    }


    /**
     * ?????????????????????????????????????????????????????????BleDevice??????
     */
    public void autoConnectSystemBle() {
        List<BluetoothDevice> connectedDevices = mBleManager.getConnectedDevices(BluetoothProfile.GATT);
        for (BluetoothDevice connectedDevice : connectedDevices) {
            String address = connectedDevice.getAddress();
            BleDevice bleDevice = mBleDeviceMap.get(address);
            if (bleDevice != null) {
                continue;
            }
//            BleLog.i("???????????????,XBle??????????????????BleDevice??????"+address);
            connectedDevice.connectGatt(this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        runOnMainThread(() -> {
                            String mac = gatt.getDevice().getAddress().toUpperCase();
//                            BleLog.i("????????????????????????:" + mac);
                            synchronized (mBleDeviceMap) {
                                BleDevice bleDevice = mBleDeviceMap.get(address);
                                if (bleDevice != null) {
                                    //?????????,???????????????
                                    return;
                                }
                                BleDevice mDevice = new BleDevice(gatt, mac);
                                mBleDeviceMap.put(mac, mDevice);

                            }
                        });
                    }
                }
            });
        }
    }


//--------------------------------BLE?????? start-------------------------------------------


    private volatile LinkedList<BluetoothGattService> mBluetoothGattServiceList = new LinkedList<>();
    private OnBleAdvertiser mOnBleAdvertiser = null;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startAdvertiseData(AdBleBroadcastBean adBleValueBean, OnBleAdvertiserConnectListener listener) {
        deviceAdConnectListener();
        if (mBluetoothGattServer != null) {
            mBluetoothGattServer.clearServices();
        }
        stopAdvertiseData();
        BluetoothLeAdvertiser bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        if (bluetoothLeAdvertiser == null) {
            if (listener != null) {
                listener.onStartAdFailure(-1);
            }
            return;
        }
        mOnBleAdvertiser = new OnBleAdvertiser(listener);
        bluetoothLeAdvertiser.startAdvertising(adBleValueBean.getAdvertiseSettings(), adBleValueBean
                .getAdvertiseData(), mOnBleAdvertiser);
        if (adBleValueBean.getAdvertiseSettings().isConnectable()) {
            mBluetoothGattServiceList.clear();
            mBluetoothGattServiceList.addAll(adBleValueBean.getBluetoothGattServiceList());
            BluetoothGattService bluetoothGattService = mBluetoothGattServiceList.getLast();
            mBluetoothGattServer.addService(bluetoothGattService);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopAdvertiseData() {
        BluetoothLeAdvertiser bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        if (mOnBleAdvertiser != null) {
            mOnBleAdvertiser.setStartStatus(false);
        }
        if (bluetoothLeAdvertiser != null) {
            if (mOnBleAdvertiser != null) {
                bluetoothLeAdvertiser.stopAdvertising(mOnBleAdvertiser);
                mOnBleAdvertiser.onStartSuccess(null);
            }
        } else {
            if (mOnBleAdvertiser != null) {
                mOnBleAdvertiser.onStartFailure(-1);
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static class OnBleAdvertiser extends AdvertiseCallback {
        private OnBleAdvertiserConnectListener mOnBleAdvertiserListener;
        /**
         * ??????????????????
         */
        private boolean startStatus = true;

        public void setStartStatus(boolean startStatus) {
            this.startStatus = startStatus;
        }

        public OnBleAdvertiser(OnBleAdvertiserConnectListener onBleAdvertiserListener) {
            startStatus = true;
            mOnBleAdvertiserListener = onBleAdvertiserListener;
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            if (mOnBleAdvertiserListener != null) {
                if (startStatus) {
                    mOnBleAdvertiserListener.onStartAdSuccess(settingsInEffect);
                } else {
                    mOnBleAdvertiserListener.onStopAdSuccess();
                }
            }

        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            if (mOnBleAdvertiserListener != null) {
                if (startStatus) {
                    mOnBleAdvertiserListener.onStartAdFailure(errorCode);
                } else {
                    mOnBleAdvertiserListener.onStopAdFailure(errorCode);
                }
            }
        }
    }


    /**
     * ??????????????????????????????,????????????APP???????????????
     */
    public List<BluetoothDevice> getSystemConnectDevice() {
        return mBleManager.getConnectedDevices(BluetoothProfile.GATT);
    }

    /**
     * ?????????????????????????????????
     */
    private BluetoothGattServer mBluetoothGattServer;

    /**
     * ????????????
     */
    private volatile boolean mAutoMonitorSystemConnectBle = false;

    public void setAutoMonitorSystemConnectBle(boolean autoMonitorSystemConnectBle) {
        mAutoMonitorSystemConnectBle = autoMonitorSystemConnectBle;
    }

    /**
     * ??????ble????????????,????????????????????????????????????????????????????????????
     */
    private void deviceAdConnectListener() {
        if (mBluetoothGattServer != null)
            mBluetoothGattServer.close();
        //???????????????
        mBluetoothGattServer = mBleManager.openGattServer(this, new BluetoothGattServerCallback() {

            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
//                XBleL.i("??????????????????:onConnectionStateChange:" + device.getAddress() + " status:" + status + " newState" + newState);
                //????????????
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    //???????????????????????????
                    if (mConnectGatt == null && mAutoMonitorSystemConnectBle) {
                        //????????????????????????
                        String address = device.getAddress();
                        BleDevice bleDevice = mBleDeviceMap.get(address);
                        if (bleDevice != null) {
                            //?????????
                            return;
                        }
                        connectDevice(address);//??????????????????,?????????????????????

                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    //????????????
                    String mAddress = device.getAddress().toUpperCase();
                    if (mConnectGatt != null && mAddress.equals(mConnectGatt.getDevice()
                            .getAddress())) {
                        mConnectGatt = null;
                    }
                    disconnect(mAddress, status, null);

                }
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                super.onServiceAdded(status, service);
                XBleL.i("??????????????????:onServiceAdded:" + (status == BluetoothGatt.GATT_SUCCESS));
                //????????????
                if ((status == BluetoothGatt.GATT_SUCCESS)) {
                    mBluetoothGattServiceList.removeLast();
                    if (mBluetoothGattServiceList.size() > 0)
                        mBluetoothGattServer.addService(mBluetoothGattServiceList.getLast());
                }

            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                XBleL.i("??????????????????:onCharacteristicReadRequest");
                AdBleDevice adBleDevice = newAdBleDevice(device);
                adBleDevice.onCharacteristicReadRequest(device, requestId, offset, characteristic);


            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                XBleL.i("??????????????????:onCharacteristicWriteRequest:" + device.getAddress());
                AdBleDevice adBleDevice = newAdBleDevice(device);
                adBleDevice.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                //?????????
//                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);


            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
                XBleL.i("??????????????????:onDescriptorReadRequest");
                AdBleDevice adBleDevice = newAdBleDevice(device);
                //??????????????????????????????????????????
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
                XBleL.i("??????????????????:onDescriptorWriteRequest");
                AdBleDevice adBleDevice = newAdBleDevice(device);
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                adBleDevice.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                super.onExecuteWrite(device, requestId, execute);
                XBleL.i("??????????????????:onExecuteWrite");
                AdBleDevice adBleDevice = newAdBleDevice(device);
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                super.onNotificationSent(device, status);
                XBleL.i("??????????????????:onNotificationSent:" + status);
                AdBleDevice adBleDevice = newAdBleDevice(device);
                adBleDevice.onNotificationSent(device, status);
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                super.onMtuChanged(device, mtu);
                XBleL.i("??????????????????:onMtuChanged:" + mtu);
                AdBleDevice adBleDevice = newAdBleDevice(device);
                adBleDevice.onMtuChangedRequest(device, mtu);
            }


        });


    }


    /**
     * ??????AdBleDevice??????
     *
     * @param device BluetoothDevice
     * @return AdBleDevice
     */
    private synchronized AdBleDevice newAdBleDevice(BluetoothDevice device) {
        String address = device.getAddress();
        AdBleDevice adBleDevice1 = mAdBleDeviceMap.get(address);
        if (adBleDevice1 == null) {
            AdBleDevice adBleDevice = new AdBleDevice(device, mBluetoothGattServer);
            mAdBleDeviceMap.put(address, adBleDevice);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopAdvertiseData();
            }
            runOnMainThread(() -> {
                if (mOnBleAdvertiserConnectListener != null) {
                    mOnBleAdvertiserConnectListener.onAdConnectionSuccess(address);
                }
            });
            return adBleDevice;
        }
        return adBleDevice1;

    }


//--------------------------------BLE?????? end-------------------------------------------

//--------------------------------set/get start-------------------------------------------

    /**
     * ?????????????????????
     */
    public boolean isScanStatus() {
        return mScanStatus;
    }

    public void setConnectMax(int connectMax) {
        mConnectMax = connectMax;
    }

    /**
     * ????????????????????????
     */
    public void setConnectBleTimeout(long connectBleTimeout) {
        this.mConnectBleTimeout = connectBleTimeout;
    }


    public void setOnBleConnectListener(OnBleConnectListener onBleConnectListener) {
        mOnBleConnectListener = onBleConnectListener;
    }


    public void setOnBleAdvertiserConnectListener(OnBleAdvertiserConnectListener onBleAdvertiserConnectListener) {
        mOnBleAdvertiserConnectListener = onBleAdvertiserConnectListener;
    }

    public void setOnBleStatusListener(OnBleStatusListener onBleStatusListener) {
        mOnBleStatusListener = onBleStatusListener;
    }
}

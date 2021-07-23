package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.xing.xblelibrary.utils.BleBroadcastUtils;
import com.xing.xblelibrary.utils.BleLog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 蓝牙广播内容bean<br>
 * 发出广播的中心设备
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AdBleValueBean {

    private static String TAG = AdBleValueBean.class.getName();
    private BluetoothDevice mDevice;
    private byte[] mScanRecord;
    private int mAdvertiseMode= AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
    private String mName = "Name";
    private String mac;
    private List<byte[]> manufacturerData;
    private List<ParcelUuid> mParcelUuids;
    private BleBroadcastUtils mBleBroadcastUtils;
    private Map<ParcelUuid, byte[]> mServiceData = new ArrayMap<ParcelUuid, byte[]>();

    public AdBleValueBean(BluetoothDevice device, int rssi, byte[] scanRecord, Map<String, String> map) {
        this.mName = device.getName();
        mDevice = device;
        mRssi = rssi;
        mScanRecord = scanRecord;
        mac = device.getAddress();
        mBleBroadcastUtils = new BleBroadcastUtils(mScanRecord);
        mParcelUuids = mBleBroadcastUtils.getServiceUuids();
        boolean init = init(mac);
        if (!init) {
            BleLog.e("搜索到的设备初始化异常");
        }
    }

    public void addServer(String serverUuid,Characteristic ){

    }








}



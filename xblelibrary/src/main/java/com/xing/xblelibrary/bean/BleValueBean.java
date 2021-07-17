package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.ParcelUuid;

import com.xing.xblelibrary.utils.BleBroadcastUtils;
import com.xing.xblelibrary.utils.BleLog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * 蓝牙广播内容bean<br>
 * 搜索到的设备
 */
public class BleValueBean {
    private static String TAG = BleValueBean.class.getName();
    private BluetoothDevice mDevice;
    private byte[] mScanRecord;
    private int mRssi;
    private String mName = "Name";
    private String mac;
    private List<byte[]> manufacturerData;
    private List<ParcelUuid> mParcelUuids;
    private BleBroadcastUtils mBleBroadcastUtils;

    public BleValueBean(BluetoothDevice device, int rssi, byte[] scanRecord, Map<String, String> map) {
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

    public BleValueBean(BluetoothDevice device, int rssi, byte[] scanRecord) {
        this(device, rssi, scanRecord, null);

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public BleValueBean(ScanResult scanResult) {
        this(scanResult, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public BleValueBean(ScanResult scanResult, Map<String, String> map) {
        mDevice = scanResult.getDevice();
        mac = scanResult.getDevice().getAddress();
        mRssi = scanResult.getRssi();
        ScanRecord scanRecord = scanResult.getScanRecord();
        if (scanRecord != null) {
            mName = scanRecord.getDeviceName();
            mScanRecord = scanRecord.getBytes();
            mBleBroadcastUtils = new BleBroadcastUtils(mScanRecord);
            mParcelUuids = mBleBroadcastUtils.getServiceUuids();
            boolean init = init(mac);
            if (!init) {
//                BleLog.i("搜索到设备自定义厂商数据异常");
            }

        }
    }


    /**
     * 初始化数据
     */
    public boolean init(String mac) {
        if (mBleBroadcastUtils == null) {
            BleLog.e(TAG, "BleBroadcastUtils未初始化");
            return false;
        }
        List<byte[]> mDatas = mBleBroadcastUtils.getManufacturerSpecificData();
//        byte[] moveData = mBleBroadcastUtils.getManufacturerByteMove();
//        BleLog.i(TAG, "更多的厂商自定义数据:" + mac + "||" + BleStrUtils.byte2HexStr(moveData));
        manufacturerData = mDatas;
        if (mDatas == null || mDatas.isEmpty()) {
            return false;
        }
        return true;
    }


    public BluetoothDevice getDevice() {
        return mDevice;
    }


    public byte[] getScanRecord() {
        return mScanRecord;
    }

    public int getRssi() {
        return mRssi;
    }

    public void setRssi(int rssi) {
        mRssi = rssi;
    }

    public String getName() {
        return mName;
    }

    public String getMac() {
        return mac;
    }


    public List<byte[]> getManufacturerDataList() {
        return manufacturerData;
    }

    @Nullable
    public byte[] getManufacturerData() {
        if (manufacturerData.isEmpty()) {
            return null;
        }
        return manufacturerData.get(0);
    }


    public List<ParcelUuid> getParcelUuids() {
        return mParcelUuids;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof BleValueBean) {
            return equals((BleValueBean) obj);
        } else if (obj instanceof String) {
            return equals((String) obj);
        }
        return super.equals(obj);
    }


    /**
     * 通过地址和对象判断两个设备是否为同一个设备
     *
     * @param mBleValueBean BleValueBean
     * @return true是, false不是
     */
    private boolean equals(BleValueBean mBleValueBean) {
        BluetoothDevice mNewDevice = mBleValueBean.getDevice();
        if (mDevice != null) {
            if (mDevice == mBleValueBean.getDevice() || equals(mNewDevice.getAddress())) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }


    /**
     * 通过地址判断两个设备是否为同一个设备
     *
     * @param mBleAddress 蓝牙物理地址
     * @return true是, false不是
     */
    private boolean equals(String mBleAddress) {
        if (mDevice != null) {
            if (mDevice.getAddress().equals(mBleAddress)) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }


    /**
     * 判断广播中是否包含某个uuid
     */
    private boolean contains(UUID uuid) {
        if (mParcelUuids == null || mParcelUuids.isEmpty()) {
            return false;
        }

        for (ParcelUuid parcelUuid : mParcelUuids) {
            if (parcelUuid.toString().equalsIgnoreCase(uuid.toString())) {
                return true;
            }
        }
        return false;
    }


}



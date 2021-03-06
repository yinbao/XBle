package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.xing.xblelibrary.utils.BleBroadcastUtils;
import com.xing.xblelibrary.utils.XBleL;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 蓝牙广播内容bean<br>
 * 搜索到的外围设备
 */
public class BleBroadcastBean {
    private static String TAG = BleBroadcastBean.class.getName();
    private BluetoothDevice mDevice;
    private byte[] mScanRecord;
    private int mRssi;
    private String mName = "Name";
    private String mac;
    private List<byte[]> manufacturerData;
    private List<ParcelUuid> mParcelUuids;
    private BleBroadcastUtils mBleBroadcastUtils;
    /**
     * 是否允许连接
     */
    private boolean mConnectBle =true;

    public BleBroadcastBean(BluetoothDevice device, int rssi, byte[] scanRecord) {
        this.mName = device.getName();
        mDevice = device;
        mRssi = rssi;
        mScanRecord = scanRecord;
        mac = device.getAddress();
        mBleBroadcastUtils = new BleBroadcastUtils(mScanRecord);
        mParcelUuids = mBleBroadcastUtils.getServiceUuids();
        boolean init = init();
        if (!init) {
            XBleL.e("搜索到的设备初始化异常");
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public BleBroadcastBean(ScanResult scanResult) {
        mDevice = scanResult.getDevice();
        mac = scanResult.getDevice().getAddress();
        mRssi = scanResult.getRssi();
        ScanRecord scanRecord = scanResult.getScanRecord();
        if (scanRecord != null) {
            mName = scanRecord.getDeviceName();
            mScanRecord = scanRecord.getBytes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //当前广播是否允许连接
                mConnectBle =scanResult.isConnectable();
            }
            mBleBroadcastUtils = new BleBroadcastUtils(mScanRecord);
            mParcelUuids = mBleBroadcastUtils.getServiceUuids();
            boolean init = init();

        }
    }


    /**
     * 初始化数据
     */
    public boolean init() {
        if (mBleBroadcastUtils == null) {
            XBleL.e(TAG, "BleBroadcastUtils未初始化");
            return false;
        }
        List<byte[]> mDatas = mBleBroadcastUtils.getManufacturerSpecificData();
        manufacturerData = mDatas;
        return mDatas != null && !mDatas.isEmpty();
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

    public String getName() {
        return mName;
    }

    public String getMac() {
        return mac;
    }

    public void setRssi(int rssi) {
        mRssi = rssi;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public boolean isConnectBle() {
        return mConnectBle;
    }

    public List<byte[]> getManufacturerDataList() {
        return manufacturerData;
    }

    @Nullable
    public byte[] getManufacturerFirstData() {
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
        if (obj instanceof BleBroadcastBean) {
            return equals((BleBroadcastBean) obj);
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
    private boolean equals(BleBroadcastBean mBleValueBean) {
        BluetoothDevice mNewDevice = mBleValueBean.getDevice();
        if (mDevice != null) {
            return mDevice == mBleValueBean.getDevice() || equals(mNewDevice.getAddress());
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
            return mDevice.getAddress().equals(mBleAddress);
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


    @Override
    public String toString() {
        return "BleBroadcastBean{" + "mDevice=" + mDevice + ", mScanRecord=" + Arrays
                .toString(mScanRecord) + ", mRssi=" + mRssi + ", mName='" + mName + '\'' + ", mac='" + mac + '\'' + ", manufacturerData=" + manufacturerData + ", mParcelUuids=" + mParcelUuids + ", " +
                "mBleBroadcastUtils=" + mBleBroadcastUtils + ", mConnectable=" + mConnectBle + '}';
    }
}



package com.xing.xblelibrary.utils;

import android.os.ParcelUuid;

import com.xing.xblelibrary.bean.BluetoothUuid;

import java.util.ArrayList;
import java.util.List;

/**
 * xing<br>
 * 2021/7/17<br>
 * 广播解析工具类
 */
public class BleBroadcastUtils {
    private static final int DATA_TYPE_FLAGS = 0x01;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    private static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    private static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    private static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    private static final int DATA_TYPE_SERVICE_DATA_16_BIT = 0x16;
    private static final int DATA_TYPE_SERVICE_DATA_32_BIT = 0x20;
    private static final int DATA_TYPE_SERVICE_DATA_128_BIT = 0x21;
    private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;
    private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA_MOVE = 0xFE;
    private List<byte[]> mManufacturerByte;
    private List<ParcelUuid> mServiceUuids;

    public BleBroadcastUtils(byte[] scanRecord) {
        this(scanRecord, -1);
    }

    public BleBroadcastUtils(byte[] scanRecord, int manufacturerId) {
        mManufacturerByte=new ArrayList<>();
        getBleData(scanRecord, manufacturerId);
    }

    // Helper method to extract bytes from byte array.
    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }


    public List<byte[]> getManufacturerSpecificData() {
        return mManufacturerByte;

    }


    public List<ParcelUuid> getServiceUuids() {
        return mServiceUuids;
    }

    private void getBleData(byte[] scanRecord, int id) {
        int currentPos = 0;
        mServiceUuids = new ArrayList<>();
        try {
            while (currentPos < scanRecord.length) {
                int length = scanRecord[currentPos++] & 0xFF;
                if (length == 0)
                    break;
                int dataLength = length - 1;
                int fieldType = scanRecord[currentPos++] & 0xFF;

                switch (fieldType) {
                    case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
                    case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_16_BIT, mServiceUuids);
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
                    case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_32_BIT, mServiceUuids);
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
                    case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_128_BIT, mServiceUuids);
                        break;


                    case DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
                        if (id == -1) {
                            byte[] bytes = extractBytes(scanRecord, currentPos, dataLength);
                            mManufacturerByte.add(bytes);
                        } else {
                            int manufacturerId = ((scanRecord[currentPos + 1] & 0xFF) << 8) + (scanRecord[currentPos] & 0xFF);
                            if (manufacturerId == id) {
                                byte[] bytes = extractBytes(scanRecord, currentPos, dataLength);
                                mManufacturerByte.add(bytes);
                            } else {
                                currentPos += dataLength;
                            }
                        }
                        break;
                    default:
                        break;
                }
                currentPos += dataLength;
            }
        } catch (Exception e) {
            mServiceUuids = null;
            mManufacturerByte = null;
            e.printStackTrace();
        }
        if (mServiceUuids!=null&&mServiceUuids.isEmpty()) {
            mServiceUuids = null;
        }
    }


    // Parse service UUIDs.
    private static int parseServiceUuid(byte[] scanRecord, int currentPos, int dataLength, int uuidLength, List<ParcelUuid> serviceUuids) {
        while (dataLength > 0) {
            byte[] uuidBytes = extractBytes(scanRecord, currentPos, uuidLength);
            serviceUuids.add(BluetoothUuid.parseUuidFrom(uuidBytes));
            dataLength -= uuidLength;
            currentPos += uuidLength;
        }
        return currentPos;
    }

}

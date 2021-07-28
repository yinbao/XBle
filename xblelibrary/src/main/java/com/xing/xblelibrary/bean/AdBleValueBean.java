package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Build;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.xing.xblelibrary.config.BleConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.RequiresApi;

/**
 * 蓝牙广播内容bean<br>
 * 发出广播的中心设备
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AdBleValueBean {

    private List<BluetoothGattService> mBluetoothGattServiceList;

    private AdvertiseSettings mAdvertiseSettings = null;
    private AdvertiseData mAdvertiseData = null;

    private AdBleValueBean(AdvertiseSettings advertiseSettings, AdvertiseData advertiseData, Map<String, AdGattService> gattServiceMap) {
        mAdvertiseSettings = advertiseSettings;
        mAdvertiseData = advertiseData;
        if (gattServiceMap != null) {
            Collection<AdGattService> values = gattServiceMap.values();
            mBluetoothGattServiceList = new ArrayList<>();
            for (AdGattService value : values) {
                mBluetoothGattServiceList.add(value.getBluetoothGattService());
            }
        }
    }


    public List<BluetoothGattService> getBluetoothGattServiceList() {
        return mBluetoothGattServiceList;
    }

    public AdvertiseSettings getAdvertiseSettings() {
        return mAdvertiseSettings;
    }

    public AdvertiseData getAdvertiseData() {
        return mAdvertiseData;
    }

    public static Builder newBuilder() {
        return new Builder();
    }


    public static class Builder {

        /**
         * 广播模式: 低功耗,平衡,低延迟
         */
        private int mAdvertiseMode;
        /**
         * 发射功率级别: 极低,低,中,高
         */
        private int mTxPowerLevel;
        /**
         * 能否连接,广播分为可连接广播和不可连接广播
         */
        private boolean mConnectable;
        /**
         * 广播超时时间
         */
        private int mTimeoutMillis;
        /**
         * 广播中是否包含名称
         */
        private boolean mIncludeDeviceName;
        /**
         * 广播中是否包含传输速率
         */
        private boolean mIncludeTxPowerLevel;
        /**
         * 设置自定义厂商数据
         */
        private Map<Integer, byte[]> mManufacturerData;
        /**
         * 设置广播的服务uuid
         */
        private List<String> mServerUuid;
        /**
         * 设置广播的服务uuid,内容
         */
        private Map<String, byte[]> mServiceUuidData;
        private AdvertiseSettings.Builder settingsBuilder;
        private AdvertiseData.Builder dataBuilder;
        /**
         * 连接后提供的gatt服务
         */
        private Map<String, AdGattService> mGattServiceMap;

        public Builder() {
            mManufacturerData=new HashMap<>();
            mGattServiceMap=new HashMap<>();
            mServiceUuidData=new ArrayMap<>();
            mServerUuid=new ArrayList<>();
            settingsBuilder = new AdvertiseSettings.Builder();
            mAdvertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;//广播模式: 低功耗,平衡,低延迟
            mTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;//发射功率级别: 极低,低,中,高
            mConnectable = true;//能否连接,广播分为可连接广播和不可连接广播
            mTimeoutMillis = 0;
            dataBuilder = new AdvertiseData.Builder();
            mIncludeDeviceName = true;//设置广播中是否包含名称
            mIncludeTxPowerLevel = true;//设置广播中是否包含传输速率
//            dataBuilder.addManufacturerData(0,null);//设置自定义厂商数据
//            dataBuilder.addServiceUuid(null);//设置广播的服务uuid
//            dataBuilder.addServiceData(null, null);//设置广播的服务uuid,内容

        }


        /**
         * 默认:低功耗
         * 广播模式: 低功耗,平衡,低延迟
         *
         * @param advertiseMode {@link AdvertiseSettings#ADVERTISE_MODE_LOW_POWER,AdvertiseSettings#ADVERTISE_MODE_BALANCED,AdvertiseSettings#ADVERTISE_MODE_LOW_LATENCY}
         */
        public Builder setAdvertiseMode(int advertiseMode) {
            mAdvertiseMode = advertiseMode;
            return this;
        }

        /**
         * 默认:高
         * 发射功率级别: 极低,低,中,高
         *
         * @param txPowerLevel {@link AdvertiseSettings#ADVERTISE_TX_POWER_ULTRA_LOW,AdvertiseSettings#ADVERTISE_TX_POWER_LOW,AdvertiseSettings#ADVERTISE_TX_POWER_MEDIUM,AdvertiseSettings#ADVERTISE_TX_POWER_HIGH}
         */
        public Builder setTxPowerLevel(int txPowerLevel) {
            mTxPowerLevel = txPowerLevel;
            return this;
        }

        /**
         * 默认可连接
         *
         * @param connectable 设置是否可以连接
         */
        public Builder setConnectable(boolean connectable) {
            mConnectable = connectable;
            return this;

        }

        /**
         * 设置广播超时时间
         *
         * @param timeoutMillis 0代表不超时,范围{0-180000 ms}
         */
        public Builder setTimeoutMillis(int timeoutMillis) {
            mTimeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * 设置广播中是否显示蓝牙名称,默认为手机蓝牙名称,不支持修改
         *
         * @param includeDeviceName 广播中是否显示蓝牙名称
         */
        public Builder setIncludeDeviceName(boolean includeDeviceName) {
            mIncludeDeviceName = includeDeviceName;
            return this;
        }

        /**
         * 设置广播中是否包含发射功率级别
         *
         * @param includeTxPowerLevel 是否显示
         */
        public Builder setIncludeTxPowerLevel(boolean includeTxPowerLevel) {
            mIncludeTxPowerLevel = includeTxPowerLevel;
            return this;
        }

        /**
         * 设置广播中的自定义厂商数据
         *
         * @param manufacturerId   厂商id
         * @param manufacturerData 厂商数据
         */
        public Builder addManufacturerData(int manufacturerId, byte[] manufacturerData) {

            mManufacturerData.put(manufacturerId, manufacturerData);
            return this;
        }


        /**
         * 设置广播的uuid
         *
         * @param serviceUuid uuid
         */
        public Builder addAdServiceUuid(String serviceUuid) {

            if (!mServerUuid.contains(serviceUuid)) {
                mServerUuid.add(serviceUuid);
            }
            return this;
        }

        /**
         * 设置广播中 16-bit 服务UUID的服务数据
         *
         * @param serviceUuid 16-bit UUID
         * @param serviceData 服务数据
         */
        public Builder addServiceUuidData(String serviceUuid, byte[] serviceData) {

            mServiceUuidData.put(serviceUuid, serviceData);
            return this;
        }

        /**
         * 设置连接后获取的服务
         *
         * @param adGattService 连接后获取的服务数据
         */
        public Builder addGattService(AdGattService adGattService) {

            BluetoothGattService bluetoothGattService = adGattService.getBluetoothGattService();
            if (bluetoothGattService != null) {
                String uuid = bluetoothGattService.getUuid().toString();
                if (!TextUtils.isEmpty(uuid)) {
                    mGattServiceMap.put(uuid, adGattService);
                }
            }
            return this;
        }

        /**
         * 注意:BLE广播数据分为广播数据和广播回应包数据
         * 广播数据包最大为:31 byte
         * 广播回应包最大为:31 byte
         * 大部分手机广播数据会拼接回应包,所以广播数据为:62 byte
         */
        public AdBleValueBean build() {
            settingsBuilder.setAdvertiseMode(mAdvertiseMode);
            settingsBuilder.setConnectable(mConnectable);
            settingsBuilder.setTimeout(mTimeoutMillis);
            settingsBuilder.setTxPowerLevel(mTxPowerLevel);
            dataBuilder.setIncludeDeviceName(mIncludeDeviceName);
            dataBuilder.setIncludeTxPowerLevel(mIncludeTxPowerLevel);
            if (mManufacturerData != null) {
                Set<Integer> keySet = mManufacturerData.keySet();
                for (Integer key : keySet) {
                    if (key != null) {
                        byte[] bytes = mManufacturerData.get(key);
                        if (bytes != null) {
                            dataBuilder.addManufacturerData(key, bytes);
                        }
                    }
                }
            }

            if (mServerUuid != null) {
                for (String uuid : mServerUuid) {
                    if (uuid != null) {
                        dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid));
                    }
                }
            }
            if (mServiceUuidData != null) {
                Set<String> uuidKey = mServiceUuidData.keySet();
                for (String uuid : uuidKey) {
                    if (!TextUtils.isEmpty(uuid)) {
                        byte[] bytes = mServiceUuidData.get(uuid);
                        if (bytes != null) {
                            dataBuilder.addServiceData(ParcelUuid.fromString(uuid), bytes);
                        }
                    }
                }
            }

            return new AdBleValueBean(settingsBuilder.build(), dataBuilder.build(), mGattServiceMap);
        }


    }


    public static AdBleValueBean parseFromBytes(byte[] scanRecord) {
        if (scanRecord == null) {
            return null;
        }

        int currentPos = 0;
        int advertiseFlag = -1;
        List<ParcelUuid> serviceUuids = new ArrayList<ParcelUuid>();
        List<ParcelUuid> serviceSolicitationUuids = new ArrayList<ParcelUuid>();
        String localName = null;
        int txPowerLevel = Integer.MIN_VALUE;

        SparseArray<byte[]> manufacturerData = new SparseArray<byte[]>();
        Map<ParcelUuid, byte[]> serviceData = new ArrayMap<ParcelUuid, byte[]>();

        try {
            while (currentPos < scanRecord.length) {
                // length is unsigned int.
                int length = scanRecord[currentPos++] & 0xFF;
                if (length == 0) {
                    break;
                }
                // Note the length includes the length of the field type itself.
                int dataLength = length - 1;
                // fieldType is unsigned int.
                int fieldType = scanRecord[currentPos++] & 0xFF;
                switch (fieldType) {
                    case BleConfig.DATA_TYPE_FLAGS:
                        advertiseFlag = scanRecord[currentPos] & 0xFF;
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
                    case BleConfig.DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_16_BIT, serviceUuids);
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
                    case BleConfig.DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_32_BIT, serviceUuids);
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
                    case BleConfig.DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_128_BIT, serviceUuids);
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT:
                        parseServiceSolicitationUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_16_BIT, serviceSolicitationUuids);
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT:
                        parseServiceSolicitationUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_32_BIT, serviceSolicitationUuids);
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT:
                        parseServiceSolicitationUuid(scanRecord, currentPos, dataLength, BluetoothUuid.UUID_BYTES_128_BIT, serviceSolicitationUuids);
                        break;
                    case BleConfig.DATA_TYPE_LOCAL_NAME_SHORT:
                    case BleConfig.DATA_TYPE_LOCAL_NAME_COMPLETE:
                        localName = new String(extractBytes(scanRecord, currentPos, dataLength));
                        break;
                    case BleConfig.DATA_TYPE_TX_POWER_LEVEL:
                        txPowerLevel = scanRecord[currentPos];
                        break;
                    case BleConfig.DATA_TYPE_SERVICE_DATA_16_BIT:
                    case BleConfig.DATA_TYPE_SERVICE_DATA_32_BIT:
                    case BleConfig.DATA_TYPE_SERVICE_DATA_128_BIT:
                        int serviceUuidLength = BluetoothUuid.UUID_BYTES_16_BIT;
                        if (fieldType == BleConfig.DATA_TYPE_SERVICE_DATA_32_BIT) {
                            serviceUuidLength = BluetoothUuid.UUID_BYTES_32_BIT;
                        } else if (fieldType == BleConfig.DATA_TYPE_SERVICE_DATA_128_BIT) {
                            serviceUuidLength = BluetoothUuid.UUID_BYTES_128_BIT;
                        }

                        byte[] serviceDataUuidBytes = extractBytes(scanRecord, currentPos, serviceUuidLength);
                        ParcelUuid serviceDataUuid = BluetoothUuid.parseUuidFrom(serviceDataUuidBytes);
                        byte[] serviceDataArray = extractBytes(scanRecord, currentPos + serviceUuidLength, dataLength - serviceUuidLength);
                        serviceData.put(serviceDataUuid, serviceDataArray);
                        break;
                    case BleConfig.DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
                        // The first two bytes of the manufacturer specific data are
                        // manufacturer ids in little endian.
                        int manufacturerId = ((scanRecord[currentPos + 1] & 0xFF) << 8) + (scanRecord[currentPos] & 0xFF);
                        byte[] manufacturerDataBytes = extractBytes(scanRecord, currentPos + 2, dataLength - 2);
                        manufacturerData.put(manufacturerId, manufacturerDataBytes);
                        break;
                    default:
                        // Just ignore, we don't handle such data type.
                        break;
                }
                currentPos += dataLength;
            }

            if (serviceUuids.isEmpty()) {
                serviceUuids = null;
            }


            Builder builder = newBuilder();
            builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
            builder.setTxPowerLevel(txPowerLevel);
            builder.setConnectable(true);
            builder.setIncludeTxPowerLevel(txPowerLevel != Integer.MIN_VALUE);
            builder.setIncludeDeviceName(localName != null);
            for (int i = 0; i < manufacturerData.size(); i++) {
                int key = manufacturerData.keyAt(i);
                if (key >= 0) {
                    builder.addManufacturerData(key,manufacturerData.get(key));
                }
            }
            builder.setTimeoutMillis(0);
            for (ParcelUuid serviceUuid : serviceUuids) {
                builder.addAdServiceUuid(serviceUuid.toString());
            }


            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Parse service UUIDs.
     */
    private static int parseServiceUuid(byte[] scanRecord, int currentPos, int dataLength, int uuidLength, List<ParcelUuid> serviceUuids) {
        while (dataLength > 0) {
            byte[] uuidBytes = extractBytes(scanRecord, currentPos, uuidLength);
            serviceUuids.add(BluetoothUuid.parseUuidFrom(uuidBytes));
            dataLength -= uuidLength;
            currentPos += uuidLength;
        }
        return currentPos;
    }


    /**
     * Helper method to extract bytes from byte array.
     */
    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }


    /**
     * Parse service Solicitation UUIDs.
     */
    private static int parseServiceSolicitationUuid(byte[] scanRecord, int currentPos, int dataLength, int uuidLength, List<ParcelUuid> serviceSolicitationUuids) {
        while (dataLength > 0) {
            byte[] uuidBytes = extractBytes(scanRecord, currentPos, uuidLength);
            serviceSolicitationUuids.add(BluetoothUuid.parseUuidFrom(uuidBytes));
            dataLength -= uuidLength;
            currentPos += uuidLength;
        }
        return currentPos;
    }

}



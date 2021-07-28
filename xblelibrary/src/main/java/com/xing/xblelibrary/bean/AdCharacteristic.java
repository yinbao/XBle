package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.xing.xblelibrary.config.BleConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * xing<br>
 * 2021/07/23<br>
 * 外围设备连接后提供的特征对象,只广播创建此对象
 */
public class AdCharacteristic {

    private BluetoothGattCharacteristic mBluetoothGattCharacteristic;

    private AdCharacteristic(String characteristicUUID, boolean readStatus) {
        this(characteristicUUID, readStatus, false, false,null);
    }


    private AdCharacteristic(String characteristicUUID, boolean readStatus, boolean notifyStatus) {
        this(characteristicUUID, readStatus, false, notifyStatus,null);
    }

    private AdCharacteristic(String characteristicUUID, boolean readStatus, boolean writeStatus, boolean notifyStatus,Map<String,BluetoothGattDescriptor> descriptorMap) {
        //添加可读+通知characteristic
        int property = 0;
        int permission = 0;
        if (readStatus) {
            permission |= BluetoothGattCharacteristic.PERMISSION_READ;
            property |= BluetoothGattCharacteristic.PROPERTY_READ;
        }
        if (writeStatus) {
            permission |= BluetoothGattCharacteristic.PERMISSION_WRITE;
            property |= BluetoothGattCharacteristic.PROPERTY_WRITE;
        }
        if (notifyStatus) {
            permission |= BluetoothGattCharacteristic.PERMISSION_READ;
            property |= BluetoothGattCharacteristic.PROPERTY_NOTIFY;
        }
        mBluetoothGattCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(characteristicUUID), property, permission);
        if (notifyStatus)
            mBluetoothGattCharacteristic.addDescriptor(new BluetoothGattDescriptor(BleConfig.UUID_NOTIFY_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_WRITE));
        if (descriptorMap!=null) {
            Collection<BluetoothGattDescriptor> values = descriptorMap.values();
            for (BluetoothGattDescriptor value : values) {
                mBluetoothGattCharacteristic.addDescriptor(value);
            }
        }

    }

    public BluetoothGattCharacteristic getBluetoothGattCharacteristic() {
        return mBluetoothGattCharacteristic;
    }

    public static Builder newBuilder() {
        return new Builder();
    }


    public static class Builder {

        private boolean readStatus;
        private boolean writeStatus;
        private boolean notifyStatus;
        private Map<String,BluetoothGattDescriptor> mDescriptorMap;

        public Builder() {
            mDescriptorMap=new HashMap<>();
        }

        public Builder setReadStatus(boolean readStatus) {
            this.readStatus = readStatus;
            return this;
        }

        public Builder setWriteStatus(boolean writeStatus) {
            this.writeStatus = writeStatus;
            return this;
        }

        public Builder setNotifyStatus(boolean notifyStatus) {
            this.notifyStatus = notifyStatus;
            return this;
        }


        /**
         * 添加描述(BluetoothGattDescriptor)
         *
         * @param uuidDescriptor        UUID
         * @param permissions {@link BluetoothGattCharacteristic#PERMISSION_WRITE,BluetoothGattCharacteristic#PERMISSION_READ}
         */
        public Builder addBluetoothGattDescriptor(String uuidDescriptor, int... permissions) {

            int permission = 0;
            for (int i : permissions) {
                permission |= i;
            }
            mDescriptorMap.put(uuidDescriptor,new BluetoothGattDescriptor(UUID.fromString(uuidDescriptor), permission));
            return this;
        }

        public AdCharacteristic build(String characteristicUUID) {
            return new AdCharacteristic(characteristicUUID,readStatus,writeStatus,notifyStatus,mDescriptorMap);
        }
    }

}

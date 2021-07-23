package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.xing.xblelibrary.config.BleConfig;

import java.util.UUID;

/**
 * xing<br>
 * 2021/07/23<br>
 * 作为外围的特征
 */
public class AdCharacteristic {

    private BluetoothGattCharacteristic mBluetoothGattCharacteristic;

    public AdCharacteristic(UUID characteristicUUID, boolean readStatus) {
        this(characteristicUUID, readStatus, false, false);
    }


    public AdCharacteristic(UUID characteristicUUID, boolean readStatus, boolean notifyStatus) {
        this(characteristicUUID, readStatus, false, notifyStatus);
    }

    public AdCharacteristic(UUID characteristicUUID, boolean readStatus, boolean writeStatus, boolean notifyStatus) {
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
        mBluetoothGattCharacteristic = new BluetoothGattCharacteristic(characteristicUUID, property, permission);
        if (notifyStatus)
            mBluetoothGattCharacteristic.addDescriptor(new BluetoothGattDescriptor(BleConfig.UUID_NOTIFY_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_WRITE));

    }

    public static class Builder {

        private boolean readStatus;
        private boolean writeStatus;
        private boolean notifyStatus;
        private UUID characteristicUUID;

        public Builder() {
        }

        public Builder setReadStatus(boolean readStatus) {
            this.readStatus = readStatus;
            return this;
        }

        public Builder setWriteStatus(boolean writeStatus) {
            this.writeStatus = writeStatus;
        }

        public Builder setNotifyStatus(boolean notifyStatus) {
            this.notifyStatus = notifyStatus;
        }

        public Builder setCharacteristicUUID(UUID characteristicUUID) {
            this.characteristicUUID = characteristicUUID;
        }
    }

}

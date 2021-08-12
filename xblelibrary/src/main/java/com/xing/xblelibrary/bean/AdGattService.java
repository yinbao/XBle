package com.xing.xblelibrary.bean;

import android.bluetooth.BluetoothGattService;
import android.os.Build;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.RequiresApi;

/**
 * xing<br>
 * 2021/07/27<br>
 * 外围设备连接后提供的服务对象,只广播创建此对象
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AdGattService {

    private BluetoothGattService mBluetoothGattService;

    private AdGattService(String serviceUUID, Map<String, AdCharacteristic> characteristicMap) {
        mBluetoothGattService = new BluetoothGattService(UUID.fromString(serviceUUID), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        Collection<AdCharacteristic> values = characteristicMap.values();
        for (AdCharacteristic value : values) {
            mBluetoothGattService.addCharacteristic(value.getBluetoothGattCharacteristic());
        }
    }


    public BluetoothGattService getBluetoothGattService() {
        return mBluetoothGattService;
    }

    public static Builder newBuilder() {
        return new Builder();
    }


    public static class Builder {

        private Map<String, AdCharacteristic> mCharacteristicMap;

        public Builder() {
            mCharacteristicMap=new HashMap<>();
        }

        /**
         * 添加特征(BluetoothGattCharacteristic)
         *
         * @param adCharacteristic AdCharacteristic
         */
        public Builder addAdCharacteristic(AdCharacteristic adCharacteristic) {

            String uuidCharacteristic = "";
            if (adCharacteristic != null) {
                uuidCharacteristic=adCharacteristic.getBluetoothGattCharacteristic().getUuid().toString();
            }
            mCharacteristicMap.put(uuidCharacteristic, adCharacteristic);
            return this;
        }
        public AdGattService build(String serviceUUID) {
            return new AdGattService(serviceUUID,mCharacteristicMap);
        }
    }


}



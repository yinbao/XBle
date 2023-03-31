package com.xing.XBle;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.xing.xblelibrary.XBleManager;
import com.xing.xblelibrary.bean.AdBleBroadcastBean;
import com.xing.xblelibrary.bean.AdCharacteristic;
import com.xing.xblelibrary.bean.AdGattService;
import com.xing.xblelibrary.config.XBleStaticConfig;
import com.xing.xblelibrary.device.AdBleDevice;
import com.xing.xblelibrary.device.SendDataBean;
import com.xing.xblelibrary.listener.OnBleAdvertiserConnectListener;
import com.xing.xblelibrary.listener.OnBleNotifyDataListener;
import com.xing.xblelibrary.utils.UuidUtils;
import com.xing.xblelibrary.utils.XBleL;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * xing<br>
 * 2021/8/2<br>
 * 手机作为外围设备
 */
public class PhonePeripheralActivity extends AppCompatActivity implements View.OnClickListener, OnBleAdvertiserConnectListener {


    private final int REFRESH_DATA = 2;

    private List<String> mListData;
    private ArrayAdapter mListAdapterData;
    private ListView mListViewData;
    private Button btn_start_ad, btn_stop_ad, btn_send_data;
    private EditText et_ad_uuid, et_ad_uuid2, et_ad_data_id, et_ad_data, et_send_data;

    /**
     * 广播服务的uuid
     */
    public static String UUID_SERVER_BROADCAST = "0000FFD0-0000-1000-8000-00805F9B34FB";
    /**
     * 服务的uuid
     */
    public final static String UUID_SERVER = "0000FFE0-0000-1000-8000-00805F9B34FB";
    /**
     * write
     */
    public final static String UUID_WRITE = "0000FFE1-0000-1000-8000-00805F9B34FB";

    /**
     * Notify
     */
    public final static String UUID_NOTIFY = "0000FFE2-0000-1000-8000-00805F9B34FB";

    /**
     * Write && Notify ( APP与BLE进行Inet交互的UUID) 独享
     */
    public final static String UUID_WRITE_NOTIFY = "0000FFE3-0000-1000-8000-00805F9B34FB";

    public AdBleDevice mAdBleDevice;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {

                case REFRESH_DATA:
                    if (mListAdapterData != null) {
                        mListAdapterData.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_peripheral);
        init();
    }

    private void init() {
        initView();
        initData();
        initListener();
    }

    private void initListener() {
        btn_start_ad.setOnClickListener(this);
        btn_stop_ad.setOnClickListener(this);
        btn_send_data.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        int id = v.getId();
        if (id == R.id.btn_start_ad) {//开始广播
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String adUUID = et_ad_uuid.getText().toString().trim();
                String adUUID2 = et_ad_uuid2.getText().toString().trim();
                String dataId = et_ad_data_id.getText().toString().trim();
                String data = et_ad_data.getText().toString().trim();
                byte[] bytes = new byte[data.length() >> 1];
                int j = 0;
                for (int i = 0; i < data.length(); i += 2) {
                    bytes[j] = (byte) Integer.parseInt(data.substring(i, i + 2), 16);
                    j++;
                }
                AdCharacteristic adCharacteristic1 = AdCharacteristic.newBuilder().setReadStatus(true).setWriteStatus(true).setNotifyStatus(false).build(UUID_WRITE);
                AdCharacteristic adCharacteristic2 = AdCharacteristic.newBuilder().setReadStatus(false).setWriteStatus(false).setNotifyStatus(true).build(UUID_NOTIFY);
                AdCharacteristic adCharacteristic3 = AdCharacteristic.newBuilder().setReadStatus(true).setWriteStatus(true).setNotifyStatus(true).build(UUID_WRITE_NOTIFY);
                AdGattService adGattService = AdGattService.newBuilder().addAdCharacteristic(adCharacteristic1).addAdCharacteristic(adCharacteristic2).addAdCharacteristic(adCharacteristic3)
                        .build(UUID_SERVER);
//                AdBleValueBean adBleValueBean = AdBleValueBean.parseAdBytes(new byte[]{});//通过广播数据生成广播对象
                AdBleBroadcastBean.Builder builder = AdBleBroadcastBean.newBuilder().addGattService(adGattService)//只做广播可免除
//                        .setConnectable(false)//是否可连接,默认可连接
                        .setTimeoutMillis(0)//一直广播
                        .setIncludeTxPowerLevel(false)//不广播功耗
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)//低延迟
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)//发射功率高
                        .addManufacturerData(Integer.parseInt(dataId, 16), bytes);

                if (!TextUtils.isEmpty(adUUID)) {
                    UUID uuid = UuidUtils.getUuid(adUUID);
                    if (uuid != null) {
                        builder.addAdServiceUuid(uuid.toString());
                    }
                }
                if (!TextUtils.isEmpty(adUUID2)&&!adUUID2.equalsIgnoreCase(adUUID)) {
                    UUID uuid = UuidUtils.getUuid(adUUID2);
                    if (uuid != null) {
                        builder.addAdServiceUuid(uuid.toString());
                    }
                }
                AdBleBroadcastBean adBleValueBean = builder.build();
                XBleManager.getInstance().setOnBleAdvertiserConnectListener(this);//设置广播的监听
                XBleManager.getInstance().startAdvertiseData(adBleValueBean);
            }
        } else if (id == R.id.btn_stop_ad) {//停止广播
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                XBleManager.getInstance().stopAdvertiseData();
                mListData.add(0, TimeUtils.getCurrentTimeStr() + "停止广播");
                mHandler.sendEmptyMessage(REFRESH_DATA);
            }
        } else if (id == R.id.btn_send_data) {//发送数据
            String data = et_send_data.getText().toString().trim();
            int length = data.length();
            int size = length >> 1;
            byte[] bytes = new byte[size];
            int j = 0;
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) Integer.parseInt(data.substring(j, j + 2), 16);
                j += 2;
            }
            if (mAdBleDevice != null) {
                mAdBleDevice.sendData(new SendDataBean(bytes, UUID.fromString(UUID_NOTIFY), XBleStaticConfig.WRITE_DATA, UUID.fromString(UUID_SERVER)));
            }
        }

    }


    private void initData() {
        mListData = new ArrayList<>();
        mListAdapterData = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mListData);
        mListViewData.setAdapter(mListAdapterData);
    }

    private void initView() {
        mListViewData = findViewById(R.id.lv_show_data);
        btn_start_ad = findViewById(R.id.btn_start_ad);
        btn_stop_ad = findViewById(R.id.btn_stop_ad);
        et_ad_uuid = findViewById(R.id.et_ad_uuid);
        et_ad_uuid2 = findViewById(R.id.et_ad_uuid2);
        et_ad_data_id = findViewById(R.id.et_ad_data_id);
        et_ad_data = findViewById(R.id.et_ad_data);
        et_send_data = findViewById(R.id.et_send_data);
        btn_send_data = findViewById(R.id.btn_send_data);

    }


    //---------------------广播--------------------------------

    @Override
    public void onStartAdSuccess(AdvertiseSettings advertiseSettings) {
        if (advertiseSettings != null) {
            XBleL.i("广播成功:" + advertiseSettings.toString());
        }
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "广播成功");
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }


    /**
     * @param errorCode 错误码:-1代表获取蓝牙对象为null
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_DATA_TOO_LARGE}//广播数据超过31 byte
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_TOO_MANY_ADVERTISERS}//没有装载广播对象
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_ALREADY_STARTED}//已经在广播了
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_INTERNAL_ERROR}//低层内部错误
     */
    @Override
    public void onStartAdFailure(int errorCode) {
        String errData = "";
        switch (errorCode) {

            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                errData = "广播数据超过31 byte";
                break;
            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                errData = "没有装载广播对象";
                break;
            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                errData = "已经在广播了";
                break;
            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                errData = "低层内部错误";
                break;
            case -1:
                errData = "蓝牙对象获取失败,手机不支持或者蓝牙未开启";
                break;
        }


        mListData.add(0, TimeUtils.getCurrentTimeStr() + "广播失败:" + errorCode + "\n原因:" + errData);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onStopAdSuccess() {
        XBleL.i("停止广播成功:");
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "停止广播成功:");
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }


    @Override
    public void onStopAdFailure(int errorCode) {
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "停止广播失败:" + errorCode);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onStartAdvertiser() {
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "开始广播");
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onAdConnectionSuccess(String mac) {
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "手机被连接成功:" + mac);
        mHandler.sendEmptyMessage(REFRESH_DATA);
        mAdBleDevice = XBleManager.getInstance().getAdBleDevice(mac);
        if (mAdBleDevice != null) {
            mAdBleDevice.setOnNotifyDataListener(new OnBleNotifyDataListener() {
                @Override
                public void onNotifyData(BluetoothGattCharacteristic characteristic, byte[] data) {
                    XBleL.i("中央设备返回的数据:" + BleStrUtils.byte2HexStr(data));
                    mListData.add(0, TimeUtils.getCurrentTimeStr() + "UUID:" + characteristic.getUuid().toString() + "\n中央设备返回的数据:" + BleStrUtils.byte2HexStr(data));
                    mHandler.sendEmptyMessage(REFRESH_DATA);
                }
            });
        }
    }

    @Override
    public void onAdDisConnected(String mac, int code) {
        mAdBleDevice = null;
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "手机被断开连接:" + mac);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }
}

package com.xing.XBle;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.xing.xblelibrary.XBleManager;
import com.xing.xblelibrary.bean.AdBleValueBean;
import com.xing.xblelibrary.bean.AdCharacteristic;
import com.xing.xblelibrary.bean.AdGattService;
import com.xing.xblelibrary.device.AdBleDevice;
import com.xing.xblelibrary.listener.OnBleAdvertiserConnectListener;
import com.xing.xblelibrary.listener.OnNotifyDataListener;
import com.xing.xblelibrary.utils.BleLog;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * xing<br>
 * 2021/8/2<br>
 * java类作用描述
 */
public class PhonePeripheralActivity extends AppCompatActivity implements View.OnClickListener, OnBleAdvertiserConnectListener {


    private final int REFRESH_DATA = 2;

    private List<String> mListData;
    private ArrayAdapter mListAdapterData;
    private ListView mListViewData;
    private Button  btn_start_ad, btn_stop_ad;
    private EditText tv_ad_uuid,tv_ad_data_id,tv_ad_data;
    public static String UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB";
    public static String UUID_SERVER_1 = "0000F0A0-0000-1000-8000-00805F9B34FB";
    public static String UUID_SERVER_2 = "0000F0A1-0000-1000-8000-00805F9B34FB";


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

    }

    @Override
    public void onClick(View v) {

        int id = v.getId();
        if (id == R.id.btn_start_ad) {//开始广播
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String adUUID = tv_ad_uuid.getText().toString().trim();
                String dataId = tv_ad_data_id.getText().toString().trim();
                String data = tv_ad_data.getText().toString().trim();
                byte[] bytes=new byte[data.length()>>1];
                int j=0;
                for (int i=0;i<data.length();i+=2){
                    bytes[j]= (byte) Integer.parseInt(data.substring(i,i+2),16);
                    j++;
                }
                AdCharacteristic adCharacteristic = AdCharacteristic.newBuilder().setReadStatus(true).setWriteStatus(true).setNotifyStatus(true).build(UUID_SERVER_2);
                AdGattService adGattService = AdGattService.newBuilder().addAdCharacteristic(adCharacteristic).build(UUID_SERVER_1);
//                AdBleValueBean adBleValueBean = AdBleValueBean.parseAdBytes(new byte[]{});//通过广播数据生成广播对象
                AdBleValueBean adBleValueBean = AdBleValueBean.newBuilder().addGattService(adGattService)//只做广播可免除
//                        .setConnectable(false)//是否可连接,默认可连接
                        .addAdServiceUuid(adUUID+UUID_SUFFIX).setTimeoutMillis(0)//一直广播
                        .setIncludeTxPowerLevel(false)//不广播功耗
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)//低延迟
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)//发射功率极低
                        .addManufacturerData(Integer.parseInt(dataId,16), bytes).build();
                XBleManager.getInstance().setOnBleAdvertiserConnectListener(this);//设置广播的监听
                int adId = XBleManager.getInstance().startAdvertiseData(adBleValueBean);
            }
        } else if (id == R.id.btn_stop_ad) {//停止广播
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                XBleManager.getInstance().stopAdvertiseData(-1);
                mListData.add(0, TimeUtils.getCurrentTimeStr() + "停止广播");
                mHandler.sendEmptyMessage(REFRESH_DATA);
            }
        }
    
    }

    private void initData() {
        mListData=new ArrayList<>();
        mListAdapterData = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mListData);
        mListViewData.setAdapter(mListAdapterData);
    }

    private void initView() {
        mListViewData=findViewById(R.id.lv_show_data);
        btn_start_ad=findViewById(R.id.btn_start_ad);
        btn_stop_ad=findViewById(R.id.btn_stop_ad);
        tv_ad_uuid=findViewById(R.id.tv_ad_uuid);
        tv_ad_data_id=findViewById(R.id.tv_ad_data_id);
        tv_ad_data=findViewById(R.id.tv_ad_data);

    }


    //---------------------广播--------------------------------

    @Override
    public void onStartAdSuccess(int adId, AdvertiseSettings advertiseSettings) {
        if (advertiseSettings != null)
            BleLog.i("广播成功:" + advertiseSettings.toString());
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "广播成功:" + adId);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }


    /**
     * @param adId
     * @param errorCode 错误码:-1代表获取蓝牙对象为null
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_DATA_TOO_LARGE}//广播数据超过31 byte
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_TOO_MANY_ADVERTISERS}//没有装载广播对象
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_ALREADY_STARTED}//已经在广播了
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_INTERNAL_ERROR}//低层内部错误
     */
    @Override
    public void onStartAdFailure(int adId, int errorCode) {
        String errData="";
        switch (errorCode) {

            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                errData="广播数据超过31 byte";
                break;
            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                errData="没有装载广播对象";
                break;
            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                errData="已经在广播了";
                break;
            case android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                errData="低层内部错误";
                break;

        }


        mListData.add(0, TimeUtils.getCurrentTimeStr() + "广播失败:" + errorCode+"\n原因:"+errData);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onStopAdSuccess(int adId) {
        BleLog.i("停止广播成功:" + adId);
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "停止广播成功:" + adId);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }


    @Override
    public void onStopAdFailure(int adId, int errorCode) {
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
        AdBleDevice adBleDevice = XBleManager.getInstance().getAdBleDevice(mac);
        adBleDevice.setOnNotifyDataListener(new OnNotifyDataListener() {
            @Override
            public void onNotifyData(BluetoothGattCharacteristic characteristic, byte[] data) {
                BleLog.i("中央设备返回的数据:" + BleStrUtils.byte2HexStr(data));
                mListData.add(0, TimeUtils.getCurrentTimeStr() + "UUID:" + characteristic.getUuid().toString() + "\n中央设备返回的数据:" + BleStrUtils.byte2HexStr(data));
                mHandler.sendEmptyMessage(REFRESH_DATA);
            }
        });
    }

    @Override
    public void onAdDisConnected(String mac, int code) {
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "手机被断开连接:" + mac);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }
}
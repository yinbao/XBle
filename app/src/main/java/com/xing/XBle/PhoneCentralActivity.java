package com.xing.XBle;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.xing.xblelibrary.XBleManager;
import com.xing.xblelibrary.bean.BleBroadcastBean;
import com.xing.xblelibrary.config.XBleStaticConfig;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.device.SendDataBean;
import com.xing.xblelibrary.listener.OnBleConnectListener;
import com.xing.xblelibrary.listener.OnBleNotifyDataListener;
import com.xing.xblelibrary.listener.OnBleScanFilterListener;
import com.xing.xblelibrary.utils.XBleL;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * xing<br>
 * 2021/8/2<br>
 * 手机作为中央设备
 */
public class PhoneCentralActivity extends AppCompatActivity implements View.OnClickListener, OnBleConnectListener , OnBleScanFilterListener {


    private final int REFRESH_BLE = 1;
    private final int REFRESH_DATA = 2;
    private Context mContext;
    private List<BleBroadcastBean> mListBle;
    private List<String> mListData;
    private BleDataAdapter mListAdapterBle;
    private ArrayAdapter mListAdapterData;
    private ListView mListViewBle;
    private ListView mListViewData;
    private Button btn_start_scan, btn_clear, btn_stop_scan, btn_disconnect,btn_send_data;
    private EditText et_filter_name,et_filter_mac,et_send_data;
    private String mBleName = "",mBleMac="";
    private BleDevice mBleDevice;
    private String mConnectMac;
    private String mWriteUuid=PhonePeripheralActivity.UUID_WRITE;
    private String mServiceUuid=PhonePeripheralActivity.UUID_SERVER;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case REFRESH_BLE:
                    if (mListAdapterBle != null) {
                        mListAdapterBle.notifyDataSetChanged();
                    }

                    break;


                case REFRESH_DATA:
                    if (mListAdapterData != null) {
                        mListAdapterData.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_central);
        mContext=this;
        init();


    }


    private void init() {
        initView();
        initData();
        initListener();
    }

    private void initListener() {
        mListViewBle.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BleBroadcastBean bleBroadcastBean = mListBle.get(position);
                mConnectMac = bleBroadcastBean.getMac();
                XBleManager.getInstance().stopScan();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    //可以优化连接BLE 4.0出现133的问题
                    XBleManager.getInstance().connectDevice(mConnectMac, BluetoothDevice.TRANSPORT_LE);
                }else {
                    XBleManager.getInstance().connectDevice(mConnectMac);
                }
            }
        });

        btn_start_scan.setOnClickListener(this);
        btn_stop_scan.setOnClickListener(this);
        btn_clear.setOnClickListener(this);
        btn_disconnect.setOnClickListener(this);
        btn_send_data.setOnClickListener(this);

    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
         if (id == R.id.btn_start_scan) {

            XBleManager.getInstance().startScan(30000,(UUID[]) null);
        } else if (id == R.id.btn_stop_scan) {
            XBleManager.getInstance().stopScan();
            mListData.add(0, TimeUtils.getCurrentTimeStr() + "停止扫描");
            mHandler.sendEmptyMessage(REFRESH_DATA);
        } else if (id == R.id.btn_clear) {
            mListBle.clear();
            mListData.clear();
            mHandler.sendEmptyMessage(REFRESH_BLE);
            mHandler.sendEmptyMessage(REFRESH_DATA);
        } else if (id == R.id.btn_disconnect) {
            XBleManager.getInstance().disconnectAll();
        }else if (id == R.id.btn_send_data) {
             String data = et_send_data.getText().toString().trim();
             int length = data.length();
             int size = length>>1;
             byte[] bytes=new byte[size];
             int j=0;
             for (int i=0;i<size;i++){
                 bytes[i]= (byte) Integer.parseInt(data.substring(j,j+2),16);
                 j+=2;
             }
             if (mBleDevice!=null&&mWriteUuid!=null&&mServiceUuid!=null){
                 mBleDevice.sendData(new SendDataBean(bytes, UUID.fromString(mWriteUuid), XBleStaticConfig.WRITE_DATA,UUID.fromString(mServiceUuid)));
             }

         }
    }

    private void initData() {
        mListBle = new ArrayList<>();
        mListAdapterBle = new BleDataAdapter(mListBle);
        mListViewBle.setAdapter(mListAdapterBle);

        mListData = new ArrayList<>();
        mListAdapterData = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mListData);
        mListViewData.setAdapter(mListAdapterData);
        XBleManager.getInstance().setOnBleConnectListener(this);
        XBleManager.getInstance().setOnScanFilterListener(this);

    }

    private void initView() {
        mListViewBle = findViewById(R.id.lv_show_ble);
        mListViewData = findViewById(R.id.lv_show_data);
        btn_start_scan = findViewById(R.id.btn_start_scan);
        btn_clear = findViewById(R.id.btn_clear);
        btn_stop_scan = findViewById(R.id.btn_stop_scan);
        btn_disconnect = findViewById(R.id.btn_disconnect);
        et_filter_name = findViewById(R.id.et_filter_name);
        et_filter_mac = findViewById(R.id.et_filter_mac);
        et_send_data = findViewById(R.id.et_send_data);
        btn_send_data = findViewById(R.id.btn_send_data);
    }

    private class BleDataAdapter extends BaseAdapter{

        private List<BleBroadcastBean> mData;

        public BleDataAdapter(List<BleBroadcastBean> data) {
            mData=data;
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView==null){
                convertView= LayoutInflater.from(mContext).inflate(android.R.layout.simple_list_item_1,null);
                viewHolder=new ViewHolder(convertView);
                convertView.setTag(viewHolder);
            }else {
                viewHolder= (ViewHolder) convertView.getTag();
            }
            BleBroadcastBean bleBroadcastBean = mData.get(position);
            String bleData = "Name=" + bleBroadcastBean.getName() + "\nRssi=" + bleBroadcastBean.getRssi() + "\nMAC=" + bleBroadcastBean.getMac();
            viewHolder.text1.setText(bleData);
            return convertView;
        }


        private final class ViewHolder{
            public TextView text1;

            public ViewHolder(View view) {
                text1= (TextView) view;
            }
        }

    }



    //-----------------------权限----------------------------------------

    /**
     * 需要申请的权限
     */
    private String[] LOCATION_PERMISSION = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    /**
     * 权限请求返回
     */
    private final int PERMISSION = 101;
    /**
     * 定位服务返回
     */
    protected final int LOCATION_SERVER = 102;


    protected void initPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onPermissionsOk();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, PERMISSION);
        } else {
            onPermissionsOk();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //请求权限被拒绝
        if (requestCode != PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initPermissions();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION[0])) {
                //权限请求失败，但未选中“不再提示”选项,再次请求
                ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, PERMISSION);
            } else {
                //权限请求失败，选中“不再提示”选项


            }

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_SERVER) {
            //定位服务页面返回
            initPermissions();
        }
    }

    /**
     * 权限ok
     */
    protected void onPermissionsOk() {
        XBleManager.getInstance().setOnScanFilterListener(this);//设置监听
        XBleManager.getInstance().startScan(10000,(UUID[]) null);//开始搜索
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "开始扫描:10S");
    }


    @Override
    public void onStartScan() {
        //开始扫描
        mBleName = et_filter_name.getText().toString().trim();
        mBleMac = et_filter_mac.getText().toString().trim();
        XBleL.i("开始扫描");

        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onScanBleInfo(BleBroadcastBean data) {
        //扫描返回的结果,每发现一个设备就会回调一次
        XBleL.i("扫描结果:" + data.getName() + " mac:" + data.getMac());
        if ((TextUtils.isEmpty(mBleName) || (data.getName() != null && data.getName().toUpperCase().contains(mBleName.toUpperCase()))) && (TextUtils.isEmpty(mBleMac) || (data.getMac().replace(":", "")
                .contains(mBleMac)))) {
            boolean newData=true;
            for (int i = 0; i < mListBle.size(); i++) {
                BleBroadcastBean bleBroadcastBean = mListBle.get(i);
                if (bleBroadcastBean.equals(data)){
                    newData=false;
                    bleBroadcastBean.setRssi(data.getRssi());
                }
            }
            synchronized (mListBle){
                if (newData){
                    mListBle.add(data);
                }
                if (mListAdapterBle != null) {
                    mListAdapterBle.notifyDataSetChanged();
                }
            }
        }
    }

    @Override
    public void onScanComplete() {
        //扫描超时,当设置扫描时间为0时将不会触发此方法
        XBleL.i("扫描超时");
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "扫描超时");
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onScanErr(long time) {
        //扫描异常
        XBleL.i("扫描太频繁,请在" + time + "ms后再进行扫描");
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "扫描太频繁,请在" + time + "ms后再进行扫描");
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onConnecting(String mac) {
        //连接中
        XBleL.i("正在连接的设备:" + mac);
        if (!mac.equalsIgnoreCase(mConnectMac)){
            return;
        }
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "正在连接的设备:" + mac);
        mHandler.sendEmptyMessage(REFRESH_DATA);

    }

    @Override
    public void onDisConnected(String mac, int code) {
        //连接断开
        //code=-1 代表连接超时
        //code=-2 代表获取服务失败
        XBleL.i("连接断开:" + mac + " 错误码:" + code);
        if (!mac.equalsIgnoreCase(mConnectMac)){
            return;
        }
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "连接断开:" + mac + " 错误码:" + code);
        mHandler.sendEmptyMessage(REFRESH_DATA);
        mBleDevice=null;
    }

    @Override
    public void onConnectionSuccess(String mac) {
        //连接成功,需要获取服务成功之后才允许操作
        XBleL.i("连接成功:" + mac);
        if (!mac.equalsIgnoreCase(mConnectMac)){
            return;
        }
        mListData.add(TimeUtils.getCurrentTimeStr() + "连接成功:" + mac);
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }

    @Override
    public void onServicesDiscovered(String mac) {
        //连接获取服务成功
        XBleL.i("获取服务成功:" + mac);
        if (!mac.equalsIgnoreCase(mConnectMac)){
            return;
        }
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "获取服务成功:" + mac + " 当前连接个数:" + XBleManager.getInstance().getBleDeviceAll().size());
        mHandler.sendEmptyMessage(REFRESH_DATA);
        mBleDevice = XBleManager.getInstance().getBleDevice(mac);
        if (mBleDevice != null) {
            mBleDevice.setSendDataInterval(100);//修改发送队列间隔,默认是200ms
            mBleDevice.setNotifyAll();//开启所有的notify
            mBleDevice.setIndicationAll();//开启所有的Indication
//            bleDevice.setNotify(serverUUID,notifyUUID1,notifyUUID2);//设置通知
//            bleDevice.sendDataNow(new SendDataBean());实时发送内容
//            bleDevice.sendData(new SendDataBean());使用队列发送内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBleDevice.setMtu(100);//设置吞吐量,23~517,需要ble设备支持
//                {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}默认
//                {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}高功率,提高传输速度
//                {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}低功率,传输速度减慢,更省电
                mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);//设置ble交互间隔,需要ble设备支持
            }

            mBleDevice.setOnNotifyDataListener(new OnBleNotifyDataListener() {

                @Override
                public void onNotifyData(BluetoothGattCharacteristic characteristic, byte[] data) {
                    //需要setNotify之后,并且ble返回了数据才会触发
                    XBleL.i("ble返回的数据:" + BleStrUtils.byte2HexStr(data));
                    mListData.add(0, TimeUtils.getCurrentTimeStr() + "Notify:" + characteristic.getUuid().toString() + "\n外围设备返回的数据:" + BleStrUtils.byte2HexStr(data));
                    mHandler.sendEmptyMessage(REFRESH_DATA);
                }


            });
        }
    }

    @Override
    public void bleOpen() {
        //蓝牙已开启
        onPermissionsOk();
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "蓝牙已开启");
        mHandler.sendEmptyMessage(REFRESH_DATA);

    }

    @Override
    public void bleClose() {
        //蓝牙已关闭
        mListData.add(0, TimeUtils.getCurrentTimeStr() + "蓝牙已关闭");
        mHandler.sendEmptyMessage(REFRESH_DATA);
    }


}
package com.xing.XBle;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.xing.xblelibrary.XBleManager;
import com.xing.xblelibrary.bean.BleValueBean;
import com.xing.xblelibrary.device.BleDevice;
import com.xing.xblelibrary.listener.OnBleScanConnectListener;
import com.xing.xblelibrary.listener.OnNotifyDataListener;
import com.xing.xblelibrary.utils.BleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements OnBleScanConnectListener {


    private List<String> mList;
    private ArrayAdapter listAdapter;
    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mListView=findViewById(R.id.lv_show_data);
        mList = new ArrayList<>();
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mList);
        mListView.setAdapter(listAdapter);
        BleLog.init(true);//开启日志
        XBleManager.getInstance().init(getApplicationContext(), new XBleManager.onInitListener() {
            @Override
            public void onInitSuccess() {
                //初始化成功
                initPermissions();//判断权限

            }
        });


        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String data = mList.get(position);
                String mac=data.substring(data.indexOf("MAC=")+4).trim();
                XBleManager.getInstance().stopScan();
                XBleManager.getInstance().connectDevice(mac);
            }
        });

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
        if (requestCode != PERMISSION)
            return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initPermissions();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, LOCATION_PERMISSION[0])) {
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
        XBleManager.getInstance().setOnBleScanConnectListener(this);//设置监听
        XBleManager.getInstance().startScan(1000);//开始搜索
    }


    @Override
    public void onStartScan() {
        //开始扫描
        BleLog.i("开始扫描");
    }

    @Override
    public void onScanning(BleValueBean data) {
        //扫描返回的结果,每发现一个设备就会回调一次
        BleLog.i("扫描结果:"+data.getName()+" mac:"+data.getMac());
        if (!mList.contains(data.getMac())){
            mList.add("Name="+data.getName()+"\nMAC="+data.getMac());
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onScanTimeOut() {
        //扫描超时,当设置扫描时间为0时将不会触发此方法
        BleLog.i("扫描超时");
    }

    @Override
    public void onScanErr(long time) {
        //扫描异常
        BleLog.i("扫描太频繁,请在"+time+"ms后再进行扫描");
    }

    @Override
    public void onConnecting(String mac) {
        //连接中
        BleLog.i("正在连接的设备:"+mac);
    }

    @Override
    public void onDisConnected(String mac, int code) {
        //连接断开
        //code=-1 代表连接超时
        //code=-2 代表获取服务失败
        BleLog.i("连接断开:"+mac+" 错误码:"+code);
    }

    @Override
    public void onConnectionSuccess(String mac) {
        //连接成功,需要获取服务成功之后才允许操作
        BleLog.i("连接成功:"+mac);
    }

    @Override
    public void onServicesDiscovered(String mac) {
        //连接获取服务成功
        BleLog.i("连接并获取服务成功:"+mac);
        BleDevice bleDevice = XBleManager.getInstance().getBleDevice(mac);
        if (bleDevice!=null){
            bleDevice.setSendDataInterval(100);//修改发送队列间隔,默认是200ms
            bleDevice.setNotifyAll();//开启所有的notify
//            bleDevice.setNotify(serverUUID,notifyUUID1,notifyUUID2);//设置通知
//            bleDevice.sendDataNow(new SendDataBean());实时发送内容
//            bleDevice.sendData(new SendDataBean());使用队列发送内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bleDevice.setMtu(23);//设置吞吐量,23~517,需要ble设备支持
//                {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}默认
//                {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}高功率,提高传输速度
//                {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}低功率,传输速度减慢,更省电
                bleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);//设置ble交互间隔,需要ble设备支持
            }

            bleDevice.setOnNotifyDataListener(new OnNotifyDataListener() {
                @Override
                public void onNotifyData(UUID uuid, byte[] data) {
                    //需要setNotify之后,并且ble返回了数据才会触发
                    BleLog.i("ble返回的数据:"+BleStrUtils.byte2HexStr(data));
                }
            });
        }
    }

    @Override
    public void bleOpen() {
        //蓝牙已开启
        onPermissionsOk();
    }

    @Override
    public void bleClose() {
        //蓝牙已关闭
    }
}
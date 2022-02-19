package com.xing.XBle;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.xing.xblelibrary.XBleManager;
import com.xing.xblelibrary.utils.XBleL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements  View.OnClickListener {

    private Button btn_central, btn_periphery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initPermissions();//判断权限
        init();
        XBleL.init(true);//开启日志
        XBleManager.getXBleConfig()
                .setConnectMax(7)//设置最大连接数据
                .setAutoConnectSystemBle(false)//设置是否自动连接系统已连接的设备
                .setAutoMonitorSystemConnectBle(false);//设置是否自动监听连接系统连接的设备,并在通用OnBleScanConnectListener接口中回调
        XBleManager.getInstance().init(getApplicationContext(), new XBleManager.onInitListener() {
            @Override
            public void onInitSuccess() {
            }
        });

    }


    private void init() {
        initView();
        initData();
        initListener();
    }

    private void initListener() {

        btn_periphery.setOnClickListener(this);
        btn_central.setOnClickListener(this);

    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_periphery) {
            startActivity(new Intent(this,PhonePeripheralActivity.class));
        }  else if (id == R.id.btn_central) {
            startActivity(new Intent(this,PhoneCentralActivity.class));
        } 
    }

    private void initData() {


    }

    private void initView() {
        btn_periphery = findViewById(R.id.btn_periphery);
        btn_central = findViewById(R.id.btn_central);

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

    }


}
# XBle使用说明
一个支持多连接和监听android系统连接的Ble库

![手机广播的数据](https://github.com/yinbao/XBle/blob/master/BroadcastData.jpeg)
![手机作外围](https://github.com/yinbao/XBle/blob/master/peripheral.jpg)
![手机作中央](https://github.com/yinbao/XBle/blob/master/central.jpg)

##  使用条件
1. Android SDK最低版本android4.4（API 19).
2. 设备所使用蓝牙版本需要4.0及以上。
3. 配置java1.8
4. 项目依赖androidx库

##  添加依赖

```


1.将JitPack存储库添加到您的构建文件中
将其添加到存储库末尾的root build.gradle中：
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

2.添加依赖项,最新版本号请参考文档开头
	dependencies {
	        implementation 'com.github.yinbao:XBle:+'
	}

3.在gradle中配置java1.8
    android {
        ...
        compileOptions {
            sourceCompatibility 1.8
            targetCompatibility 1.8
        }
		repositories {
			flatDir {
				dirs 'libs'
			}
		}
	}


```

## 权限设置

```
<!--In most cases, you need to ensure that the device supports BLE.-->
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true"/>

<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>

<!--Android 6.0 and above. Bluetooth scanning requires one of the following two permissions. You need to apply at run time.-->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>

<!--Optional. If your app need dfu function.-->
<uses-permission android:name="android.permission.INTERNET"/>
```

>  6.0及以上系统必须要定位权限，且需要手动获取权限

## 开始集成

> 初始化

```
//设置一些全局参数
XBleManager.getXBleConfig()
                .setConnectMax(7)//设置最多允许连接多少个设备(1~7)
                .setAutoConnectSystemBle(false)//设置是否自动连接系统已连接的设备
                .setAutoMonitorSystemConnectBle(false);//设置是否自动监听连接系统连接的设备,并在通用OnBleScanConnectListener接口中回调

 XBleManager.getInstance().init(getApplication(), new onInitListener() {
            @Override
            public void onInitSuccess() {
                //初始化完成
            }

            @Override
            public void onInitFailure() {
				//初始化失败
            }
        });



```

## 手机作为中央设备(大部分情况下都是中央)

-  设置接口XBleManager.getInstance().setOnBleScanConnectListener();实现OnBleScanConnectListener接口可以获取搜索,连接,断开等状态以及数据

```
/**
 * 蓝牙搜索,连接等操作接口
 */
public interface OnBleScanConnectListener extends OnBleConnectListener {
    /**
     * 开始扫描设备
     */
    default void onStartScan(){}
    /**
     * 每扫描到一个设备就会回调一次
     */
    default void onScanning(BleValueBean data){}
    /**
     * 扫描超时(完成)
     */
   default void onScanTimeOut(){}

     /**
     * 扫描异常
     * @param time 多少ms后才可以再次进行扫描
     */
    default void onScanErr(long time){}

    /**
     * 正在连接
     */
   default void onConnecting(String mac){}
  /**
     * 连接断开,在UI线程
     */
   default void onDisConnected(String mac, int code){}

    /**
     * 连接成功(发现服务),在UI线程
     */
  default void onServicesDiscovered(String mac){}

    /**
     * 已开启蓝牙,在触发线程
     */
   default void bleOpen(){}

    /**
     * 未开启蓝牙,在触发线程
     */
   default void bleClose(){}
}
```

-  设置/取消/清空 观察者形式监听BLE连接断连状态:

```

设置:XBleManager.getInstance().addBleConnectListener(OnBleConnectListener);
取消:XBleManager.getInstance().removeBleConnectListener(OnBleConnectListener);
清空:XBleManager.getInstance().removeAllBleConnectListener();

```

-  搜索  XBleManager.getInstance().startScan(long timeOut);//timeOut(毫秒)

```
    /**
     * 搜索设备(不过滤)
     * 扫描过于频繁会导致扫描失败
     * 需要保证5次扫描总时长超过30s
     * @param timeOut 超时时间,毫秒(搜索多久去取数据,0代表一直搜索)
     */
     startScan(long timeOut)

   /**
     * 搜索设备
     * 扫描过于频繁会导致扫描失败
     * 需要保证5次扫描总时长超过30s
     * @param timeOut  超时时间,毫秒(搜索多久去取数据,0代表一直搜索)
     * @param scanUUID 过滤的UUID(空数组代表不过滤)
     */
     startScan(long timeOut, UUID scanUUID)


	搜索到的设备
	会在OnCallbackBle接口中的onScanning(BleValueBean data)返回
	或者
	在OnScanFilterListener接口中的onScanBleInfo(BleValueBean bleValueBean)返回

```

-  连接XBleManager.getInstance().connectDevice(BleValueBean bleValueBean);或者connectDevice(String mAddress);

```
注:连接之前建议停止搜索XBleManager.getInstance().stopScan(),这样连接过程会更稳定
连接成功并获取服务成功后会在OnCallbackBle接口中的onServicesDiscovered(String mac)返回
```

-  断开连接

```
XBleManager.getInstance().disconnectAll();断开所有连接
XBleManager对象只提供断开所有设备的方法,断开某个设备可用BleDevice.disconnect();方法断开连接
XBleManager.getInstance().getBleDevice(String mac);可以获取BleDevice对象
```

-  监听系统连接的BLE,可用于第三方APP连接BLE后,自己的APP可以获取到连接对象,进行读写操作(可以绕过BLE的握手校验等操作).监听连接成功后会在OnBleScanConnectListener接口返回结果

```

    /**
     * 设备监听,监听指定的mac地址的设备,发现连接成功后马上连接获取操作的对象
     *
     * @param mAddress 设备地址,null或者空字符串可以监听所有的地址
     * @param status   是否开启监听
     */
XBleManager.getInstance().deviceConnectListener(String mAddress, boolean status);

```

-  获取连接的设备对象

```

BleDevice bleDevice =XBleManager.getInstance().getBleDevice(mAddress);
BleDevice对象拥有对此设备的所有操作,包括断开连接,发送指令,接收指令等操作
BleDevice.disconnect();//断开当前设备的连接
BleDevice.sendData(SendDataBean sendDataBean)//发送指令,内容需要用SendDataBean装载


    /**
     * @param hex         发送的内容
     * @param uuid        需要操作的特征uuid
     * @param type        操作类型(1=读,2=写,3=信号强度) {@link BleConfig}
     * @param uuidService 服务uuid
     */
    public SendDataBean(byte[] hex, UUID uuid, int type, UUID uuidService) {
        this.hex = hex;
        this.uuid = uuid;
        this.type = type;
        if (uuidService != null)
            this.uuidService = uuidService;
 }

由于发送数据存在发送队列,SendDataBean对象不建议复用,避免数据给覆盖;


```

- 前台服务设置

```
    /**
     * 设置前台服务相关参数
     * @param id id
     * @param icon logo
     * @param title 标题
     * @param activityClass 跳转的activity
     */
XBleManager.getInstance().initForegroundService(int id, @DrawableRes int icon, String title, Class<?> activityClass)

//启动前台服务,
XBleManager.getInstance().startForeground();

//停止前台服务
XBleManager.getInstance().stopForeground();


```


##  较常用的接口介绍


- XBleManager.getInstance().setOnScanFilterListener(OnScanFilterListener onScanFilterListener)//扫描过滤前置接口,只需要扫描的情况下只实现此接口即可,也可以在此提前过滤不需要的数据

```
public interface OnScanFilterListener {

    /**
     * 过滤计算->可以对广播数据进行帅选过滤
     *
     * @param bleValueBean 蓝牙广播数据
     * @return 是否有效
     */
    default boolean onBleFilter(BleValueBean bleValueBean) {
        return true;
    }

    /**
     * 蓝牙广播数据-> 符合要求的广播数据对象返回
     *
     * @param bleValueBean 搜索到的设备信息
     */
    default void onScanBleInfo(BleValueBean bleValueBean) {
    }

}
```


-  BleDevice 中的setOnCharacteristicListener(OnCharacteristicListener onCharacteristicListener) //蓝牙低层返回的原始数据对象

```
public interface OnCharacteristicListener {


    /**
     * 读数据接口
     *
     * @param characteristic
     */
    default void onCharacteristicReadOK(BluetoothGattCharacteristic characteristic) {
    }

    /**
     * 写数据接口
     *
     * @param characteristic
     */
    default void onCharacteristicWriteOK(BluetoothGattCharacteristic characteristic) {
    }

    /**
     * 设置Notify成功的回调
     */
    default void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
    }

    /**
     * notify返回的数据
     *
     * @param characteristic
     */
    default void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
    }


}

```

### BleDevice对象介绍

- BleDevice是当前连接的对象,可以进行读写通知等操作,BleDevice对象可以在连接成功后通过XBleManager.getInstance().getBleDevice(mac);获取;BleDevice中的相关方法介绍

```

readRssi();//可以获取当前的信号值,从OnBleRssiListener接口返回,需要手动设置监听BleDevice.setOnBleRssiListener


setNotify(UUID uuidService, UUID... uuidNotify);//可以设置一个Service下面的多个Notify,跟在后面即可,当有多个Service需要设置的时候多写几个setNotify即可.
如果需要监听Notify结果,可以实现OnCharacteristicListener接口中的onDescriptorWriteOK方法

setCloseNotify可以关闭相关Notify

disconnect();//断开当前连接
disconnect(boolean notice);//notice 断开后是否需要系统回调通知



```

- BleDevice中的setConnectPriority();可以设置连接参数,修改BLE的交互速率(需要硬件支持);android 5.0以上才支持

```

    /**
     * 设置连接参数
     *
     * @param connectionPriority 参数
	 * {@link BluetoothGatt.CONNECTION_PRIORITY_BALANCED}默认
     * {@link BluetoothGatt.CONNECTION_PRIORITY_HIGH}高功率,提高传输速度
     * {@link BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER}低功率,传输速度减慢,更省电
     * @return 结果
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setConnectPriority(int connectionPriority) {
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.requestConnectionPriority(connectionPriority);
        }
        return false;
    }

```

- BleDevice中的setMtu();可以设置发送的最大字节数,理论值23~517(需要硬件支持);android 5.0以上才支持

```

    /**
     * 返回的Mtu
     *
     * @param mtu 实际支持的最大字节数
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean setMtu(int mtu) {
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.requestMtu(mtu);
        }
        return false;
    }


```


- BleDevice中的sendData(SendDataBean);发送数据,内置发送队列;大部分BLE都是有接收间隔的,发送太快可能会导致BLE接收异常.
- 某些情况下不需要发送队列,可以调用BleDevice中的sendDataNow(SendDataBean);立刻发送数据
- BleDevice中的setSendDataInterval(int interval);可以设置发送队列的间隔,参数是毫秒(ms)

```

    /**
     * 修改发送队列的间隔
     * 默认是200ms
     *
     * @param interval 单位(ms)
     */
    public void setSendDataInterval(int interval) {
        mSendDataInterval = interval;
    }

public class SendDataBean {
    /**
     * 发送的内容
     */
    private byte[] hex;
    /**
     * 需要操作的特征uuid
     */
    private UUID uuid;
    /**
     * 操作类型(
	 * BleConfig.READ_DATA=读,
	 * BleConfig.WRITE_DATA=写,
	 * BleConfig.RSSI_DATA=信号强度)
     */
    private int type;

    /**
     * 消息是否需要置顶发送,默认false
     */
    private boolean mTop = false;
    /**
     * 服务的uuid
     */
    private UUID uuidService = null;
}

```


- BleDevice中的getBluetoothGatt();可以拿到当前连接的GATT
- BleDevice中的getMac();可以拿到当前连接的mac地址
- BleDevice中的getName();可以拿到当前连接的设备名称


## 手机作为外围设备(android 5.0以后才支持)


-  设置接口XBleManager.getInstance().setOnBleAdvertiserConnectListener();实现OnBleAdvertiserConnectListener接口可以获取搜索,连接,断开等状态以及数据

```
/**
 * xing<br>
 * 2021/07/22<br>
 * Ble作为外围广播监听
 */
public interface OnBleAdvertiserConnectListener {

    /**
     * 开始广播
     */
    default void onStartAdvertiser(){}

    /**
     * 发送广播成功
     */
    default void onStartAdSuccess(int adId, AdvertiseSettings advertiseSettings) {
    }

    /**
     * 发送广播失败
     *
     * @param errorCode 错误码:-1代表获取蓝牙对象为null
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_DATA_TOO_LARGE}//广播数据超过31 byte
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_TOO_MANY_ADVERTISERS}//没有装载广播对象
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_ALREADY_STARTED}//已经在广播了
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_INTERNAL_ERROR}//低层内部错误
     *                  {@link android.bluetooth.le.AdvertiseCallback#ADVERTISE_FAILED_FEATURE_UNSUPPORTED}//硬件不支持
     */
    default void onStartAdFailure(int adId, int errorCode) {
    }


    /**
     * 停止广播成功
     */
    default void onStopAdSuccess(int adId) {
    }

    /**
     * 停止广播失败
     *
     * @param errorCode 错误码:-1代表获取蓝牙对象为null
     */
    default void onStopAdFailure(int adId, int errorCode) {
    }

    /**
     * 外围设备连接成功
     */
    default void onAdConnectionSuccess(String mac) {
    }

    /**
     * 连接断开,在UI线程
     */
    default void onAdDisConnected(String mac, int code) {
    }
}
```


-  发起广播  XBleManager.getInstance().startAdvertiseData(AdBleValueBean adBleValueBean);

```
//例:
                AdCharacteristic adCharacteristic = AdCharacteristic.newBuilder().setReadStatus(true).setWriteStatus(true).setNotifyStatus(true).build("uuid");
                AdGattService adGattService = AdGattService.newBuilder().addAdCharacteristic(adCharacteristic).build("uuid");
//                AdBleValueBean adBleValueBean = AdBleValueBean.parseAdBytes(new byte[]{});//通过广播数据生成广播对象
                AdBleValueBean adBleValueBean = AdBleValueBean.newBuilder().addGattService(adGattService)//只做广播可免除
//                        .setConnectable(false)//是否可连接,默认可连接
                        .addAdServiceUuid("uuid").setTimeoutMillis(0)//一直广播
                        .setIncludeTxPowerLevel(false)//不广播功耗
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)//低延迟
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)//发射功率极低
                        .addManufacturerData(Integer.parseInt(dataId, 16), bytes).build();
                XBleManager.getInstance().setOnBleAdvertiserConnectListener(this);//设置广播的监听
                int adId = XBleManager.getInstance().startAdvertiseData(adBleValueBean);


```

-  XBleManager.getInstance().stopAdvertiseData(int id);停止广播,-1代表停止所有,



> 注:中央设备连接成功后,要触发读写norify之后才会回调onAdConnectionSuccess(String mac)接口


-  获取连接的中央设备对象,在onAdConnectionSuccess接口中获取

```

AdBleDevice bleDevice = XBleManager.getInstance().getAdBleDevice(mac);
AdBleDevice对象拥有对此设备的所有操作,包括断开连接,发送指令,接收指令等操作
AdBleDevice.disconnect();//断开当前设备的连接
AdBleDevice.sendData(SendDataBean sendDataBean)//发送指令,内容需要用SendDataBean装载


    /**
     * @param hex         发送的内容
     * @param uuid        需要操作的特征uuid
     * @param type        操作类型(1=读,2=写,3=信号强度) {@link BleConfig}
     * @param uuidService 服务uuid
     */
    public SendDataBean(byte[] hex, UUID uuid, int type, UUID uuidService) {
        this.hex = hex;
        this.uuid = uuid;
        this.type = type;
        if (uuidService != null)
            this.uuidService = uuidService;
 }

由于发送数据存在发送队列,SendDataBean对象不建议复用,避免数据给覆盖;


```





- AdBleDevice中的getBluetoothDevice();可以拿到当前连接的BluetoothDevice
- AdBleDevice中的getBluetoothGattServer();可以拿到当前连接的BluetoothGattServer
- AdBleDevice的getMac();可以拿到当前连接的mac地址
- AdBleDevice的getName();可以拿到当前连接的设备名称




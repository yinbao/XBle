# XBle使用说明
一个支持多连接和监听android系统连接的Ble库


##  使用条件
1. Android SDK最低版本android4.4（API 19）。
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

-  设置接口XBleManager.getInstance().setOnCallbackBle();实现OnCallbackBle接口可以获取搜索,连接,断开等状态和数据

```
/**
 * 蓝牙搜索,连接等操作接口
 */
public interface OnCallbackBle extends OnCallback {
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
	在OnScanFilterListener接口中的onScanRecord(BleValueBean bleValueBean)返回

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
     * @param uuidService 服务uuid(一般情况下不需要设置,使用默认的即可)
     */
    public SendDataBean(byte[] hex, UUID uuid, int type, UUID uuidService) {
        this.hex = hex;
        this.uuid = uuid;
        this.type = type;
        if (uuidService != null)
            this.uuidService = uuidService;
 }

正常情况下,使用SendDataBean的子类即可;
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
    default boolean onFilter(BleValueBean bleValueBean) {
        return true;
    }

    /**
     * 蓝牙广播数据-> 符合要求的广播数据对象返回
     *
     * @param bleValueBean 搜索到的设备信息
     */
    default void onScanRecord(BleValueBean bleValueBean) {
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

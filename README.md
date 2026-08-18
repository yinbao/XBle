

# XBle 使用说明

[![](https://jitpack.io/v/yinbao/XBle.svg)](https://jitpack.io/#yinbao/XBle)

Android BLE 库：支持多设备连接、扫描过滤、队列发送、手机作为中央 / 外围设备。

更偏「快速上手」的模块说明见：[xblelibrary/README.md](xblelibrary/README.md)

![手机广播的数据](https://github.com/yinbao/XBle/blob/master/BroadcastData.jpeg)
![手机作外围](https://github.com/yinbao/XBle/blob/master/peripheral.jpg)
![手机作中央](https://github.com/yinbao/XBle/blob/master/central.jpg)

## 功能

- 多设备连接管理（可配置最大连接数 1~7）
- 广播包解析与自定义扫描过滤（含 Service UUID 过滤）
- 按 MAC 连接；队列发送，避免发太快导致外设异常
- Notify 注册 / 取消；读写、RSSI、MTU、连接优先级
- 可选：自动连接 / 监听系统已连接的 BLE
- 手机作为外围设备广播，可被多个中央连接（中继场景）
- 前台服务保活（可选）

## 使用条件

1. `minSdk` 19（Android 4.4+）
2. 设备蓝牙 4.0+
3. Java 11（`sourceCompatibility` / `targetCompatibility` 11）
4. 依赖 AndroidX

## 添加依赖

```gradle
// root build.gradle / settings 仓库中增加
maven { url 'https://jitpack.io' }

// app 模块
dependencies {
    implementation 'com.github.yinbao:XBle:+'  // 版本号以 JitPack 徽章为准
}
```

> JitPack 构建使用 JDK 17（见 `jitpack.yml`）。


## 权限

库 `AndroidManifest` 已声明部分权限与 `XBleServer`。业务侧仍需**运行时申请**。

常用权限（按系统版本裁剪）：

```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true" />

<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<!-- Android 6+ 扫描常需定位（运行时申请） -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

> Android 6+ 定位、Android 12+ 附近设备权限需动态申请。示例见 `app` 模块。

---

## 初始化（必做）

`init` 绑定后台 `XBleServer`，**幂等**，内部使用 Application Context。

```java
XBleL.init(true); // 可选：打开日志

XBleManager.getXBleConfig()
        .setConnectMax(7)                       // 最大连接数 1~7
        .setAutoConnectSystemBle(false)         // 是否自动连接系统已连接设备
        .setAutoMonitorSystemConnectBle(false); // 是否监听系统连接并回调

XBleManager.getInstance().init(getApplicationContext(), new XBleManager.onInitListener() {
    @Override
    public void onInitSuccess() {
        // 服务就绪后再扫 / 连更稳妥；未就绪的操作多数会排队
    }

    @Override
    public void onInitFailure() {
        // 绑定失败
    }
});
```

| API | 说明 |
|-----|------|
| `isInitialized()` | 是否已 `init` |
| `isReady()` | `XBleServer` 是否已绑定 |
| `clear()` | 停扫、断连、清监听、解绑并释放 |

未 `init` 调用扫描/连接等会抛 `IllegalStateException`。

---

## 手机作为中央设备

### 监听（推荐 add，支持多处）

连接与扫描均改为**观察者 + 弱引用**，可多处同时监听：

```java
XBleManager.getInstance().addBleConnectListener(this);
XBleManager.getInstance().addBleScanFilterListener(this);

// 页面销毁时务必移除
XBleManager.getInstance().removeBleConnectListener(this);
XBleManager.getInstance().removeBleScanFilterListener(this);
// 或
XBleManager.getInstance().removeAllBleConnectListener();
XBleManager.getInstance().removeAllBleScanFilterListener();
```

> 旧 API `setOnBleConnectListener` / `setOnScanFilterListener` 已 `@Deprecated`，内部转调 `add`。

蓝牙开关（单监听、弱引用）：

```java
XBleManager.getInstance().setOnBleStatusListener(listener); // 传 null 清除
```

#### OnBleConnectListener

```java
public interface OnBleConnectListener extends OnBleStatusListener {
    default void onConnecting(String mac) {}
    default void onConnectMaxErr(List<BluetoothDevice> list) {}
    default void onDisConnected(String mac, int code) {}
    default void onConnectionSuccess(String mac) {}      // 已连接，未发现服务
    default void onServicesDiscovered(String mac) {}     // 可 getBleDevice 操作
    // bleOpen() / bleClose() 来自 OnBleStatusListener
}
```

#### OnBleScanFilterListener

```java
public interface OnBleScanFilterListener {
    default void onStartScan() {}
    /** 返回 false：本监听器收不到该设备的 onScanBleInfo */
    default boolean onBleFilter(BleBroadcastBean bleBroadcastBean) { return true; }
    default void onScanBleInfo(BleBroadcastBean bleBroadcastBean) {}
    default void onScanComplete() {}           // 仅设置了扫描超时时回调
    default void onScanErr(long time) {}       // time ms 后可再扫
}
```

每个扫描监听器**独立过滤**，互不影响。

### 扫描 / 连接 / 断开

```java
// timeOut：毫秒；0 = 持续扫描（库内会定期重启，避免长时间搜索无回调）
XBleManager.getInstance().startScan(30000);
XBleManager.getInstance().startScan(30000, serviceUuid); // UUID 过滤
XBleManager.getInstance().stopScan();

// 连接前建议 stopScan()
XBleManager.getInstance().connectDevice(mac);
XBleManager.getInstance().connectDevice(bleBroadcastBean);

XBleManager.getInstance().disconnectAll();
BleDevice device = XBleManager.getInstance().getBleDevice(mac);
if (device != null) {
    device.disconnect();
}
```

在 `onServicesDiscovered(mac)` 之后再取 `BleDevice` 读写：

```java
@Override
public void onServicesDiscovered(String mac) {
    BleDevice device = XBleManager.getInstance().getBleDevice(mac);
    if (device == null) return;

    device.setSendDataInterval(200);
    device.setNotifyAll(); // 或 setNotify(serviceUuid, notifyUuid...)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        device.setMtu(100);
        device.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
    }
    device.setOnNotifyDataListener((characteristic, data) -> {
        // Notify 数据
    });
}
```

### 系统已连接设备

通过配置开启，无需旧的 `deviceConnectListener` API：

```java
XBleManager.getXBleConfig()
        .setAutoConnectSystemBle(true)           // 初始化后尝试接管系统已连接设备
        .setAutoMonitorSystemConnectBle(true); // 监听系统后续连接并回调
```

### 前台服务

```java
XBleManager.getInstance().initForegroundService(id, iconRes, title, MainActivity.class);
XBleManager.getInstance().startForegroundService();
XBleManager.getInstance().stopForegroundService();
```

---

## BleDevice 发送与常用 API

`SendDataBean`（**不要复用**同一实例反复入队）：

```java
new SendDataBean(
    hex,                 // 内容
    characteristicUuid,  // 特征 UUID
    XBleStaticConfig.WRITE_DATA, // 或 READ_DATA / RSSI_DATA 等
    serviceUuid
);
```

| 常量 (`XBleStaticConfig`) | 含义 |
|---------------------------|------|
| `WRITE_DATA` | 写 |
| `READ_DATA` | 读 |
| `RSSI_DATA` | 读 RSSI（也可用 `readRssi()`） |
| `NOTICE_DATA` | Notify 开关（一般用 `setNotify`） |
| `MTU_DATA` | MTU（也可用 `setMtu`） |

### sendData vs sendDataNow

| API | 行为 |
|-----|------|
| `sendData(bean)` | 普通队列；写回调后再等 `setSendDataInterval`（默认 200ms）发下一条，避免外设处理不过来 |
| `sendDataNow(bean)` | 实时队列（默认优先）；仍等 `onCharacteristicWrite` 保证 GATT 串行，但回调后**立即**发下一条 |

有响应写 / 无响应写都会等待 Android 的 `onCharacteristicWrite` 再调度下一条。

```java
device.setSendDataInterval(200);   // 仅作用于 sendData
device.setSendNotifyInterval(50);  // Notify 连续设置间隔，最小 20
device.setResend(true, 3);         // 可选失败重发
device.sendData(bean);
device.sendDataNow(urgentBean);
```

其他常用：

```java
device.setNotify(serviceUuid, notifyUuid1, notifyUuid2);
device.setCloseNotify(serviceUuid, notifyUuid);
device.setOnCharacteristicListener(...); // OnBleCharacteristicListener
device.setOnBleRssiListener(...);
device.setOnBleMtuListener(...);         // 回调值为 mtu-3（约等于有效载荷）
device.getMac();
device.getName();
device.getBluetoothGatt();
```

队列优先级（主线程串行）：**控制(MTU/RSSI) > Notify > 写（实时/普通）**。

---

## 手机作为外围设备（Android 5.0+）

```java
XBleManager.getInstance().setOnBleAdvertiserConnectListener(this);
XBleManager.getInstance().startAdvertiseData(adBleBroadcastBean);
XBleManager.getInstance().stopAdvertiseData();

AdBleDevice ad = XBleManager.getInstance().getAdBleDevice(mac);
```

组装示例（类名以源码为准：`AdBleBroadcastBean`）：

```java
AdCharacteristic c1 = AdCharacteristic.newBuilder()
        .setReadStatus(true).setWriteStatus(true).setNotifyStatus(false)
        .build(UUID_WRITE);
AdCharacteristic c2 = AdCharacteristic.newBuilder()
        .setNotifyStatus(true)
        .build(UUID_NOTIFY);
AdGattService service = AdGattService.newBuilder()
        .addAdCharacteristic(c1).addAdCharacteristic(c2)
        .build(UUID_SERVER);

AdBleBroadcastBean ad = AdBleBroadcastBean.newBuilder()
        .addGattService(service)
        .addAdServiceUuid(UUID_SERVER_BROADCAST)
        .setTimeoutMillis(0)
        .setIncludeTxPowerLevel(false)
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .addManufacturerData(manufacturerId, manufacturerBytes)
        .build();

XBleManager.getInstance().setOnBleAdvertiserConnectListener(this);
XBleManager.getInstance().startAdvertiseData(ad);
```

`OnBleAdvertiserConnectListener` 主要回调：`onStartAdSuccess` / `onStartAdFailure` / `onAdConnectionSuccess` / `onAdDisConnected` 等（无 `adId` 参数）。

> 中央连上外围后，通常需发生读写 / Notify 交互后才会走到 `onAdConnectionSuccess`。

完整示例：`PhonePeripheralActivity`、`PhoneCentralActivity`。

---

## 注意事项

1. 先等 `onServicesDiscovered`，再 `getBleDevice` 发数据。  
2. 监听用 `add` + `onDestroy` 里 `remove`；弱引用不能替代注销。  
3. 扫描过频会 `onScanErr`，需等待提示毫秒数后再扫。  
4. 连接数受系统与 `setConnectMax` 共同限制。  
5. 不要复用同一个 `SendDataBean` 入队。  
6. `clear()` 会释放单例与服务，之后需重新 `init`。

## 示例工程

| 类 | 说明 |
|----|------|
| `MainActivity` | 初始化与入口 |
| `PhoneCentralActivity` | 中央扫描 / 连接 / 发送 |
| `PhonePeripheralActivity` | 外围广播 / 收发 |

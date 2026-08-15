# XBle Library 快速上手

库模块说明。完整文档（功能、依赖、权限、中央/外围示例）见仓库根目录 [README.md](../README.md)。

核心入口：

- `XBleManager`：初始化、扫描、连接、监听、广播
- `BleDevice`：单设备读写、Notify、发送队列

## 最小流程

```java
XBleManager.getXBleConfig().setConnectMax(7);
XBleManager.getInstance().init(context.getApplicationContext(), new XBleManager.onInitListener() {
    @Override
    public void onInitSuccess() {
        XBleManager.getInstance().addBleScanFilterListener(scanListener);
        XBleManager.getInstance().addBleConnectListener(connectListener);
        XBleManager.getInstance().startScan(15000);
    }
});
```

```text
onScanBleInfo → connectDevice(mac)
onServicesDiscovered → getBleDevice(mac) → setNotify / sendData
onDestroy → removeBleScanFilterListener / removeBleConnectListener
```

## 监听 API（当前）

| 用途 | 推荐 API | 说明 |
|------|----------|------|
| 连接状态 | `addBleConnectListener` / `remove...` | 弱引用，可多处监听 |
| 扫描过滤 | `addBleScanFilterListener` / `remove...` | 弱引用；各自独立 `onBleFilter` |
| 蓝牙开关 | `setOnBleStatusListener` | 弱引用，单监听，`null` 清除 |

已废弃：`setOnBleConnectListener`、`setOnScanFilterListener`（内部转 `add`）。

## 发送

| API | 行为 |
|-----|------|
| `sendData` | 普通队列；写回调后再隔 `setSendDataInterval`（默认 200ms） |
| `sendDataNow` | 实时队列；写回调后立即下一条 |

有/无响应写均等待 `onCharacteristicWrite`。操作类型见 `XBleStaticConfig`。

## 状态查询

- `isInitialized()` / `isReady()` / `clear()`
- `getBleDevice(mac)` / `getBleDeviceAll()`

示例 Activity：`PhoneCentralActivity`、`PhonePeripheralActivity`。

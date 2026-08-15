package com.xing.xblelibrary.listener;

import com.xing.xblelibrary.bean.BleBroadcastBean;

/**
 * 扫描/过滤多观察者分发（弱引用，由 BaseListenerIm 持有）。
 * <p>
 * 每个监听器独立执行 {@link OnBleScanFilterListener#onBleFilter}，
 * 仅向过滤通过的监听器回调 {@link OnBleScanFilterListener#onScanBleInfo}。
 */
public class BleScanFilterListenerIm extends BaseListenerIm<OnBleScanFilterListener> {

    private static class SingletonHolder {
        private static final BleScanFilterListenerIm INSTANCE = new BleScanFilterListenerIm();
    }

    public static BleScanFilterListenerIm getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void onStartScan() {
        for (OnBleScanFilterListener observer : getAliveListeners()) {
            observer.onStartScan();
        }
    }

    public void onScanComplete() {
        for (OnBleScanFilterListener observer : getAliveListeners()) {
            observer.onScanComplete();
        }
    }

    public void onScanErr(long time) {
        for (OnBleScanFilterListener observer : getAliveListeners()) {
            observer.onScanErr(time);
        }
    }

    public void onScanBleInfo(BleBroadcastBean bleBroadcastBean) {
        for (OnBleScanFilterListener observer : getAliveListeners()) {
            if (observer.onBleFilter(bleBroadcastBean)) {
                observer.onScanBleInfo(bleBroadcastBean);
            }
        }
    }
}

package com.xing.xblelibrary.listener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 观察者模式基类：以弱引用持有监听器，Activity/Fragment 销毁后可被 GC 回收。
 */
public abstract class BaseListenerIm<T> {

    private final List<WeakReference<T>> listListener = new ArrayList<>();

    /**
     * 添加事件监听对象（弱引用，同一实例不会重复添加）
     */
    public void addListListener(T o) {
        if (o == null) {
            return;
        }
        synchronized (this) {
            pruneClearedLocked();
            for (WeakReference<T> ref : listListener) {
                if (ref.get() == o) {
                    return;
                }
            }
            listListener.add(new WeakReference<>(o));
        }
    }

    /**
     * 注销事件监听对象
     */
    public void removeListener(T o) {
        if (o == null) {
            return;
        }
        synchronized (this) {
            Iterator<WeakReference<T>> it = listListener.iterator();
            while (it.hasNext()) {
                T listener = it.next().get();
                if (listener == null || listener == o) {
                    it.remove();
                }
            }
        }
    }

    /**
     * 清空观察者列表
     */
    public void removeListenerAll() {
        synchronized (this) {
            listListener.clear();
        }
    }

    /**
     * 取出仍存活的监听器快照，并顺带清理已被 GC 的弱引用。
     */
    protected List<T> getAliveListeners() {
        synchronized (this) {
            List<T> alive = new ArrayList<>(listListener.size());
            Iterator<WeakReference<T>> it = listListener.iterator();
            while (it.hasNext()) {
                T listener = it.next().get();
                if (listener == null) {
                    it.remove();
                } else {
                    alive.add(listener);
                }
            }
            return alive;
        }
    }

    private void pruneClearedLocked() {
        Iterator<WeakReference<T>> it = listListener.iterator();
        while (it.hasNext()) {
            if (it.next().get() == null) {
                it.remove();
            }
        }
    }
}

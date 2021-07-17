package com.xing.xblelibrary.listener;

import java.util.HashSet;
import java.util.Set;

/**
 * 功能描述:
 * 1,观察者模式基类
 */

public abstract class BaseListenerIm<T> {

    protected volatile Set<T> listListener = new HashSet<>();


    /**
     * 添加改变事件监听对象
     *
     * @param o 列表改变事件监听对象
     */
    public void addListListener(T o) {
        synchronized (BaseListenerIm.class) {
            if (o != null){
                listListener.add(o);
            }
        }
    }

    /**
     * 注销事件监听对象
     *
     * @param o 列表该表监听事件监听对象
     */
    public void removeListener(T o) {
        synchronized (BaseListenerIm.class) {
            if (o != null)
                listListener.remove(o);
        }
    }

    /**
     * 清空观察者列表
     */
    public void removeListenerAll() {
        synchronized (BaseListenerIm.class) {
            listListener.clear();
        }
    }
    
}

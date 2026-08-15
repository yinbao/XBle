package com.xing.xblelibrary.listener;

/**
 * xing<br>
 * 2021/07/21<br>
 */
public interface OnBleMtuListener {

    /**
     * MTU 返回的数据会-3,就是实际可用的MTU大小
     * @param mtu 吞吐量(23~517),
     */
    void OnMtu(int mtu);

}

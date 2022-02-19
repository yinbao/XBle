package com.xing.xblelibrary.listener;

/**
 * xing<br>
 * 2021/07/21<br>
 */
public interface OnBleMtuListener {

    /**
     * MTU
     * @param mtu 吞吐量(23~517)
     */
    void OnMtu(int mtu);

}

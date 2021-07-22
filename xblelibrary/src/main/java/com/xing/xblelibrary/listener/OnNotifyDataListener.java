package com.xing.xblelibrary.listener;

import java.util.UUID;

/**
 * xing<br>
 * 2021/07/21<br>
 * 透传数据接口
 */
public interface OnNotifyDataListener {


    /**
     * notify返回的数据
     *
     * @param uuid UUID
     * @param data 数据
     */
     void onNotifyData(UUID uuid,byte[] data);


}

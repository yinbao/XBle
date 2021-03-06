package com.xing.xblelibrary.device;


import com.xing.xblelibrary.config.XBleStaticConfig;

import java.util.UUID;

/**
 * xing<br>
 * 2021/07/21<br>
 * 发送数据对象,bean
 * 由于存在发送队列,建议不要复用当前对象
 * <p>
 * Send data object, bean <br>
 * Because there is a send queue, it is recommended not to reuse the current object
 */
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
     * 操作类型
     * 读{@link XBleStaticConfig#READ_DATA}
     * 写{@link XBleStaticConfig#WRITE_DATA}
     * 信号强度{@link XBleStaticConfig#RSSI_DATA}
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


    /**
     * 重发次数
     */
    private int mResendNumber=0;


    /**
     * @param hex         发送的内容
     * @param uuid        需要操作的特征uuid
     * @param type        操作类型 {@link XBleStaticConfig#READ_DATA, XBleStaticConfig#WRITE_DATA, XBleStaticConfig#RSSI_DATA}
     * @param uuidService 服务uuid
     */
    public SendDataBean(byte[] hex, UUID uuid, int type, UUID uuidService) {
        this.hex = hex;
        this.uuid = uuid;
        this.type = type;
        if (uuidService != null)
            this.uuidService = uuidService;
    }

    public byte[] getHex() {
        return hex;
    }

    protected void setHex(byte[] hex) {
        this.hex = hex;
    }

    public UUID getUuid() {
        return uuid;
    }


    /**
     * @return (1 = 读, 2 = 写, 3 = 信号强度)
     */
    public int getType() {
        return type;
    }

    /**
     * @param type (1=读,2=写,3=信号强度)
     */
    public void setType(int type) {
        this.type = type;
    }

    public UUID getUuidService() {
        return uuidService;
    }


    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setUuidService(UUID uuidService) {
        this.uuidService = uuidService;
    }


    public boolean isTop() {
        return mTop;
    }

    public void setTop(boolean top) {
        mTop = top;
    }


    public int getResendNumber() {
        return mResendNumber;
    }

    public void addResendNumber() {
        mResendNumber+=1;
    }
}

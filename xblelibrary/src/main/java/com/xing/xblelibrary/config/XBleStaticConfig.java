package com.xing.xblelibrary.config;


import java.util.UUID;

/**
 * xing<br>
 * 2021/07/21<br>
 * 蓝牙参数配置,uuid等信息
 */
public class XBleStaticConfig {


    //-------------操作------------
    /**
     * 读(read)
     */
    public static final int READ_DATA = 1;
    /**
     * 写(write)
     */
    public static final int WRITE_DATA = 2;
    /**
     * 读取rssi(Read rssi)
     */
    public static final int RSSI_DATA = 3;
    /**
     * 通知(notify)
     */
    public static final int NOTICE_DATA = 4;

    /**
     * 指示(Indication)
     */
    public static final int INDICATION_DATA = 5;
    //-----------------

    /**
     * 设置Notify的特征中的描述UUID
     */
    public static UUID UUID_NOTIFY_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    /**
     * 1=已经在搜索了(如果没有回调,可能是权限问题)
     * Fails to start scan as BLE scan with the same settings is already started by the app.
     */
    public static final int SCAN_FAILED_ALREADY_STARTED = 1;

    /**
     * 注册搜索失败
     * Fails to start scan as app cannot be registered.
     */
    public static final int SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2;

    /**
     * 蓝牙低层内部错误
     * Fails to start scan due an internal error
     */
    public static final int SCAN_FAILED_INTERNAL_ERROR = 3;

    /**
     * 不支持此搜索方式
     * Fails to start power optimized scan as this feature is not supported.
     */
    public static final int SCAN_FAILED_FEATURE_UNSUPPORTED = 4;

    /**
     * 硬件不支持
     * Fails to start scan as it is out of hardware resources.
     */
    public static final int SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5;

    /**
     * 搜索过于频繁
     * Fails to start scan as application tries to scan too frequently.
     */
    public static final int SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6;


    /**
     * 连接过程超时
     * 连接成功,获取服务超时
     */
    public static final int DISCONNECT_CODE_ERR_TIMEOUT=-1;
    /**
     * 连接成功,获取服务失败
     */
    public static final int DISCONNECT_CODE_ERR_SERVICE_FAIL=-2;
    /**
     * 未连接,当前广播不支持连接
     */
    public static final int DISCONNECT_CODE_ERR_NO_CONNECT=-3;


    //---------------连接错误码---------------
    /* Success code and error codes */
//            #define  GATT_SUCCESS                        0x0000
//            #define  GATT_INVALID_HANDLE                 0x0001
//            #define  GATT_READ_NOT_PERMIT                0x0002
//            #define  GATT_WRITE_NOT_PERMIT               0x0003
//            #define  GATT_INVALID_PDU                    0x0004
//            #define  GATT_INSUF_AUTHENTICATION           0x0005
//            #define  GATT_REQ_NOT_SUPPORTED              0x0006
//            #define  GATT_INVALID_OFFSET                 0x0007
//            #define  GATT_INSUF_AUTHORIZATION            0x0008
//            #define  GATT_PREPARE_Q_FULL                 0x0009
//            #define  GATT_NOT_FOUND                      0x000a
//            #define  GATT_NOT_LONG                       0x000b
//            #define  GATT_INSUF_KEY_SIZE                 0x000c
//            #define  GATT_INVALID_ATTR_LEN               0x000d
//            #define  GATT_ERR_UNLIKELY                   0x000e
//            #define  GATT_INSUF_ENCRYPTION               0x000f
//            #define  GATT_UNSUPPORT_GRP_TYPE             0x0010
//            #define  GATT_INSUF_RESOURCE                 0x0011
//            #define  GATT_ILLEGAL_PARAMETER              0x0087
//            #define  GATT_NO_RESOURCES                   0x0080
//            #define  GATT_INTERNAL_ERROR                 0x0081
//            #define  GATT_WRONG_STATE                    0x0082
//            #define  GATT_DB_FULL                        0x0083
//            #define  GATT_BUSY                           0x0084
//            #define  GATT_ERROR                          0x0085
//            #define  GATT_CMD_STARTED                    0x0086
//            #define  GATT_PENDING                        0x0088
//            #define  GATT_AUTH_FAIL                      0x0089
//            #define  GATT_MORE                           0x008a
//            #define  GATT_INVALID_CFG                    0x008b
//            #define  GATT_SERVICE_STARTED                0x008c
//            #define  GATT_ENCRYPED_MITM                  GATT_SUCCESS
//            #define  GATT_ENCRYPED_NO_MITM               0x008d
//            #define  GATT_NOT_ENCRYPTED                  0x008e
//
//            #define  GATT_RSP_ERROR                      0x01
//            #define  GATT_REQ_MTU                        0x02
//            #define  GATT_RSP_MTU                        0x03
//            #define  GATT_REQ_FIND_INFO                  0x04
//            #define  GATT_RSP_FIND_INFO                  0x05
//            #define  GATT_REQ_FIND_TYPE_VALUE            0x06
//            #define  GATT_RSP_FIND_TYPE_VALUE            0x07
//            #define  GATT_REQ_READ_BY_TYPE               0x08
//            #define  GATT_RSP_READ_BY_TYPE               0x09
//            #define  GATT_REQ_READ                       0x0A
//            #define  GATT_RSP_READ                       0x0B
//            #define  GATT_REQ_READ_BLOB                  0x0C
//            #define  GATT_RSP_READ_BLOB                  0x0D
//            #define  GATT_REQ_READ_MULTI                 0x0E
//            #define  GATT_RSP_READ_MULTI                 0x0F
//            #define  GATT_REQ_READ_BY_GRP_TYPE           0x10
//            #define  GATT_RSP_READ_BY_GRP_TYPE           0x11
//            #define  GATT_REQ_WRITE                      0x12
//            #define  GATT_RSP_WRITE                      0x13
//            #define  GATT_CMD_WRITE                      0x52
//            #define  GATT_REQ_PREPARE_WRITE              0x16
//            #define  GATT_RSP_PREPARE_WRITE              0x17
//            #define  GATT_REQ_EXEC_WRITE                 0x18
//            #define  GATT_RSP_EXEC_WRITE                 0x19
//            #define  GATT_HANDLE_VALUE_NOTIF             0x1B
//            #define  GATT_HANDLE_VALUE_IND               0x1D
//            #define  GATT_HANDLE_VALUE_CONF              0x1E
//            #define  GATT_SIGN_CMD_WRITE                 0xD2


    //------------广播解析相关------------
    public static final int DATA_TYPE_FLAGS = 0x01;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    public static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    public static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    public static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    public static final int DATA_TYPE_SERVICE_DATA_16_BIT = 0x16;
    public static final int DATA_TYPE_SERVICE_DATA_32_BIT = 0x20;
    public static final int DATA_TYPE_SERVICE_DATA_128_BIT = 0x21;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT = 0x14;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT = 0x1F;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT = 0x15;
    public static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;





}

package com.baidu.iot.devicecloud.devicemanager.constant;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class DataPointConstant {
    public static final int DEFAULT_VERSION = 100;

    public static final String DATA_POINT_ALIVE_INTERVAL = "alive_interval";
    public static final String DATA_POINT_PRIVATE_ERROR = "private_error";

    public static final String DATA_POINT_DUER_DIRECTIVE = "duer_directive";
    public static final String DATA_POINT_DUER_EVENT = "duer_event";
    public static final String DATA_POINT_DUER_ACK = "duer_ack";
    public static final String DATA_POINT_DUER_PRIVATE = "duer_private";
    public static final String DATA_POINT_DUER_DLP = "duer_dlp";
    public static final String DATA_POINT_PACKAGE_INFO = "package_info";
    public static final String DATA_POINT_OTA_EVENT = "ota_event";
    public static final String DATA_POINT_DUER_TRACE_INFO = "duer_trace_info";
    public static final String DATA_POINT_DUER_BIND_UTOKEN = "duer_bind_utoken";
    public static final String DATA_POINT_DUER_LOG = "duer_log";
    public static final String DATA_POINT_DEVICE_STATUS = "device_status";

    // Private
    public static final String PRIVATE_PROTOCOL_NAMESPACE = "ai.dueros.private.protocol";
    public static final String PRIVATE_PROTOCOL_DIALOGUE_FINISHED = "DialogueFinished";
}

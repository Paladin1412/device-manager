package com.baidu.iot.devicecloud.devicemanager.constant;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/18.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class CommonConstant {
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_ORIGIN_CLIENT_IP = "X-Forwarded-For";
    public static final String HEADER_ORIGIN_CLIENT_PORT = "X-Forwarded-Port";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    public static final String HEADER_MESSAGE_TYPE = "X-MESSAGE-TYPE";
    public static final String HEADER_MESSAGE_TIMESTAMP = "X-MESSAGE-TIMESTAMP";
    public static final String HEADER_CLT_ID = "X-CLT-ID";
    public static final String HEADER_SN = "X-SN";
    public static final String HEADER_CUID = "X-CUID";
    public static final String HEADER_STANDBY_DEVICE_ID = "X-STANDBY-DEVICE-ID";
    public static final String HEADER_STREAMING_VERSION = "X-STREAMING_VERSION";
    public static final String HEADER_LOG_ID = "X-LOG-ID";
    public static final String HEADER_ALIVE_INTERVAL = "X-ALIVE-INTERVAL";
    public static final String HEADER_STATUS_CODE = "X-STATUS-CODE";
    public static final String HEADER_PRE_TTS = "X-PRE-TTS";
    public static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    public static final String MESSAGE_ACK_NEED = "needAck";
    public static final String MESSAGE_ACK_SECRET_KEY = "secretKey";

    public static final String PARAMETER_BEARER = "Bearer ";
    public static final String PARAMETER_TOKEN = "token";
    public static final String PARAMETER_UUID = "uuid";
    public static final String PARAMETER_CUID = "cuid";
    public static final String PARAMETER_VERSION = "version";
    public static final String PARAMETER_ID = "id";
    public static final String PARAMETER_CODE = "code";
    public static final String PARAMETER_PATH = "path";
    public static final String PARAMETER_QUERY = "query";
    public static final String PARAMETER_PAYLOAD = "payload";
    public static final String PARAMETER_MISC = "misc";
    public static final String PARAMETER_METADATA = "metadata";
    public static final String PARAMETER_AUDIO = "audio";
    public static final String PARAMETER_NAME = "name";
    public static final String PARAMETER_CID = "cid:";
    public static final String PARAMETER_CONTENT_DISPOSITION = "content-disposition";
    public static final String PARAMETER_CONTENT_ID = "Content-ID";
    public static final String PARAMETER_CLIENT_ID = "clt_id";
    public static final String PARAMETER_MESSAGE_ID = "msgid";

    // splitters
    public static final String SPLITTER_DOLLAR = "$";
    public static final String SPLITTER_URL = "/";
    public static final String SPLITTER_COLON = ":";
    public static final String SPLITTER_SPACE = " ";
    public static final String SPLITTER_EQUALITY_SIGN = "=";
    public static final String SPLITTER_SEMICOLON = ";";
    public static final String SPLITTER_LF = "\n";

    public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    public static final String SESSION_KEY_PREFIX = "device:session:";
    public static final String SESSION_DEVICE_ACCESS_TOKEN = "accessToken";

    public static final String DEVICE_RESOURCE_KEY_PREFIX = "duer:iot:sh:info:device:";
    public static final String DEVICE_INFO = "deviceInfo";

    public static final String MESSAGE_SUCCESS = "success";
    public static final int MESSAGE_SUCCESS_CODE = 0;
    public static final int MESSAGE_SUCCESS_CODE_DH2 = 1;
    public static final String MESSAGE_FAILURE = "failure";
    public static final int MESSAGE_FAILURE_CODE = -1;
    public static final int MESSAGE_UNEXPECTED_FAILURE_CODE = -2;
}

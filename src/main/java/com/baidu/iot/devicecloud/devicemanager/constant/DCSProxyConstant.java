package com.baidu.iot.devicecloud.devicemanager.constant;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class DCSProxyConstant {
    public static final String JSON_KEY_PARAM = "param";
    public static final String JSON_KEY_VALUE = "value";
    public static final String JSON_KEY_LINK_VERSION = "LinkVersion";
    public static final String JSON_KEY_SUPPORT_FAST_TLV = "SupportFastTLV";
    public static final String JSON_KEY_PID = "pid";
    public static final String JSON_KEY_SN = "sn";
    public static final String JSON_KEY_SNR = "snr";
    public static final String JSON_KEY_CLIENT_IP = "clientip";
    public static final String JSON_KEY_TTS = "TTS";
    public static final String JSON_KEY_PRE_TTS = "PRE_TTS";
    public static final String JSON_KEY_USER_AGENT = "user-agent";
    public static final String JSON_KEY_CLT_ID = "clt_id";
    public static final String JSON_KEY_AUTHORIZATION = "authorization";
    public static final String JSON_KEY_STANDBY_DEVICE_ID = "standbydeviceid";
    public static final String JSON_KEY_BNS = "bns";
    public static final String JSON_KEY_REQUIRE_SNR = "require_snr";
    public static final String JSON_KEY_STREAM_VERSION = "streaming_version";
    public static final String JSON_KEY_CONTENT_TYPE = "content-type";
    public static final String JSON_KEY_DUEROS_DEVICE_ID = "dueros-device-id";
    public static final String JSON_KEY_TIMESTAMP = "timestamp";
    public static final String JSON_KEY_NAME = "name";
    public static final String JSON_KEY_DATA = "data";
    public static final String JSON_KEY_CONFIDENT = "confident";
    public static final String JSON_KEY_DECODER_TYPE = "decoder_type";
    public static final String JSON_KEY_ERROR = "error";
    public static final String JSON_KEY_ERROR_MSG = "error_msg";
    public static final String JSON_KEY_ERROR_NO = "error_no";
    public static final String JSON_KEY_INDEX = "index";
    public static final String JSON_KEY_IS_WAKEUP = "is_wakeup";
    public static final String JSON_KEY_LAST = "last";
    public static final String JSON_KEY_SESSION = "session";
    public static final String JSON_KEY_SIL_DURATION = "sil_duration";
    public static final String JSON_KEY_SIL_START = "sil_start";
    public static final String JSON_KEY_SPEECH_ID = "speech_id";
    public static final String JSON_KEY_TEXT = "text";
    public static final String JSON_KEY_UNCERTAIN_WORD = "uncertain_word";
    public static final String JSON_KEY_ASR_REJECT = "asr_reject";
    public static final String JSON_KEY_STATUS = "status";
    public static final String JSON_KEY_MSG = "msg";
    public static final String JSON_KEY_VOLUMN = "volume";
    public static final String JSON_KEY_SPEED = "speed";
    public static final String JSON_KEY_XML = "xml";
    public static final String JSON_KEY_KEY = "key";
    public static final String JSON_KEY_SPEAKER = "speaker";
    public static final String JSON_KEY_PITCH = "pitch";
    public static final String JSON_KEY_AUE = "aue";
    public static final String JSON_KEY_RATE = "rate";
    public static final String JSON_KEY_TTA_OPTIONAL = "tts_optional";
    public static final String JSON_KEY_CONTENT_ID = "content_id";

    public static final String JSON_KEY_DCS_CLIENT_CONTEXT = "clientContext";
    public static final String JSON_KEY_DCS_EVENT = "event";

    public static final String JSON_KEY_RESULT_TYPE_ASR = "asr";
    public static final String JSON_KEY_RESULT_TYPE_VP = "vp_res";

    public static final String HEADER_PRODUCT_ID = "pid";
    public static final String HEADER_USER_AGENT = "user-agent";
    public static final String HEADER_CLIENT_ID = "clt_id";
    public static final String HEADER_AUTHORIZATION = "authorization";
    public static final String HEADER_STANDBY_DEVICE_ID = "standbydeviceid";
    public static final String HEADER_CALLER_BNS = "bns";
    public static final String HEADER_SN = "sn";
    public static final String HEADER_STREAMING_VERSION = "streaming_version";
    public static final String HEADER_CONTENT_TYPE = "content-type";
    public static final String HEADER_DUEROS_DEVICE_ID = "dueros-device-id";

    public static final String USER_STATE_CONNECTED = "userConnected";
    public static final String USER_STATE_DISCONNECTED = "userDisconnected";
    public static final String USER_STATE_EXCEPTION = "userException";

    // directive
    public static final String DIRECTIVE_KEY_DIRECTIVE = "directive";
    public static final String DIRECTIVE_KEY_HEADER = "header";
    public static final String DIRECTIVE_KEY_HEADER_NAME = "name";
    public static final String DIRECTIVE_KEY_HEADER_NAMESPACE = "namespace";
    public static final String DIRECTIVE_KEY_HEADER_MESSAGE_ID = "messageId";
    public static final String DIRECTIVE_KEY_HEADER_DIALOG_ID = "dialogRequestId";
    public static final String DIRECTIVE_KEY_PAYLOAD = "payload";
    public static final String DIRECTIVE_KEY_PAYLOAD_TYPE = "type";
    public static final String DIRECTIVE_KEY_PAYLOAD_TOKEN = "token";
    public static final String DIRECTIVE_KEY_PAYLOAD_FORMAT = "format";
    public static final String DIRECTIVE_KEY_PAYLOAD_URL = "url";

    // command
    public static final String COMMAND_SPEAK = "Speak";
    public static final String COMMAND_PLAY = "Play";

    // env
    public static final int ASR_PORT_OFFSET = 8;
    public static final int EVENT_PORT_OFFSET = 7;
}

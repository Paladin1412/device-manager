package com.baidu.iot.devicecloud.devicemanager.constant;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/2/21.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class CoapConstant {
    public static final int COAP_MAGIC_HEAD = 0xbeefdead;
    public static final int COAP_METHOD_EMPTY = 0;
    public static final int COAP_METHOD_GET = 1;
    public static final int COAP_METHOD_POST = 2;
    public static final int COAP_METHOD_PUT = 3;
    public static final int COAP_METHOD_DELETE = 4;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_CREATED = 65;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_DELETED = 66;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID = 67;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_CHANGED = 68;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_CONTENT = 69;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_CONTINUE = 95;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_REQUEST = 128;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED = 129;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_OPTION = 130;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_FORBIDDEN = 131;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_FOUND = 132;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_METHOD_NOT_ALLOWED = 133;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_ACCEPTABLE = 134;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_REQUEST_ENTITY_INCOMPLETE = 136;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_PRECONDITION_FAILED = 140;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_REQUEST_ENTITY_TOO_LARGE = 141;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT = 143;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_INTERNAL_SERVER_ERROR = 160;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_IMPLEMENTED = 161;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_GATEWAY = 162;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_SERVICE_UNAVAILABLE = 163;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_GATEWAY_TIMEOUT = 164;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_PROXYING_NOT_SUPPORTED = 165;
    public static final int COAP_RESPONSE_CODE_DUER_MSG_RSP_INVALID = 0xFF;
}

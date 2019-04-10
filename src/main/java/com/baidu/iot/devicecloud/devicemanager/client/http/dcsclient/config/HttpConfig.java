package com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Component
public class HttpConfig {
    // 请求https
    public static  String HTTP_PREFIX;
    @Value("${dcs3.scheme:http://}")
    public void setDcs3Prefix (String prefix) {
        HTTP_PREFIX = prefix;
    }
    // 请求host

    public static  String HOST;
    @Value("${dcs3.host:}")
    public void setDcs3Host (String host) {
        HOST = host;
    }

    public static  String API;
    @Value("${dcs3.api:}")
    public void setDcs3Api (String api) {
        API = api;
    }

    public static  String BNS;
    @Value("${dcs3.bns:}")
    public void setDcs3Bns (String bns) {
        BNS = bns;
    }

    public static String VERIFIER;
    @Value("${dcs3.verifier:}")
    public void setDcs3Verifier (String verifier) { VERIFIER = verifier;}


    public static String endpoint = null;
    // 请求event事件
    public static final String EVENTS = "/dcs/v1/events";
    // 请求directives事件
    public static final String DIRECTIVES = "/dcs/v1/directives";
    // 请求云端对接接口
    public static final String SERVER = "/dcs/v1/server";
    // ping
    public static final String PING = "/dcs/v1/ping";
    // 请求event事件TAG
    public static final String HTTP_EVENT_TAG = "event";
    // 请求directives事件TAG
    public static final String HTTP_DIRECTIVES_TAG = "directives";
    // 请求ping的TAG
    public static final String HTTP_PING_TAG = "ping";

    public static String getEndpoint() {
//        return BnsUtils.getBNSOrUrl(HTTP_PREFIX, BNS, API);
        return null;
    }

    public static String getEventsUrl() {
        return getEndpoint() + EVENTS;
    }

    public static String getServerUrl() {
        return getEndpoint() + SERVER;
    }

    public static String getDirectivesUrl() {
        return getEndpoint() + DIRECTIVES;
    }

    public static String getPingUrl() {
        return getEndpoint() + PING;
    }

    public static class HttpHeaders {
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String CONTENT_DISPOSITION = "Content-Disposition";
        public static final String DUEROS_DEVICE_ID = "dueros-protocol-id";
        public static final String AUTHORIZATION = "Authorization";
        public static final String CONTENT_ID = "Content-ID";
        public static final String BEARER = "Bearer ";
        public static final String DEBUG = "debug";
        public static final String DEBUG_PARAM = "0";
    }

    public static class ContentTypes {
        public static final String JSON = "application/json";
        public static final String APPLICATION_JSON = JSON + ";" + " charset=UTF-8";
        public static final String APPLICATION_AUDIO = "application/octet-stream";
    }

    public static class ContentDisposition {
        public static final String FORM_DATA_METADATA = "form-data; name=\"metadata\"";
        public static final String FORM_DATA_AUDIO = "form-data; name=\"audio\"";
    }
    public static class Parameters {
        public static final String BOUNDARY = "boundary";
        public static final String DATA_METADATA = "metadata";
        public static final String DATA_AUDIO = "audio";
    }
}

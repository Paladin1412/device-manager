package com.baidu.iot.devicecloud.devicemanager.config.remoteserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DcsProxyServerConfig {
    public static String DCS_PROXY_BNS;
    public static String DCS_PROXY_API;

    public static String TTS_PROXY_BNS;
    public static String TTS_PROXY_API;

    public static String DH_BNS;
    public static String DH_API;

    @Value("${dcs.proxy.bns:dumi-connect-controller-sandbox-prev-5035.orp.all}")
    public void setDcsProxyBns(String dcsProxyBns) {
        DCS_PROXY_BNS = dcsProxyBns;
    }

    @Value("${dcs.proxy.api}")
    public void setDcsProxyApi(String dcsProxyApi) {
        DCS_PROXY_API = dcsProxyApi;
    }

    @Value("${tts.proxy.bns:unknown}")
    public void setTtsProxyBns(String ttsProxyBns) {
        TTS_PROXY_BNS = ttsProxyBns;
    }

    @Value("${tts.proxy.api}")
    public void setTtsProxyApi(String ttsProxyApi) {
        TTS_PROXY_API = ttsProxyApi;
    }

    @Value("${dh.bns:}")
    public void setDhBns(String dhProxyBns) {
        DH_BNS = dhProxyBns;
    }

    @Value("${dh.api:}")
    public void setDhApi(String dhProxyApi) {
        DH_API = dhProxyApi;
    }
}

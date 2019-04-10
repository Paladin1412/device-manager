package com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by huangwenqing on 2017/4/13.
 */
@Component
public class DiConfig {

    static String diScheme;
    @Value("${di.scheme:http://}")
    public void setDiScheme (String scheme) {
        diScheme = scheme;
    }

    static String diApi;
    @Value("${di.api}")
    public void setDiApi (String url) {
        diApi = url;
    }

    static String diBns;
    @Value("${di.bns:}")
    public void setDiBns (String bns) {
        diBns = bns;
    }
}



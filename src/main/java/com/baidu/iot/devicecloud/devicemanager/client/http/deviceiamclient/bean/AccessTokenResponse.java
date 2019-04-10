package com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by huangwenqing on 2017/8/2.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessTokenResponse {
    private String accessToken;

    private Long timestamp;

    public AccessTokenResponse(String accessToken) {
        this.accessToken = accessToken;
        this.timestamp = System.currentTimeMillis();
    }

    public AccessTokenResponse(String accessToken, Long timestamp) {
        this.accessToken = accessToken;
        this.timestamp = timestamp;
    }
}

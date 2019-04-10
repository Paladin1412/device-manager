package com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Created by huangwenqing on 2017/8/2.
 */

@Data
public class AccessTokenRequest {
    @JsonProperty("device_uuid")
    private String cuid;

    @JsonProperty("clientId")
    private String voiceId;

    @JsonProperty("clientSecret")
    private String voiceKey;

    @JsonIgnore
    private String logId;

    public AccessTokenRequest(String cuid, String vId, String vKey, String logId) {
        this.cuid = cuid;
        this.voiceKey   = vKey;
        this.voiceId    = vId;
        this.logId      = logId;
    }
}

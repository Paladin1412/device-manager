package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import lombok.Data;

@Data
public class SenderMsg {
    private String channel;
    private String message;
    private Long crateTime;
}

package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * SenderChannelType
 *
 * @author Long Yunxiang (longyunxiang@baidu.com)
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum SenderChannelType {
    DATA, HEARTBEAT, CONTROL, SYSTEM, IFTTT, USER,
    MAX_HOLDER
}

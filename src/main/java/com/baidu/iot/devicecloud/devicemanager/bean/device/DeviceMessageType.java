package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DeviceMessageType
 *
 * @author Long Yunxiang (longyunxiang@baidu.com)
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum DeviceMessageType {
    DATA, CONTROL, HEARTBEAT, ACTIVATE, OTA, RULE, OTA_PROGRESS, REG,
    MAX_HOLDER
}

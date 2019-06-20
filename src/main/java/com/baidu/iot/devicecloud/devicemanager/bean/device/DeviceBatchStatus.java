package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.baidu.iot.devicecloud.devicemanager.bean.CodeEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Device batch status
 *
 * @author Huang Yichao (huangyichao@baidu.com)
 */
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum DeviceBatchStatus implements CodeEnum {
    ERROR(0), ACTIVE(1), DELETED(2);

    @Getter
    private final int code;
}

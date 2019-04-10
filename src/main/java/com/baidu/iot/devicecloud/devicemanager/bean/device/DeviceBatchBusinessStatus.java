package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The project status.
 *
 * @author Feng Guodong (fengguodong01@baidu.com).
 */
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum DeviceBatchBusinessStatus implements CodeEnum {
    DEVELOPING(1), PRODUCED(2);

    @Getter
    private final int code;
}

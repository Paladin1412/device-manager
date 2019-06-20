package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.baidu.iot.devicecloud.devicemanager.bean.CodeEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by pansongsong02 on 2017/6/16.
 */
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum DeviceBatchNeedBindStatus implements CodeEnum {
    NEEDLESS(0), NEED(1);

    @Getter
    private final int code;
}

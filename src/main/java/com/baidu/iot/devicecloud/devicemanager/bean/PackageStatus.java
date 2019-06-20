package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum  PackageStatus implements CodeEnum {
    ERROR(0), RELEASED(1), RESERVED(2), DELETED(3), INVALID(4);

    @Getter
    private final int code;
}

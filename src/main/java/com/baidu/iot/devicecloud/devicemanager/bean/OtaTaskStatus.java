package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/13.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum OtaTaskStatus implements CodeEnum {
    CREATED(0), RUNNING(1), SUCCESS(2), FAILED(3), DELETED(4), VALID(5), INSTALLED(6);

    @Getter
    private final int code;
}

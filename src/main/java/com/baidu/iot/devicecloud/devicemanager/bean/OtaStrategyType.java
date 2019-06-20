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
public enum  OtaStrategyType implements CodeEnum {
    SILENT(1), INTERACTIVE_STRONG(2), INTERACTIVE_WEEK(3), PASSIVE(4), NULL(5), SILENT_ACTIVE(6);

    @Getter
    private final int code;
}

package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.DATETIME_FORMAT;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/13.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@EqualsAndHashCode
@ToString
public class OtaTask {
    private Long id;

    private Long taskId;

    private String deviceUuid;

    private Long packageId;

    private Long otaStrategyId;

    private Long osVersionId;

    private OtaTaskStatus status;

    private String eventLog;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME_FORMAT, timezone = "UTC")
    private Date createTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME_FORMAT, timezone = "UTC")
    private Date updateTime;
}

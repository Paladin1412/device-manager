package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotNull;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/17.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class OtaTaskCreateRequest {
    @NotNull
    private String deviceUuid;

    @NotNull
    private Long packageId;

    private Long strategyId;
}

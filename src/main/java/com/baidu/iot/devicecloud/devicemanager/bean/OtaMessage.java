package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
@EqualsAndHashCode()
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtaMessage {
    @JsonIgnore
    private String uuid;
    @JsonIgnore
    private String dialogRequestId;
    @JsonIgnore
    private String messageId;
    @JsonProperty("transaction")
    private String taskId;
    private String version;
    @JsonProperty("old_version")
    private String oldVersion;
    private String url;
    private String signature;
    private long size;

    private OtaStrategyType strategy;
}

package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.http.MediaType;

import javax.validation.constraints.NotNull;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/16.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
@EqualsAndHashCode()
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseMessage {
    @JsonIgnore
    @NotNull
    private int messageType;

    @JsonIgnore
    private String contentType = MediaType.APPLICATION_JSON_VALUE;

    @JsonIgnore
    private String userAgent;

    @JsonIgnore
    private String deviceIp;

    @JsonIgnore
    private String devicePort;

    @JsonIgnore
    private String deviceId;

    @JsonIgnore
    private String productId;

    @JsonIgnore
    private String cltId;

    @JsonIgnore
    private String sn;

    // api caller's ip:port or bns
    @JsonIgnore
    private String bns;

    @JsonIgnore
    private String standbyDeviceId;

    @JsonIgnore
    private String streamingVersion;

    @JsonIgnore
    private String logId;

    @JsonIgnore
    private boolean needAck = false;

    @JsonIgnore
    private Long timestamp = System.currentTimeMillis();
}

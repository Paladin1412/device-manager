package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/9/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceSystemInfo {
    private String from;

    @JsonProperty(value = "client_id")
    private String clientId;

    @JsonProperty(value = "sdk_version")
    private String sdkVersion;

    @JsonProperty(value = "operation_system")
    private String operationSystem;

    @JsonProperty(value = "operation_system_version")
    private String operationSystemVersion;

    @JsonProperty(value = "device_brand")
    private String deviceBrand;

    @JsonProperty(value = "device_model")
    private String deviceModel;

    @JsonProperty(value = "ces_version")
    private String cesVersion;

    @JsonProperty(value = "abtest")
    private int abTest;

    private int real;
}

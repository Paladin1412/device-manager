package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * Device Resource
 *
 * @author Huang Yichao (huangyichao@baidu.com)
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceResource {

    @JsonProperty(value = "device_uuid")
    private String deviceUuid;

    private String cuid;

    @JsonProperty(value = "account_uuid")
    private String accountUuid;

    @JsonProperty(value = "user_id")
    private String userId;

    private List<String> userIds;

    private String bduss;

    private String token;

    @JsonProperty(value = "mac_id")
    private String mac;

    private ProjectInfo projectInfo;

    private DeviceBatchInfo deviceBatchInfo;

    private Float longitude;

    private Float latitude;

    private String geoCoordinateSystem;

    private String ip;

    private String port;

    private String accessToken;
}

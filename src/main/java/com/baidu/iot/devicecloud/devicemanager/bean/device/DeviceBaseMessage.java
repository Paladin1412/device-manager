package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DeviceBaseMessage
 *
 * @author Long Yunxiang (longyunxiang@baidu.com)
 */
@Data
@ToString
@EqualsAndHashCode()
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceBaseMessage {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.FFF'Z'", timezone = "UTC")
    protected Date time = new Date();

    protected DeviceMessageType messageType;

    /*
     * Parse from rawPayload
     */
    protected String deviceUuid;
    protected String token;
    protected Map<String, Object> data;

    /*
     * Parse from device iam
     */
    protected String accountUuid;

    protected Float longitude;
    protected Float latitude;
    protected String geoCoordinateSystem;

    // from device iam <- user iam
    protected String userId;

    protected List<String> userIds;

    protected String bduss = "";

    protected Integer needBind = 0;

    /*
     * Parse from COAP request header
     */
    protected String sourceHostAddress;
    protected Integer sourcePort;

    /*
     * Raw data
     */
    @JsonIgnore
    protected String rawPayload;

    private String logId;

    private Long timestamp;

    private String dcs = "3";

    private String cuid;

}

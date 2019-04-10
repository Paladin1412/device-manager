package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

/**
 * Database basic class for device batch.
 *
 * @author Huang Yichao (huangyichao@baidu.com)
 */
@Data
@ToString
@EqualsAndHashCode()
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceBatchInfo {
    private Long id;

    private Long projectId;

    private Long osVersionId;

    private String serialNum;

    private Integer amount;

    private String fileId;

    private Long size;

    private String md5;

    private String coapDownloadUri;

    private String httpDownloadUri;

    private DeviceBatchNeedBindStatus needBind;

    private DeviceBatchBusinessStatus businessStatus;

    private DeviceBatchStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = CommonConstant.DATETIME_FORMAT, timezone = "UTC")
    private Date createTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = CommonConstant.DATETIME_FORMAT, timezone = "UTC")
    private Date updateTime;
}

package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.DATETIME_FORMAT;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
@EqualsAndHashCode
public class PackageInfo {
    private Long id;

    private Long projectId;

    private Long osVersionId;

    private String description;

    private String fileId;

    private Long size;

    private String md5;

    private String coapDownloadUri;

    private String httpDownloadUri;

    private PackageStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME_FORMAT, timezone = "UTC")
    private Date createTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME_FORMAT, timezone = "UTC")
    private Date updateTime;
}

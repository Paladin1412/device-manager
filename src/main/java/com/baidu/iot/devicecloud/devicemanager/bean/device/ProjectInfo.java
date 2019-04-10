package com.baidu.iot.devicecloud.devicemanager.bean.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * ProjectInfo
 *
 * @author Long Yunxiang (longyunxiang@baidu.com)
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectInfo {
    private Long id;

    private Long seriesId;

    private String accountUuid;

    private String name;

    private String appType;

    private String appId;

    private String appKey;

    private String voiceId;

    private String voiceKey;

    private String token;
}
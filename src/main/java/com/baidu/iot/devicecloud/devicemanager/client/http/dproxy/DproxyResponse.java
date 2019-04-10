package com.baidu.iot.devicecloud.devicemanager.client.http.dproxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Created by pansongsong02 on 2017/11/18.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DproxyResponse {
    private Integer status;
    private String msg;
    private Object res;
}

package com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcsUserStateResponse {
    private String name;
    private String msg;
    private ArrayNode data;
}

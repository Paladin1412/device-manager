package com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.builder;

import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean.DcsUserStateRequestBody;
import com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class DcsUserStateRequestBodyBuilder {
    public static DcsUserStateRequestBody build(String name, String clt_id) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode data = objectMapper.createObjectNode();
        data.set(DCSProxyConstant.JSON_KEY_CLT_ID, TextNode.valueOf(clt_id));
        data.set(DCSProxyConstant.JSON_KEY_TIMESTAMP, LongNode.valueOf(System.currentTimeMillis()));

        return new DcsUserStateRequestBody(name, data);
    }
}

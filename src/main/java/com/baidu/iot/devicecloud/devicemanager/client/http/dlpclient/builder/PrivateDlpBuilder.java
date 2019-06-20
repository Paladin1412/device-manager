package com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_IOT_PRIVATE_NAMESPACE;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Data
public class PrivateDlpBuilder {
    private ObjectNode data = JsonUtil.createObjectNode();
    private ObjectNode header = JsonUtil.createObjectNode();
    private ObjectNode payload = JsonUtil.createObjectNode();
    private ObjectNode toClient = JsonUtil.createObjectNode();

    public PrivateDlpBuilder(String name) {
        init();
        this.header(DLP_IOT_PRIVATE_NAMESPACE, name);
    }

    private void init() {
        data.set("to_client", toClient);
        toClient.set("header", header);
        toClient.set("payload", payload);
    }

    public PrivateDlpBuilder payload(JsonNode payload) {
        toClient.set("payload", payload);
        return this;
    }

    public PrivateDlpBuilder header(String namespace, String name) {
        header.set("namespace", TextNode.valueOf(namespace));
        header.set("name", TextNode.valueOf(name));
        header.set("messageId", TextNode.valueOf(UUID.randomUUID().toString()));
        return this;
    }
}

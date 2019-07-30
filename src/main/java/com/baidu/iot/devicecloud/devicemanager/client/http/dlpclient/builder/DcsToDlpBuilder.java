package com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_NAMESPACE_PREFIX;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_SCREEN_NAMESPACE;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Data
public class DcsToDlpBuilder {
    private ObjectNode data = JsonUtil.createObjectNode();
    private ObjectNode toClient = JsonUtil.createObjectNode();

    public DcsToDlpBuilder() {
        data.set("to_client", toClient);
    }

    public DcsToDlpBuilder payload(JsonNode payload) {
        toClient.set(DIRECTIVE_KEY_PAYLOAD, payload);
        return this;
    }

    public DcsToDlpBuilder header(ObjectNode header, Boolean isEvent) {
        if (isEvent) {
            String namespace = header.path(DIRECTIVE_KEY_HEADER_NAMESPACE).asText();
            header.set(DIRECTIVE_KEY_HEADER_NAMESPACE,
                    TextNode.valueOf(namespace.replace(DIRECTIVE_NAMESPACE_PREFIX, "dlp")));
        } else {
            header.set(DIRECTIVE_KEY_HEADER_NAMESPACE, TextNode.valueOf(DLP_SCREEN_NAMESPACE));
        }
        toClient.set(DIRECTIVE_KEY_HEADER, header);
        return this;
    }
}

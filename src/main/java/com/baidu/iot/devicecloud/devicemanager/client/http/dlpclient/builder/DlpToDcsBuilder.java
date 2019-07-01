package com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Data
@NoArgsConstructor
public class DlpToDcsBuilder {
    private ObjectNode data = JsonUtil.createObjectNode();
    private ObjectNode event = JsonUtil.createObjectNode();
    private ArrayNode clientContext = JsonUtil.createArrayNode();

    private String namespace = null;
    private String name = null;

    private boolean anEvent = false;

    public void writeEvent() {
        this.data.set("event", event);
        this.anEvent = true;
    }

    public void writeDirective() {
        this.data.set("directive", event);
        this.anEvent = false;
    }

    public DlpToDcsBuilder payload(JsonNode payload) {
        this.event.set("payload", payload);
        return this;
    }

    public DlpToDcsBuilder header(JsonNode header, String namespace) {
        ObjectNode asObject = (ObjectNode) header;
        asObject.set("namespace", TextNode.valueOf(namespace));
        this.event.set("header", asObject);

        this.namespace = namespace;
        this.name = header.path("name").asText();

        return this;
    }

    public void setClientContext(String namespace, String name, String token) {
        ObjectNode json = JsonUtil.createObjectNode();
        ObjectNode payload = JsonUtil.createObjectNode();
        ObjectNode header = JsonUtil.createObjectNode();

        header.set("namespace", TextNode.valueOf(namespace));
        header.set("name", TextNode.valueOf(name));

        payload.set("token", TextNode.valueOf(token));
        json.set("payload", payload);
        json.set("header", header);
        this.clientContext.add(json);
        this.data.set("clientContext", clientContext);
    }

    public void setClientContext(String namespace, String name, JsonNode payload) {
        ObjectNode json = JsonUtil.createObjectNode();
        ObjectNode header = JsonUtil.createObjectNode();

        header.set("namespace", TextNode.valueOf(namespace));
        header.set("name", TextNode.valueOf(name));

        json.set("payload", payload);
        json.set("header", header);
        this.clientContext.add(json);
        this.data.set("clientContext", clientContext);
    }
}

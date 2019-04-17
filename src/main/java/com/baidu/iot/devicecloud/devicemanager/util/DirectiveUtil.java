package com.baidu.iot.devicecloud.devicemanager.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_NAMESPACE;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/4/17.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class DirectiveUtil {
    // Private directive
    public static JsonNode assembleDuerPrivateDirective(final String name,
                                                        final String dialogId,
                                                        final String messageId,
                                                        final int index) {
        ObjectNode data      = JsonUtil.createObjectNode();
        ObjectNode directive = JsonUtil.createObjectNode();
        ObjectNode header    = JsonUtil.createObjectNode();
        ObjectNode payload   = JsonUtil.createObjectNode();
        ObjectNode extraData   = JsonUtil.createObjectNode();
        //
        header.set(DIRECTIVE_KEY_HEADER_NAMESPACE, TextNode.valueOf(PRIVATE_PROTOCOL_NAMESPACE));
        header.set(DIRECTIVE_KEY_HEADER_NAME, TextNode.valueOf(name));
        if (StringUtils.hasText(dialogId)) {
            header.set(DIRECTIVE_KEY_HEADER_DIALOG_ID,
                    TextNode.valueOf(dialogId));
        }

        if (!StringUtils.isEmpty(messageId)) {
            header.set(DIRECTIVE_KEY_HEADER_MESSAGE_ID,
                    TextNode.valueOf(StringUtils.hasText(messageId) ? messageId : UUID.randomUUID().toString()));
        }

        //
        directive.set(DIRECTIVE_KEY_HEADER, header);
        directive.set(DIRECTIVE_KEY_PAYLOAD, payload);

        extraData.set("timestamp", TextNode.valueOf(Long.toString(System.currentTimeMillis())));
        extraData.set("index", IntNode.valueOf(index));
        //
        data.set(DIRECTIVE_KEY_DIRECTIVE, directive);
        data.set("iot_cloud_extra", extraData);
        return data;
    }
}

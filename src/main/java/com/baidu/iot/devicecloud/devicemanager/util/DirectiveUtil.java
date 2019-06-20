package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.bean.OtaMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.COMMAND_SPEAK;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DLP_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DLP_UUID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DLP_REQUEST_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_OTA_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_OTA_UPDATE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.VOICE_OUTPUT_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.VOICE_OUTPUT_STOP_SPEAK_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.util.JsonUtil.valueToTree;

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

        header.set(DIRECTIVE_KEY_HEADER_MESSAGE_ID,
                TextNode.valueOf(StringUtils.hasText(messageId) ? messageId : UUID.randomUUID().toString()));

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

    public static JsonNode assembleDlpOtaDirective(OtaMessage message, final int index) {
        ObjectNode data      = JsonUtil.createObjectNode();
        ObjectNode directive = JsonUtil.createObjectNode();
        ObjectNode header    = JsonUtil.createObjectNode();
        ObjectNode payload   = JsonUtil.createObjectNode();

        header.set(
                DIRECTIVE_KEY_HEADER_NAMESPACE,
                TextNode.valueOf(DLP_OTA_NAMESPACE)
        );
        header.set(DIRECTIVE_KEY_HEADER_NAME, TextNode.valueOf(DLP_OTA_UPDATE));

        String messageId = message.getMessageId();
        header.set(DIRECTIVE_KEY_HEADER_MESSAGE_ID,
                TextNode.valueOf(StringUtils.hasText(messageId) ? messageId : UUID.randomUUID().toString()));

        String requestId = message.getDialogRequestId();
        if (StringUtils.hasText(requestId)) {
            payload.set(DIRECTIVE_KEY_HEADER_DLP_REQUEST_ID,
                    TextNode.valueOf(requestId));
        }

        payload.set(
                DIRECTIVE_KEY_DLP_PAYLOAD,
                valueToTree(message)
        );
        payload.set(
                DIRECTIVE_KEY_DLP_UUID,
                TextNode.valueOf(message.getUuid())
        );

        directive.set(DIRECTIVE_KEY_HEADER, header);
        directive.set(DIRECTIVE_KEY_PAYLOAD, payload);
        data.set(DIRECTIVE_KEY_DIRECTIVE, directive);

        return addExtraInfo(data, index);
    }

    public static JsonNode assembleStopSpeakDirective(final int index) {
        ObjectNode data      = JsonUtil.createObjectNode();
        ObjectNode directive = JsonUtil.createObjectNode();
        ObjectNode header    = JsonUtil.createObjectNode();
        ObjectNode payload   = JsonUtil.createObjectNode();

        directive.set(DIRECTIVE_KEY_HEADER, header);
        directive.set(DIRECTIVE_KEY_PAYLOAD, payload);
        data.set(DIRECTIVE_KEY_DIRECTIVE, directive);

        header.set(
                DIRECTIVE_KEY_HEADER_NAMESPACE,
                TextNode.valueOf(VOICE_OUTPUT_NAMESPACE)
        );
        header.set(DIRECTIVE_KEY_HEADER_NAME, TextNode.valueOf(VOICE_OUTPUT_STOP_SPEAK_NAME));
        header.set(DIRECTIVE_KEY_HEADER_MESSAGE_ID,
                TextNode.valueOf(UUID.randomUUID().toString()));
        return addExtraInfo(data, index);
    }

    public static Predicate<JsonNode> isSpeak =
            node ->
                    COMMAND_SPEAK.equalsIgnoreCase(node
                            .path(DIRECTIVE_KEY_DIRECTIVE)
                            .path(DIRECTIVE_KEY_HEADER)
                            .path(DIRECTIVE_KEY_HEADER_NAME).asText());

    public static Predicate<JsonNode> needRewrite =
            node -> StringUtils.startsWithIgnoreCase(
                    node
                            .path(DIRECTIVE_KEY_DIRECTIVE)
                            .path(DIRECTIVE_KEY_PAYLOAD)
                            .path(DIRECTIVE_KEY_PAYLOAD_URL).asText(),
                    PARAMETER_CID
            );

    public static Function<JsonNode, String> getContentId =
            node -> {
                String url = node
                        .path(DIRECTIVE_KEY_DIRECTIVE)
                        .path(DIRECTIVE_KEY_PAYLOAD)
                        .path(DIRECTIVE_KEY_PAYLOAD_URL)
                        .asText();
                if (StringUtils.hasText(url)) {
                    String[] items = StringUtils.split(url, SPLITTER_COLON);
                    if (items!= null && items.length > 1) {
                        return items[1];
                    }
                }
                return null;
            };

    public static Function<JsonNode, String> getMessageId =
            node -> node
                    .path(DIRECTIVE_KEY_DIRECTIVE)
                    .path(DIRECTIVE_KEY_HEADER)
                    .path(DIRECTIVE_KEY_HEADER_MESSAGE_ID)
                    .asText();

    public static Function<JsonNode, String> getDialogueRequestId =
            node -> node
                    .path(DIRECTIVE_KEY_DIRECTIVE)
                    .path(DIRECTIVE_KEY_HEADER)
                    .path(DIRECTIVE_KEY_HEADER_DIALOG_ID)
                    .asText();

    public static BiConsumer<JsonNode, String> rewrite =
            (node, url) -> {
                if (StringUtils.isEmpty(url)) {
                    return;
                }
                JsonNode payloadNode = node.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_PAYLOAD);
                if (payloadNode != null && payloadNode.isObject()) {
                    ObjectNode payload = (ObjectNode) payloadNode;
                    payload.set(DIRECTIVE_KEY_PAYLOAD_URL, TextNode.valueOf(url));
                }
            };

    public static JsonNode addExtraInfo(JsonNode directive, int index) {
        ObjectNode data = (ObjectNode) directive;
        ObjectNode extraData = JsonUtil.createObjectNode();
        extraData.set("timestamp", TextNode.valueOf(Long.toString(System.currentTimeMillis())));
        extraData.set("index", IntNode.valueOf(index));
        data.set("iot_cloud_extra", extraData);
        return data;
    }
}

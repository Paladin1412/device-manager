package com.baidu.iot.devicecloud.devicemanager.adapter;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_METADATA;
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
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DCS_NAMESPACE_PREFIX;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_DCS_EVENT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_DIALOGUE_FINISHED;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.assembleDuerPrivateDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isLegalType;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class Adapter {
    @SuppressWarnings("unused")
    public static List<TlvMessage> directive2TLV(List<JsonNode> directives, int type) {
        if (directives == null || !isLegalType(type)) {
            return Collections.emptyList();
        }

        return directives
                .stream()
                .map(
                        node -> {
                            try {
                                String serialized = JsonUtil.serialize(node);
                                if (StringUtils.hasText(serialized)) {
                                    RequestBody multipartBody = new MultipartBody.Builder()
                                            .setType(MultipartBody.FORM)
                                            .addPart(MultipartBody.Part.createFormData(PARAMETER_METADATA, serialized))
                                            .build();
                                    Buffer buffer = new Buffer();
                                    multipartBody.writeTo(buffer);
                                    long vlen = buffer.size();
                                    byte[] content = buffer.readByteArray();
                                    log.debug("Adapted multipartBody:\n{}", new String(content, Charsets.UTF_8));
                                    return new TlvMessage(type, vlen, content);
                                }
                            } catch (Exception e) {
                                log.error("Adapting directives from json node to tlv failed", e);
                            }
                            return null;
                        }
                )
                .collect(Collectors.toList());
    }

    public static TlvMessage directive2DataPointTLV(JsonNode directive, int type) {
        try {
            DataPointMessage assembled = directive2DataPoint(directive, null);
            byte[] bytes = JsonUtil.writeAsBytes(assembled);
            long vlen = bytes.length;
            log.debug("Adapted ASR directive:\n{}", assembled);
            return new TlvMessage(type, vlen, bytes);
        } catch (Exception e) {
            log.error("Adapting directives from json node to tlv failed", e);
        }
        return null;

    }

    public static List<TlvMessage> directive2DataPointTLV(List<JsonNode> directives, int type) {
        if (directives == null || directives.size() < 1 || !isLegalType(type)) {
            return Collections.emptyList();
        }

        // try to append DialogueFinished
        try2appendDialogueFinished(directives);

        return directives
                .stream()
                .map(
                        node -> directive2DataPointTLV(node, type)
                )
                .collect(Collectors.toList());
    }
    public static List<DataPointMessage> directive2DataPoint(List<JsonNode> directives, DataPointMessage origin) {
        if (directives == null || directives.size() < 1 || origin == null) {
            return Collections.emptyList();
        }

        // try to append DialogueFinished
        try2appendDialogueFinished(directives);

        return directives
                .stream()
                .map(node -> directive2DataPoint(node, origin))
                .collect(Collectors.toList());
    }

    public static DataPointMessage directive2DataPoint(JsonNode directive, DataPointMessage origin) {
        return directive2DataPoint(directive, DataPointConstant.DATA_POINT_DUER_DIRECTIVE, origin);
    }

    public static DataPointMessage directive2DataPoint(JsonNode directive, String path, DataPointMessage origin) {
        DataPointMessage assembled = new DataPointMessage();
        Optional.ofNullable(origin).ifPresent(
                dp -> BeanUtils.copyProperties(origin, assembled)
        );
        assembled.setVersion(origin != null ? origin.getVersion() : DEFAULT_VERSION);
        assembled.setId(origin != null ? origin.getId() : IdGenerator.nextId());
        assembled.setCode(CoapConstant.COAP_METHOD_PUT);
        assembled.setPath(PathUtil.lookAfterPrefix(path));
        assembled.setPayload(JsonUtil.serialize(directive));

        return assembled;
    }

    private static void try2appendDialogueFinished(List<JsonNode> directives) {
        try {
            // obtain dialogueRequestId from first directive
            JsonNode first = directives.get(0);
            JsonNode header = first.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_HEADER);
            String dialogueRequestId = header.path(DIRECTIVE_KEY_HEADER_DIALOG_ID).asText();
            directives.add(assembleDuerPrivateDirective(
                    PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                    dialogueRequestId,
                    header.path(DIRECTIVE_KEY_HEADER_MESSAGE_ID).asText(),
                    directives.size() + 1
            ));
        } catch (Exception e) {
            log.error("Trying to append DialogueFinished at last failed", e);
        }
    }

    public static JsonNode dlp2Dcs(JsonNode dlp, String uuid) {
        if (dlp != null) {
            if (dlp.has("to_server")) {
                JsonNode toServer = dlp.path("to_server");
                String dlpUuid = toServer.path("uuid").asText();
                return dlp2Dcs(toServer, dlpUuid, DIRECTIVE_KEY_DIRECTIVE);
            } else if (dlp.has("to_client")) {
                return dlp2Dcs(dlp.path("to_client"), uuid, JSON_KEY_DCS_EVENT);
            }
        }
        return NullNode.getInstance();
    }


    private static JsonNode dlp2Dcs(JsonNode dlp, String uuid, String rootName) {
        ObjectNode assembled = JsonUtil.createObjectNode();
        ObjectNode data = JsonUtil.createObjectNode();
        ObjectNode header = JsonUtil.createObjectNode();
        ObjectNode payload = JsonUtil.createObjectNode();
        JsonNode headerNode = dlp.path(DIRECTIVE_KEY_HEADER);
        String namespace = headerNode.path(DIRECTIVE_KEY_HEADER_NAMESPACE).asText();
        if (StringUtils.hasText(namespace)) {
            header.set(DIRECTIVE_KEY_HEADER_NAMESPACE, TextNode.valueOf(PRIVATE_PROTOCOL_NAMESPACE.equalsIgnoreCase(namespace) ? namespace : DLP_DCS_NAMESPACE_PREFIX + namespace));
            header.set(DIRECTIVE_KEY_HEADER_NAME, headerNode.path(DIRECTIVE_KEY_HEADER_NAME));
            header.set(DIRECTIVE_KEY_HEADER_MESSAGE_ID, headerNode.path(DIRECTIVE_KEY_HEADER_MESSAGE_ID));
            payload.set(DIRECTIVE_KEY_DLP_PAYLOAD, dlp.path(DIRECTIVE_KEY_PAYLOAD));
            if (StringUtils.hasText(headerNode.path(DIRECTIVE_KEY_HEADER_DIALOG_ID).asText())) {
                payload.set(DIRECTIVE_KEY_HEADER_DLP_REQUEST_ID, headerNode.path(DIRECTIVE_KEY_HEADER_DIALOG_ID));
            }
            if (StringUtils.hasText(uuid)) {
                payload.set(DIRECTIVE_KEY_DLP_UUID, TextNode.valueOf(uuid));
            }
            data.set(DIRECTIVE_KEY_HEADER, header);
            data.set(DIRECTIVE_KEY_PAYLOAD, payload);
            assembled.set(rootName, data);
            return assembled;
        }
        return NullNode.getInstance();
    }


}

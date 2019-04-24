package com.baidu.iot.devicecloud.devicemanager.adapter;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_DIALOGUE_FINISHED;
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
                                            .addPart(MultipartBody.Part.createFormData("metadata", serialized))
                                            .build();
                                    Buffer buffer = new Buffer();
                                    multipartBody.writeTo(buffer);
                                    long vlen = buffer.size();
                                    byte[] content = buffer.readByteArray();
                                    log.debug("Adapted multipartBody:\n{}", new String(content, Charsets.UTF_8));
                                    return new TlvMessage(type, vlen, content);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                )
                .collect(Collectors.toList());
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
                        node -> {
                            try {
                                DataPointMessage assembled = directive2DataPoint0(node, null);
                                byte[] bytes = JsonUtil.writeAsBytes(assembled);
                                long vlen = bytes.length;
                                log.debug("Adapted ASR directive:\n{}", assembled);
                                return new TlvMessage(type, vlen, bytes);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
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
                .map(node -> directive2DataPoint0(node, origin))
                .collect(Collectors.toList());
    }

    private static DataPointMessage directive2DataPoint0(JsonNode directive, DataPointMessage origin) {
        DataPointMessage assembled = new DataPointMessage();
        assembled.setVersion(origin != null ? origin.getVersion() : DEFAULT_VERSION);
        assembled.setId(origin != null ? origin.getId() : IdGenerator.nextId());
        assembled.setCode(CoapConstant.COAP_METHOD_PUT);
        assembled.setPath(PathUtil.lookAfterPrefix(DataPointConstant.DATA_POINT_DUER_DIRECTIVE));
        assembled.setPayload(JsonUtil.serialize(directive));
        return assembled;
    }

    public static void try2appendDialogueFinished(List<JsonNode> directives) {
        try {
            // obtain dialogueRequestId from first directive
            JsonNode first = directives.get(0);
            JsonNode header = first.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_HEADER);
            String dialogueRequestId = header.path(DIRECTIVE_KEY_HEADER_DIALOG_ID).asText();
            if (StringUtils.hasText(dialogueRequestId)) {
                directives.add(assembleDuerPrivateDirective(
                        PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                        dialogueRequestId,
                        header.path(DIRECTIVE_KEY_HEADER_MESSAGE_ID).asText(),
                        directives.size() + 1
                ));
            }
        } catch (Exception e) {
            log.error("Trying to append DialogueFinished at last failed: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}

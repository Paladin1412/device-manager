package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.config.AppConfiguration;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class JsonUtil {
    private static ObjectMapper objectMapper;

    static {
        Jackson2ObjectMapperFactoryBean factoryBean = new Jackson2ObjectMapperFactoryBean();
        factoryBean.setFeaturesToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        factoryBean.setFeaturesToEnable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        factoryBean.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
        objectMapper = factoryBean.getObject();
    }

    public static JsonUtil jsonUtilFactory() {
        return new JsonUtil(new AppConfiguration().objectMapperFactory());
    }

    @Autowired
    public JsonUtil(Jackson2ObjectMapperFactoryBean objectMapperFactory) {
        objectMapper = objectMapperFactory.getObject();
    }

    @Nullable
    public static String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to serialize json. object={}", object);
            log.error("The stack traces listed below", e);
        }
        return null;
    }

    @Nullable
    public static <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize json. json={}, type={}", json, type);
            log.error("The stack traces listed below", e);
        }
        return null;
    }

    public static <T> List<T> deserializeAsList(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.error("Failed to deserialize json. json={}, type={}", json, type);
            log.error("The stack traces listed below", e);
        }
        return new ArrayList<>();
    }

    public static JsonNode readTree(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (IOException ignore) {
        }
        return NullNode.getInstance();
    }

    public static JsonNode readTree(byte[] content) {
        try {
            return objectMapper.readTree(content);
        } catch (IOException ignore) {
        }
        return NullNode.getInstance();
    }

    public static JsonNode readTree(InputStream in) {
        try {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            log.error("Reading input stream as tree failed", e);
        }
        return NullNode.getInstance();
    }

    public static JsonNode valueToTree(Object o) {
        try {
            return objectMapper.valueToTree(o);
        } catch (IllegalArgumentException e) {
            log.error("Writing object {} as tree failed", o, e);
        }
        return createObjectNode();
    }

    public static byte[] writeAsBytes(Object object) throws JsonProcessingException {
        return objectMapper.writer().writeValueAsBytes(object);
    }

    public static String writeAsString(Object object) throws JsonProcessingException {
        return objectMapper.writer().writeValueAsString(object);
    }

    public static void appendAsQueryParams(UriBuilder builder, Object o) {
        Map<String,String> converted = objectMapper.convertValue(o, new TypeReference<Map<String,String>>() {});
        converted.forEach(builder::queryParam);
    }

    public static ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }
    public static ArrayNode createArrayNode() {
        return objectMapper.createArrayNode();
    }

    public static JsonNode assembleDirective(String rootName,
                                             String namespace,
                                             String name,
                                             String messageId,
                                             String dialogRequestId,
                                             String uuid,
                                             Object payloadObject) {
        ObjectNode assembled = createObjectNode();
        ObjectNode data = createObjectNode();
        ObjectNode header = createObjectNode();

        assembled.set(rootName, data);
        data.set(DIRECTIVE_KEY_HEADER, header);
        data.set(DIRECTIVE_KEY_PAYLOAD, payloadObject == null ? createObjectNode() : objectMapper.valueToTree(payloadObject));
        if ("to_server".equalsIgnoreCase(rootName) && StringUtils.hasText(uuid)) {
            data.set("uuid", TextNode.valueOf(uuid));
        }

        header.set(DIRECTIVE_KEY_HEADER_NAMESPACE, TextNode.valueOf(namespace));
        header.set(DIRECTIVE_KEY_HEADER_NAME, TextNode.valueOf(name));
        header.set(DIRECTIVE_KEY_HEADER_MESSAGE_ID,
                TextNode.valueOf(StringUtils.hasText(messageId) ? messageId : UUID.randomUUID().toString()));
        if (StringUtils.hasText(dialogRequestId)) {
            header.set(DIRECTIVE_KEY_HEADER_DIALOG_ID,
                    TextNode.valueOf(dialogRequestId));
        }

        return assembled;
    }

    public static JsonNode assembleToClientUpdateProgress(JsonNode otaEvent) {
        ObjectNode data = JsonUtil.createObjectNode();
        ObjectNode toClient = JsonUtil.createObjectNode();
        ObjectNode header = JsonUtil.createObjectNode();
        ObjectNode payload = JsonUtil.createObjectNode();
        data.set("to_client", toClient);
        header.set("namespace", TextNode.valueOf("dlp.system_update"));
        header.set("name", TextNode.valueOf("UpdateProgressStatus"));
        header.set("messageId", TextNode.valueOf(UUID.randomUUID().toString()));
        toClient.set("header", header);
        String updateState;
        int percent = -1;
        int event = otaEvent.path("event").asInt(-1);
        if (event == 10) {
            // OTA_EVENT_INSTALLED
            updateState = "install_success";
        } else if(event == 0) {
            updateState = "begin";
        } else if (event == 1
                || event == 2
                || event == 3
                || event == 6
                || event > 7) {
            // OTA_EVENT_CONNECT_FAIL(1), OTA_EVENT_CONNECTION_LOST(2),
            // OTA_EVENT_TIMEOUT(3)
            // OTA_EVENT_DOWNLOAD_FAIL(6)
            // OTA_EVENT_IMAGE_INVALID(8),
            // OTA_EVENT_WRITE_ERROR(9),
            // OTA_EVENT_REJECT(12),
            updateState = "install_failed";
        } else {
            updateState = "download";
            try {
                percent = (int) (otaEvent.path("percent").asDouble(0) * 100);
            } catch (Exception ignore) {
            }
        }
        payload.set("updateState", TextNode.valueOf(updateState));
        payload.set("percent", IntNode.valueOf(percent));

        toClient.set("payload", payload);
        return data;
    }
}

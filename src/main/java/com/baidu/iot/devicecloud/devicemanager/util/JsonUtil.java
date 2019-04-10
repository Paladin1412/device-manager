package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.config.AppConfiguration;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

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
            log.error("Failed to serialize json. object={}", object, e);
        }
        return null;
    }

    @Nullable
    public static <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize json. json={}, type={}", json, type, e);
        }
        return null;
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
        } catch (IOException ignore) {
            ignore.printStackTrace();
        }
        return NullNode.getInstance();
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
}

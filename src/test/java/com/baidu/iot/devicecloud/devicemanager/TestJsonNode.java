package com.baidu.iot.devicecloud.devicemanager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.io.IOException;
import java.sql.Time;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/17.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestJsonNode {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testEdit() throws IOException {
        JsonNode jsonNode = objectMapper.readTree("{\"transaction\": 100}");
        System.out.println(jsonNode.toString());
        ObjectNode objectNode = (ObjectNode)jsonNode;
        JsonNode tc = jsonNode.path("transaction");
        objectNode.replace("transaction", TextNode.valueOf(tc.asText()));
        System.out.println(jsonNode.toString());
    }

    @Test
    public void testMap() {
        Map<String, String> map = new HashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        System.out.println(map);
    }

    @Test
    public void testConvert() throws IOException {
        Map<String, Object> map = objectMapper.convertValue(NullNode.getInstance(), new TypeReference<Map<String,Object>>() {});
        System.out.println(map);
        JsonNode jsonNode = objectMapper.readTree("{\"transaction\":\"1542851\",\"description\":null,\"event\":4,\"percent\":0.700104}");
        JsonNode jsonNode1 = objectMapper.readTree("123");
        map = objectMapper.convertValue(jsonNode, new TypeReference<Map<String,Object>>() {});
        System.out.println(map);
        System.out.println(objectMapper.writeValueAsString(map));
    }

    @Test
    public void test0() {
        System.out.println(Long.valueOf("6", 16));
    }

    @Test
    public void testTime() {
        Time startTime = Time.valueOf("00:00:00");
        Time endTime = Time.valueOf("23:59:00");
        Time nowTime = new Time(new Date().getTime());
        System.out.println(nowTime.after(startTime) && nowTime.before(endTime));
    }
}

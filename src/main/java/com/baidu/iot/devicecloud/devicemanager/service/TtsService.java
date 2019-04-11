package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.TtsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/31.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class TtsService {
    private final TtsProxyClient client;

    @Autowired
    public TtsService(TtsProxyClient client) {
        this.client = client;
    }

    public void requestTTSAsync(TtsRequest message, boolean isPre) {
        CompletableFuture<Response> future = client.requestTtsAsync(message, isPre);
        future.handle(((response, throwable) -> {
            if (response.isSuccessful()) {
                log.debug("TTS Proxy async request succeed.");
            }
            return null;
        }));
    }

    public Map<String, String> requestTTSSync(TtsRequest message, boolean isPre) {
        Map<String, String> urlmap = new HashMap<>();
        Response response = client.requestTtsSync(message, isPre);
        if (response != null && response.isSuccessful() && !isPre) {
            ResponseBody body = response.body();
            if (body != null) {
                try {
                    JsonNode res = JsonUtil.readTree(body.string());
                    JsonNode data = res.get("data");
                    if (data != null && data.isTextual()) {
                        JsonNode dataObject = JsonUtil.readTree(data.asText());
                        if (dataObject != null && dataObject.has("url") && dataObject.has("keymap")) {
                            String url = dataObject.get("url").asText();
                            JsonNode keymap = dataObject.get("keymap");
                            Iterator<Map.Entry<String, JsonNode>> it = keymap.fields();
                            it.forEachRemaining(entry -> urlmap.put(entry.getKey(), PathUtil.lookAfterSuffix(url) + entry.getValue().asText()));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    response.close();
                }
            }
        }
        return urlmap;
    }
}

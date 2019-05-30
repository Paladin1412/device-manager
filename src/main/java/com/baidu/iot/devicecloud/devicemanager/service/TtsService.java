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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;


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

    public void requestTTSAsync(TtsRequest message, boolean isPre, Map<String, String> keysMap) {
        CompletableFuture<Response> future = client.requestTtsAsync(message, isPre, keysMap);
        future.handle(((response, throwable) -> {
            try {
                if (response != null && response.isSuccessful()) {
                    log.debug("TTS Proxy async request succeed. response: {}", response);
                }
                return null;
            } finally {
                close(response);
            }
        }));
    }

    public CompletableFuture<Response> cacheAudioAsync(TtsRequest message, String contentId, String key) {
        return client.cacheAudioAsync(message, contentId, key);
    }

    public Map<String, String> requestTTSSync(TtsRequest message, boolean isPre, Map<String, String> keysMap) {
        Map<String, String> urlmap = new HashMap<>();
        try(Response response = client.requestTtsSync(message, isPre, keysMap)) {
            if (response != null && response.isSuccessful() && !isPre) {
                ResponseBody body = response.body();
                if (body != null) {
                    decodeTtsProxyResponse(body.string(), urlmap);
                }
            }
        } catch (Exception e) {
            log.error("Requesting tts in a sync way failed", e);
        }
        return urlmap;
    }

    public Map<String, String> requestAudioUrl(String cuid, String sn, String cid, int messageType) {
        Map<String, String> urlmap = new HashMap<>();
        try(Response response = client.requestAudioUrl(cuid, sn, cid, messageType)) {
            if (response != null && response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body != null) {
                    decodeTtsProxyResponse(body.string(), urlmap);
                }
            }
        } catch (Exception e) {
            log.error("Requesting tts url in a sync way failed", e);
        }
        return urlmap;
    }

    public void decodeTtsProxyResponse(String responseString, Map<String, String> urlmap) {
        JsonNode res = JsonUtil.readTree(responseString);
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
    }
}

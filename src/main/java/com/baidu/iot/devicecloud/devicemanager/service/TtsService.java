package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.TtsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.baidu.iot.log.Log;
import com.baidu.iot.log.LogProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger infoLog = LoggerFactory.getLogger("infoLog");
    private static final LogProvider logProvider = LogProvider.getInstance();

    private final TtsProxyClient client;

    @Autowired
    public TtsService(TtsProxyClient client) {
        this.client = client;
    }

    public void requestTTSAsync(TtsRequest message, boolean isPre, Map<String, String> keysMap) {
        Log spanLog = logProvider.get(message.getSn());
        spanLog.time("pre_tts");
        infoLog.info("[ASR] Start to process the pre tts. logid:{}", spanLog.getLogId());
        CompletableFuture<Response> future = client.requestTtsAsync(message, isPre, keysMap);
        future.handle(((response, throwable) -> {
            try {
                infoLog.info(spanLog.format("[ASR] Finished to process pre tts."));
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
        Log spanLog = logProvider.get(message.getSn());
        spanLog.time("pre_tts");
        infoLog.info("[ASR] Start to process the final tts. logid:{}", spanLog.getLogId());

        Map<String, String> urlmap = new HashMap<>();
        try(Response response = client.requestTtsSync(message, isPre, keysMap)) {
            infoLog.info(spanLog.format("[ASR] Finished to process the final tts."));
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
        Log spanLog = logProvider.get(sn);
        spanLog.time("tts_url");
        infoLog.info("[ASR] Start to request audio url. logid:{}", spanLog.getLogId());

        Map<String, String> urlmap = new HashMap<>();
        try(Response response = client.requestAudioUrl(cuid, sn, cid, messageType)) {
            infoLog.info(spanLog.format("[ASR] Finished to request audio url."));
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

package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.InteractiveType;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.DlpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder.DlpToDcsBuilder;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_NAMESPACE_PREFIX;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DCS_NAMESPACE_PREFIX;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DlpService implements InitializingBean {
    private static final String connectRedisFormat = "cloud:interactive:client:%s:%s";

    private final DlpClient dlpClient;

    private static final long connectExpire = 24 * 60 * 60;
    private Cache<String, Boolean> cache;

    @Autowired
    public DlpService(DlpClient dlpClient) {
        this.dlpClient = dlpClient;
    }

    @Override
    public void afterPropertiesSet() {
        cache = CacheBuilder.newBuilder()
                .concurrencyLevel(500)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .initialCapacity(2000)
                .maximumSize(100000)
                .build();
    }

    @SuppressWarnings("SameParameterValue")
    private Boolean getClientByDevice(final String uuid, final InteractiveType type) {
        try {
            return cache.get(uuid + "-" + type, () -> Optional.ofNullable(clientStatus(uuid, type)).orElse(false));
        } catch (Exception ignore) {
            return false;
        }
    }

    private Boolean clientStatus(String uuid, InteractiveType type) {
        return DproxyClientProvider
                .getInstance().get(String.format(connectRedisFormat, uuid, type), Boolean.class);
    }

    public void modifyClientStatus(String uuid, InteractiveType type, Boolean status) {
        DproxyClientProvider
                .getInstance().setex(String.format(connectRedisFormat, uuid, type), connectExpire, status);
        cache.invalidate(uuid + "-" + type);
    }

    public void forceSendToDlp(final String uuid, final JsonNode data) {
        dlpClient.sendToDlp(uuid, data);
    }

    public void sendToDlp(final String uuid, final JsonNode data) {
        if (getClientByDevice(uuid, InteractiveType.DLP)) {
            forceSendToDlp(uuid, data);
        }
    }

    public DlpToDcsBuilder buildDcsJson(String data, String deviceUuid) {
        try {
            JsonNode jsonData = JsonUtil.readTree(data);
            if (!jsonData.has("to_server")) {
                return null;
            }
            jsonData = jsonData.path("to_server");

            JsonNode clientContext = null;
            if (jsonData.has("clientContext")) {
                clientContext = jsonData.path("clientContext");
            }
            log.debug("dlp clientContext={}", clientContext);

            String name = jsonData.path("header").path("name").asText("");
            String namespace = DLP_DCS_NAMESPACE_PREFIX;
            DlpToDcsBuilder builder = new DlpToDcsBuilder();
            switch (name) {
                case "LinkClicked":
                    builder.writeEvent();
                    namespace += "screen";
                    String token = jsonData.path("payload").path("token").asText();
                    builder.setClientContext(namespace, "ViewState", token);
                    break;
                case "ButtonClicked":
                case "RadioButtonClicked":
                    builder.writeEvent();
                    namespace += "form";
                    if (clientContext != null) {
                        builder.setClientContext(
                                clientContext.path("header").path("namespace").asText()
                                        .replaceFirst("dlp", DIRECTIVE_NAMESPACE_PREFIX),
                                clientContext.path("header").path("name").asText(),
                                clientContext.path("payload"));
                    }
                    break;
                case "SetVolume":
                case "AdjustVolume":
                case "SetMute":
                    builder.writeDirective();
                    namespace += "speaker_controller";
                    break;

                case "StopSpeak":
                    builder.writeDirective();
                    namespace += "voice_output";
                    break;
                default:
                    return null;
            }
            builder.payload(jsonData.get("payload"))
                    .header(jsonData.get("header"), namespace);
            log.debug("stream device={}, data={}", deviceUuid, builder.getData());
            return builder;

        } catch (Exception e) {
            log.debug("buildDcsJson exception", e);
            return null;
        }
    }
}

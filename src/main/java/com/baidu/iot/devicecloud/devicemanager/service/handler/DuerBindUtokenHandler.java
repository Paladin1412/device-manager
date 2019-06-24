package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.adapter.Adapter;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.DeviceIamClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.TtsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.service.PushService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_REQUEST;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_INVALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_FOUND;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.COMMAND_SPEAK;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.PRIVATE_BIND_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_BIND_UTOKEN;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.JsonUtil.assembleDirective;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/24.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DuerBindUtokenHandler extends AbstractLinkableDataPointHandler {
    private static final String SUCCESS_CONTENT = "绑定成功啦！宝贝我们一起聊天吧！";
    private static final String FAIL_CONTENT = "阿哦，绑定失败了！宝贝别灰心，重新绑定一次吧！";

    private final DeviceIamClient deviceIamClient;
    private final TtsProxyClient ttsProxyClient;
    private final PushService pushService;

    private Cache<String, String> boundCache;

    @Autowired
    public DuerBindUtokenHandler(DeviceIamClient deviceIamClient, TtsProxyClient ttsProxyClient, PushService pushService) {
        this.deviceIamClient = deviceIamClient;
        this.ttsProxyClient = ttsProxyClient;
        this.pushService = pushService;

        this.boundCache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(Duration.ofMinutes(10))
                .removalListener(LogUtils.REMOVAL_LOGGER.apply(log))
                .build();
    }

    @Override
    boolean canHandle(String type) {
        return DATA_POINT_DUER_BIND_UTOKEN.equalsIgnoreCase(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        return Mono.from(Mono.fromFuture(
                deviceIamClient.bindUToken(message)
                .handleAsync(
                        (r, t) -> {
                            Map<String, Object> result = new HashMap<>();
                            try {
                                String content;
                                int statusCode = r.code();
                                if (200 == statusCode || 208 == statusCode) {
                                    content = SUCCESS_CONTENT;
                                } else {
                                    content = FAIL_CONTENT;
                                }
                                result.put("content", content);
                                result.put("code", statusCode);
                                return result;
                            } finally {
                                close(r);
                            }
                        }
                )
        )
                .flatMap(map -> {
                    String content = (String) map.get("content");
                    int code = (int) map.get("code");
                    return ttsHelper(content, message, code);
                })
                .flatMap(baseResponse -> {
                    if (baseResponse.getCode() == MESSAGE_SUCCESS_CODE) {
                        return Mono.just(transform(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID));
                    }
                    return Mono.just(transform(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_INVALID));
                })
                .switchIfEmpty(Mono.defer(() -> Mono.just(transform(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_FOUND))))
                .onErrorResume(t -> Mono.just(transform(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_REQUEST)))
        );
    }

    private Mono<BaseResponse> ttsHelper(String content, DataPointMessage message, int code) {
        if (StringUtils.hasText(content)) {
            String ttsUrl = getTtsUrl(content, message);
            if (StringUtils.hasText(ttsUrl)) {
                JsonNode payload = assemblePayload(ttsUrl, code);
                String messageId = Optional.ofNullable(message.getSn()).orElse(UUID.randomUUID().toString());
                JsonNode directive = assembleDirective(
                        DIRECTIVE_KEY_DIRECTIVE,
                        PRIVATE_BIND_NAMESPACE,
                        COMMAND_SPEAK,
                        messageId,
                        null,
                        message.getDeviceId(),
                        payload
                );
                return pushService.push(Adapter.directive2DataPoint(directive, message));
            }
        }
        return Mono.empty();
    }

    private String getTtsUrl(String content, DataPointMessage message) {
        try {
            return boundCache.get(message.getDeviceId(), () -> {
                try(Response response = ttsProxyClient.requestText2VoiceAsync(content, message)) {
                    if (response != null && response.isSuccessful()) {
                        ResponseBody body = response.body();

                        if (body != null) {
                            JsonNode res = JsonUtil.readTree(body.bytes());
                            JsonNode data = res.get("data");
                            if (data != null) {
                                if (data.isTextual()) {
                                    JsonNode dataObject = JsonUtil.readTree(data.asText());
                                    return dataObject.path("url").asText();
                                }
                                return data.path("url").asText();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Requesting text to voice failed", e);
                }
                return "";
            });
        } catch (ExecutionException e) {
            log.error("Getting tts url failed", e);
        }
        return null;
    }

    private JsonNode assemblePayload(String url, int code) {
        ObjectNode payload = JsonUtil.createObjectNode();
        payload.set("url", TextNode.valueOf(url));
        payload.set("format", TextNode.valueOf("AUDIO_MPEG"));
        payload.set("code", TextNode.valueOf(Integer.toString(code)));
        return payload;
    }

    private DataPointMessage transform(DataPointMessage origin, int code) {
        origin.setPath(null);
        origin.setPayload(null);
        origin.setCode(code);
        return origin;
    }
}

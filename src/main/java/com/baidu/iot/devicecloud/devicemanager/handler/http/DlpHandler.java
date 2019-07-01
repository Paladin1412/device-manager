package com.baidu.iot.devicecloud.devicemanager.handler.http;

import com.baidu.iot.devicecloud.devicemanager.adapter.Adapter;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.InteractiveOnlineRequest;
import com.baidu.iot.devicecloud.devicemanager.bean.OtaMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder.PrivateDlpBuilder;
import com.baidu.iot.devicecloud.devicemanager.service.DlpService;
import com.baidu.iot.devicecloud.devicemanager.service.OtaService;
import com.baidu.iot.devicecloud.devicemanager.service.PushService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.COMMAND_STOP_SPEAK;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DEVICE_ONLINE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.GET_STATUS;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_DLP;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_PRIVATE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_DIALOGUE_FINISHED;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.assembleDuerPrivateDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.assembleStopSpeakDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deviceMayNotOnline;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DlpHandler {
    private final DlpService dlpService;
    private final PushService pushService;
    private final OtaService otaService;

    @Autowired
    public DlpHandler(DlpService dlpService, PushService pushService, OtaService otaService) {
        this.dlpService = dlpService;
        this.pushService = pushService;
        this.otaService = otaService;
    }

    /**
     * Dealing all <code>to_server</code> requests
     * @param request the original dlp request
     * @return {@link Mono}&lt;{@link ServerResponse}&gt;
     */
    @NonNull
    public Mono<ServerResponse> deal(ServerRequest request) {
        String deviceId = request.queryParam("deviceId").orElse("");
        String logId = request.queryParam("Log-Id").orElse(null);
        if (StringUtils.isEmpty(deviceId)) {
            return ServerResponse.badRequest().body(BodyInserters.fromObject("deviceId is null"));
        }

        return request.body(BodyExtractors.toMono(String.class))
                .flatMap(body -> {
                    JsonNode jsonNode = JsonUtil.readTree(body);
                    if (jsonNode.has("to_server")) {
                        JsonNode toServer = jsonNode.path("to_server");
                        String namespace = toServer.path("header").path("namespace").asText();
                        if (StringUtils.hasText(namespace)) {
                            String name = toServer.path("header").path("name").asText();
                            if (namespace.startsWith("dlp")) {
                                switch (namespace) {
                                    case "dlp.system_update": {
                                        return otaService.deal(deviceId, name, toServer);
                                    }
                                    case "dlp.location": {
                                        break;
                                    }
                                    default: {
                                        JsonNode parsed = Adapter.dlp2Dcs(jsonNode, null);
                                        if (!parsed.isNull()) {
                                            String messageId = parsed.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_HEADER).path(DIRECTIVE_KEY_HEADER_MESSAGE_ID).asText("");
                                            DeviceResource deviceResource = getDeviceInfoFromRedis(deviceId);
                                            if (deviceResource != null && StringUtils.hasText(deviceResource.getCltId())) {
                                                DataPointMessage assembled = Adapter.directive2DataPoint(parsed, DATA_POINT_DUER_DLP, null);
                                                assembled.setCltId(deviceResource.getCltId());
                                                assembled.setDeviceId(deviceId);
                                                assembled.setLogId(messageId);
                                                pushService.justPush(assembled);
                                                return ServerResponse.ok().build();
                                            }
                                        }
                                        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(failedResponses.apply(logId, "Adapting dlp to dcs failed")));
                                    }
                                }

                            } else if (PRIVATE_PROTOCOL_NAMESPACE.equalsIgnoreCase(namespace)) {
                                if (GET_STATUS.equalsIgnoreCase(name)) {
                                    return getStatus(jsonNode, deviceId);
                                } else if (COMMAND_STOP_SPEAK.equalsIgnoreCase(name)) {
                                    stopSpeak();
                                    return ServerResponse.ok().build();
                                }
                            }
                        }
                    }
                    return ServerResponse.badRequest().build();
                });
    }

    @NonNull
    public Mono<ServerResponse> dlpStatus(ServerRequest request) {
        return request.body(BodyExtractors.toMono(InteractiveOnlineRequest.class))
                .flatMap(iRequest -> {
                    dlpService.modifyClientStatus(iRequest.getDeviceId(), iRequest.getType(), iRequest.getStatus());
                    return ServerResponse.ok().build();
                });
    }

    /**
     * PAAS CORE AGENT would call this api to make the device executing the ota update.
     * @param request the original request, the payload should contains the fields in {@link OtaMessage}
     * @return {@link Mono}&lt;{@link ServerResponse}&gt;
     */
    @NonNull
    public Mono<ServerResponse> otaUpgrade(ServerRequest request) {
        String uuid = request.pathVariable("uuid");
        return request.body(BodyExtractors.toMono(OtaMessage.class))
                .flatMap(message -> otaService.upgrade(uuid, message))
                .onErrorResume(t -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(t.getMessage())));
    }

    private void stopSpeak() {
        JsonNode directive = assembleStopSpeakDirective(1);
        JsonNode dialogueFinished = assembleDuerPrivateDirective(
                PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                null,
                null,
                2);
        pushService.justPush(Adapter.directive2DataPoint(directive, null));
        pushService.justPush(Adapter.directive2DataPoint(dialogueFinished, null));
    }

    private Mono<ServerResponse> getStatus(JsonNode data, String deviceUuid) {
        DeviceResource deviceResource = getDeviceInfoFromRedis(deviceUuid);
        if (deviceResource != null && StringUtils.hasText(deviceResource.getCltId())) {
            String cltId = deviceResource.getCltId();
            String messageId = data.path(DIRECTIVE_KEY_HEADER).path(DIRECTIVE_KEY_HEADER_MESSAGE_ID).asText();
            JsonNode directive = assembleDuerPrivateDirective(
                    GET_STATUS,
                    null,
                    messageId,
                    1);

            DataPointMessage assembled = Adapter.directive2DataPoint(directive, DATA_POINT_DUER_PRIVATE, null);
            assembled.setCltId(cltId);
            assembled.setDeviceId(deviceUuid);
            assembled.setLogId(messageId);
            pushService.prepareAckPush(assembled);
            String key = assembled.getKey();

            JsonNode dialogueFinished = assembleDuerPrivateDirective(
                    PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                    null,
                    null,
                    2);
            DataPointMessage assembled1 = Adapter.directive2DataPoint(dialogueFinished, DATA_POINT_DUER_DIRECTIVE, null);
            assembled1.setCltId(cltId);
            assembled1.setDeviceId(deviceUuid);
            assembled1.setLogId(messageId);
            return Mono.from(
                    Flux.just(assembled, assembled1)
                    .flatMapSequential(pushService::push)
                    .take(1)
                    .flatMap(response -> {
                        if (response.getCode() == MESSAGE_SUCCESS_CODE) {
                            return pushService
                                    .check(assembled, key, Collections.singletonList(assembled.getId()))
                                    .timeout(Duration.ofSeconds(5), Mono.just(failedResponses.apply(key, String.format("Waiting ack timeout. key:%s", key))))
                                    .flatMap(baseResponse -> {
                                        if (baseResponse.getCode() == MESSAGE_SUCCESS_CODE) {
                                            dlpService.sendToDlp(deviceUuid, new PrivateDlpBuilder(DLP_DEVICE_ONLINE).getData());
                                            dlpService.forceSendToDlp(deviceUuid,
                                                    JsonUtil.readTree(baseResponse.getData()));
                                            return ServerResponse.noContent().build();
                                        }
                                        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(baseResponse));
                                    });
                        } else {
                            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(response));
                        }
                    })
                            .doFinally(signalType -> pushService.unPool(key))
            );
        }
        return deviceMayNotOnline.get().apply(deviceUuid);
    }
}

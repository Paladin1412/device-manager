package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.adapter.Adapter;
import com.baidu.iot.devicecloud.devicemanager.bean.CheckOtaResult;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.OtaMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.OtaTask;
import com.baidu.iot.devicecloud.devicemanager.bean.OtaTaskCreateRequest;
import com.baidu.iot.devicecloud.devicemanager.bean.PackageInfo;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.client.http.paasapiclient.PaasApiClient;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_LF;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_OTA_UPDATE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_OTA_UPDATE_PROGRESS;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.GET_STATUS;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_DLP;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.assembleDlpOtaDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deviceMayNotOnline;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.JsonUtil.assembleDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.JsonUtil.assembleToClientUpdateProgress;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class OtaService {
    private final PaasApiClient paasApiClient;
    private final DlpService dlpService;
    private final PushService pushService;

    @Autowired
    public OtaService(PaasApiClient paasApiClient, DlpService dlpService, PushService pushService) {
        this.paasApiClient = paasApiClient;
        this.dlpService = dlpService;
        this.pushService = pushService;
    }

    public Mono<ServerResponse> deal(String uuid, String name, JsonNode data) {
        String requestId = data.path(DIRECTIVE_KEY_HEADER_DIALOG_ID).asText();
        if (GET_STATUS.equalsIgnoreCase(name)) {
            return getOTAStatus(uuid, requestId);
        }
        if (DLP_OTA_UPDATE.equalsIgnoreCase(name)) {
            JsonNode payload = data.path("payload");
            CheckOtaResult otaResult = JsonUtil.deserialize(payload.toString(), CheckOtaResult.class);
            if (otaResult != null) {
                OtaTaskCreateRequest createRequest = new OtaTaskCreateRequest();
                BeanUtils.copyProperties(otaResult, createRequest);

                return createTask(createRequest)
                        .flatMap(otaTask -> ServerResponse.noContent().build())
                        .onErrorResume(t -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(t.getMessage())));

            }
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject("Checking ota result failed"));
        }
        if (DLP_OTA_UPDATE_PROGRESS.equalsIgnoreCase(name)) {
            return getTask(uuid)
                    .flatMap(jsonNode -> {
                        List<OtaTask> tasks = JsonUtil.deserializeAsList(jsonNode.path("data").toString(), OtaTask.class);
                        if (tasks.size() > 0) {
                            OtaTask task = tasks.get(0);
                            if (task != null) {
                                String otaEvents = task.getEventLog();
                                if (StringUtils.hasText(otaEvents)) {
                                    ArrayNode orderedOtaEvents = orderedEvents(otaEvents);
                                    if (orderedOtaEvents.size() > 0) {
                                        JsonNode last = orderedOtaEvents.get(orderedOtaEvents.size() - 1);
                                        dlpService.sendToDlp(uuid, assembleToClientUpdateProgress(last));
                                    }
                                }
                                return ServerResponse.status(HttpStatus.NO_CONTENT).build();
                            }
                        }
                        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    })
                    .onErrorResume(t -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(t.getMessage())));
        }
        return ServerResponse.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    public Mono<ServerResponse> upgrade(String uuid, OtaMessage message) {
        if (message != null) {
            message.setUuid(uuid);
            log.debug("Received an ota message: {}", message);

            DeviceResource deviceResource = getDeviceInfoFromRedis(uuid);
            if (deviceResource != null && StringUtils.hasText(deviceResource.getCltId())) {
                JsonNode directive = assembleDlpOtaDirective(message, 1);
                DataPointMessage assembled = Adapter.directive2DataPoint(directive, DATA_POINT_DUER_DLP, null);
                assembled.setCltId(deviceResource.getCltId());
                assembled.setDeviceId(uuid);
                String sn = Optional.ofNullable(message.getMessageId()).orElse(UUID.randomUUID().toString());
                assembled.setSn(sn);
                long logId = System.currentTimeMillis();
                assembled.setLogId(Long.toString(logId));
                pushService.prepareAckPush(assembled);
                String key = assembled.getKey();

                log.info("Pushed ota update directive to device {}, key:{} logid:{}", message.getUuid(), key, logId);
                return pushService.push(assembled)
                        .flatMap(response -> {
                            if (response.getCode() == MESSAGE_SUCCESS_CODE) {
                                return pushService
                                        .check(assembled, key, Collections.singletonList(assembled.getId()))
                                        .timeout(Duration.ofSeconds(5), Mono.just(failedResponses.apply(key, String.format("Waiting ack timeout. key:%s", key))))
                                        .flatMap(baseResponse -> ServerResponse.ok().body(BodyInserters.fromObject(baseResponse)));
                            } else {
                                return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(response));
                            }
                        })
                        .doFinally(signalType -> pushService.unPool(key));
            }
            return deviceMayNotOnline.get().apply(uuid);
        }
        return ServerResponse.badRequest().build();
    }

    private Mono<ServerResponse> getOTAStatus(String uuid, String requestId) {
        return checkOtaResult(uuid)
                .flatMap(checkOtaResult -> {
                    JsonNode toClient = assembleDirective("to_client", "dlp.system_update", "Status", null, requestId, null, checkOtaResult);
                    dlpService.forceSendToDlp(uuid, toClient);
                    return ServerResponse.status(HttpStatus.NO_CONTENT).build();
                })
                .onErrorResume(t -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(t.getMessage())));
    }

    private Mono<CheckOtaResult> checkOtaResult(String uuid) {
        return Mono.fromFuture(
                paasApiClient.checkOtaStatus(uuid)
                .handleAsync(
                        (r, t) -> {
                            try(ResponseBody body = r.body()) {
                                if (r.isSuccessful() && body != null) {
                                    JsonNode result = JsonUtil.readTree(body.string());
                                    CheckOtaResult checkOtaResult = JsonUtil.deserialize(result.path("data").toString(), CheckOtaResult.class);
                                    if (checkOtaResult != null) {
                                        checkOtaResult.setState(
                                                checkOtaResult.getOtaTaskId() == 0 ? CheckOtaResult.State.UPDATABLE : CheckOtaResult.State.UPDATING);
                                        return checkOtaResult;
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Checking ota result failed", e);
                            }
                            return new CheckOtaResult();
                        }
                )
        );
    }

    private Mono<OtaTask> createTask(OtaTaskCreateRequest createRequest) {
        return Mono.fromFuture(
                paasApiClient.createTask(createRequest)
                .handleAsync(
                        (r, t) -> {
                            try(ResponseBody body = r.body()) {
                                if (r.isSuccessful() && body != null) {
                                    JsonNode result = JsonUtil.readTree(body.string());
                                    return JsonUtil.deserialize(result.path("data").toString(), OtaTask.class);
                                }
                            } catch (Exception e) {
                                log.error("Creating ota task failed", e);
                            }
                            return null;
                        }
                )
        )
                .switchIfEmpty(Mono.just(new OtaTask()));
    }

    private Mono<PackageInfo> getPackage(long pckId) {
        return Mono.fromFuture(
                paasApiClient.getPackage(pckId)
                        .handleAsync(
                                (r, t) -> {
                                    try(ResponseBody body = r.body()) {
                                        if (r.isSuccessful() && body != null) {
                                            JsonNode result = JsonUtil.readTree(body.string());
                                            return JsonUtil.deserialize(result.path("data").toString(), PackageInfo.class);
                                        }
                                    } catch (Exception e) {
                                        log.error("Checking ota result failed", e);
                                    }
                                    return null;
                                }
                        )
        )
                .switchIfEmpty(Mono.just(new PackageInfo()));
    }

    private Mono<JsonNode> getTask(String uuid) {
        return Mono.fromFuture(
                paasApiClient.getTask(uuid)
                        .handleAsync(
                                (r, t) -> {
                                    try(ResponseBody body = r.body()) {
                                        if (r.isSuccessful() && body != null) {
                                            return JsonUtil.readTree(body.string());
                                        }
                                    } catch (Exception e) {
                                        log.error("Checking ota result failed", e);
                                    }
                                    return null;
                                }
                        )
        );
    }

    private Mono<JsonNode> getTaskById(long id) {
        return Mono.fromFuture(
                paasApiClient.getTaskById(id)
                        .handleAsync(
                                (r, t) -> {
                                    try(ResponseBody body = r.body()) {
                                        if (r.isSuccessful() && body != null) {
                                            return JsonUtil.readTree(body.string());
                                        }
                                    } catch (Exception e) {
                                        log.error("Getting ota task by id({}) failed", id, e);
                                    }
                                    return null;
                                }
                        )
        );
    }

    private ArrayNode orderedEvents(String otaEvents) {
        String[] eventItems = StringUtils.delimitedListToStringArray(otaEvents, SPLITTER_LF);
        ArrayNode result = JsonUtil.createArrayNode();
        Stream.of(eventItems)
                .map(e -> {
                    String[] items = e.split(SPLITTER_SPACE, 3);
                    if (items.length > 2) {
                        return JsonUtil.readTree(items[2]);
                    }
                    return JsonUtil.createObjectNode();
                })
                .sorted((j1, j2) -> {
                    double diff = j1.path("event").asInt(0) - j2.path("event").asInt(0) + (j1.path("percent").asDouble(0) - j2.path("percent").asDouble(0));
                    if (diff < 0) {
                        return -1;
                    }
                    if (diff > 0) {
                        return 1;
                    }
                    return 0;
                })
                .forEach(result::add);
        return result;
    }
}

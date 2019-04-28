package com.baidu.iot.devicecloud.devicemanager.handler.http;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant;
import com.baidu.iot.devicecloud.devicemanager.service.PushService;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.multipart.Part;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.baidu.iot.devicecloud.devicemanager.adapter.Adapter.try2appendDialogueFinished;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_METHOD_PUT;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_NEED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_SECRET_KEY;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_AUDIO;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_METADATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.assembleFromHeader;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.isCoapRequest;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/29.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class PushHandler {
    private final PushService pushService;

    @Autowired
    public PushHandler(PushService pushService) {
        this.pushService = pushService;
    }

    @NonNull
    public Mono<ServerResponse> deal(ServerRequest request) {
        BaseMessage message = new BaseMessage();
        assembleFromHeader.accept(request, message);
        request.queryParam("clt_id").ifPresent(message::setCltId);
        request.queryParam("msgid").ifPresent(message::setLogId);
        int method = figureOutMethod(request);
        // pushing needs ack
        message.setNeedAck(true);
        List<Integer> idList = new ArrayList<>();
        return request.body(BodyExtractors.toMultipartData())
                .flatMap(parts -> {
                    // The order of directives would be sent to device
                    List<Part> metadata = parts.getOrDefault(PARAMETER_METADATA, new ArrayList<>());
                    if (metadata.size() < 1) {
                        return Mono.empty();
                    }
                    List<Part> audio = parts.getOrDefault(PARAMETER_AUDIO, new ArrayList<>());

                    String key = pushService.pool(message);
                    List<DataPointMessage> messages = assemble(method, metadata, audio, idList, key, message);
                    return pushService.push(messages)
                            .flatMap(baseResponse -> {
                                if (baseResponse.getCode() == 0) {
                                    // waiting the result for 5 seconds.
                                    Mono<BaseResponse> responseMono = pushService.check(message, key, idList)
                                            .timeout(Duration.ofSeconds(5), Mono.just(failedResponses.apply(message.getLogId(), "Waiting ack timeout")));

                                    return ServerResponse.ok().body(responseMono, BaseResponse.class);
                                } else {
                                    return ServerResponse.ok().body(
                                            BodyInserters.fromObject(failedResponses.apply(message.getLogId(), "Pushing message failed"))
                                    );
                                }
                            });
                })
                .onErrorResume(
                        e -> {
                            String em = e.getMessage();
                            log.error("Something wrong when pushing message to dh2: {}", em);
                            e.printStackTrace();
                            return ServerResponse.ok().body(
                                    BodyInserters.fromObject(failedResponses.apply(message.getLogId(), em)));
                        }
                );
    }

    private int figureOutMethod(ServerRequest request) {
        Optional<String> optMethod = request.queryParam("method");
        // default method
        int method = COAP_METHOD_PUT;
        if (optMethod.isPresent()) {
            String methodStr = optMethod.get();
            try {
                int opt = Integer.parseInt(methodStr);
                if (isCoapRequest.test(opt)) {
                    method = opt;
                }
            } catch (NumberFormatException ignore) {}
        }
        return method;
    }

    private List<DataPointMessage> assemble(int method,
                                            List<Part> metadata,
                                            List<Part> audios,
                                            List<Integer> idList,
                                            String key,
                                            BaseMessage origin) {
        List<JsonNode> metadataJson;
        if (audios != null && audios.size() > 0) {
            metadataJson = pushService.fixUrl(metadata, audios);
        } else {
            metadataJson = pushService.readJson(metadata);
        }
        return assembleDirective(method, metadataJson, idList, key, origin);
    }

    private List<DataPointMessage> assembleDirective(int method,
                                                     List<JsonNode> directives,
                                                     List<Integer> idList,
                                                     String key,
                                                     BaseMessage origin) {
        if (directives == null || directives.size() < 1 || origin == null) {
            return Collections.emptyList();
        }

        // try to append DialogueFinished
        try2appendDialogueFinished(directives);

        return directives.stream()
                .map(jsonNode -> {
                    int id = IdGenerator.nextId();
                    idList.add(id);
                    return assembleDirective0(method, jsonNode, id, key, origin);
                })
                .collect(Collectors.toList());
    }

    private DataPointMessage assembleDirective0(int method,
                                                JsonNode directiveJson,
                                                int id,
                                                String key,
                                                BaseMessage origin) {
        DataPointMessage assembled = new DataPointMessage();
        assembled.setId(id);
        assembled.setCode(method);
        assembled.setPath(PathUtil.lookAfterPrefix(DataPointConstant.DATA_POINT_DUER_DIRECTIVE));
        assembled.setVersion(DEFAULT_VERSION);
        assembled.setPayload(directiveJson.toString());
        assembled.setCltId(origin.getCltId());
        assembled.setLogId(origin.getLogId());
        assembled.setSn(origin.getSn());
        if (StringUtils.hasText(key)) {
            ObjectNode misc = JsonUtil.createObjectNode();
            misc.set(MESSAGE_ACK_NEED, BooleanNode.getTrue());
            misc.set(MESSAGE_ACK_SECRET_KEY, TextNode.valueOf(key));
            assembled.setMisc(misc.toString());
        }
        return assembled;
    }
}

package com.baidu.iot.devicecloud.devicemanager.handler.http;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.processor.DirectiveProcessor;
import com.baidu.iot.devicecloud.devicemanager.service.PushService;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_METHOD_PUT;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_NEED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_SECRET_KEY;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CLIENT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_MESSAGE_ID;
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
    private final TtsService ttsService;

    @Autowired
    public PushHandler(PushService pushService, TtsService ttsService) {
        this.pushService = pushService;
        this.ttsService = ttsService;
    }

    @NonNull
    public Mono<ServerResponse> deal(ServerRequest request) {
        BaseMessage message = new BaseMessage();
        assembleFromHeader.accept(request, message);
        request.queryParam(PARAMETER_CLIENT_ID).ifPresent(clt_id -> {
            message.setCltId(clt_id);
            String[] items = clt_id.split(Pattern.quote(CommonConstant.SPLITTER_DOLLAR));
            if (items.length > 1) {
                message.setProductId(items[0]);
                message.setDeviceId(items[1]);
            }
        });
        request.queryParam(PARAMETER_MESSAGE_ID).ifPresent(message::setLogId);
        if (StringUtils.isEmpty(message.getSn())) {
            message.setSn(Optional.ofNullable(message.getLogId()).orElse(UUID.randomUUID().toString()));
        }

        int method = figureOutMethod(request);
        // pushing needs ack
        message.setNeedAck(true);
        List<Integer> idList = new ArrayList<>();
        String cuid = message.getDeviceId();
        String sn = message.getSn();
        String key = pushService.pool(message);
        return new DirectiveProcessor(ttsService)
                .processMultiparts(cuid, sn, request.body(BodyExtractors.toParts()), MessageType.PUSH_MESSAGE, false)
                .flatMapSequential(directive -> {
                    DataPointMessage assembled = assembleDirective0(method, directive, IdGenerator.nextId(), key, message);
                    return pushService.push(assembled).then();
                })
                .then(
                        // waiting the result for 5 seconds at most.
                        pushService
                                .check(message, key, idList)
                                .timeout(Duration.ofSeconds(5), Mono.just(failedResponses.apply(message.getLogId(), "Waiting ack timeout")))
                                .flatMap(baseResponse -> {
                                    if (baseResponse.getCode() != 0) {
                                        log.error("Checking acknowledges failed");
                                    }
                                    return ServerResponse.ok().body(BodyInserters.fromObject(baseResponse));
                                })
                                .onErrorResume(
                                        e -> {
                                            log.error("Pushing message to dh2 failed", e);
                                            return ServerResponse.ok().body(
                                                    BodyInserters.fromObject(failedResponses.apply(message.getLogId(), e.getMessage())));
                                        }
                                )
                                .doFinally(signalType -> pushService.unPool(key))
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
        assembled.setSn(Optional.ofNullable(origin.getSn()).orElseGet(origin::getLogId));
        assembled.setDeviceId(origin.getDeviceId());
        if (StringUtils.hasText(key)) {
            ObjectNode misc = JsonUtil.createObjectNode();
            misc.set(MESSAGE_ACK_NEED, BooleanNode.getTrue());
            misc.set(MESSAGE_ACK_SECRET_KEY, TextNode.valueOf(key));
            assembled.setMisc(misc.toString());
        }
        return assembled;
    }
}

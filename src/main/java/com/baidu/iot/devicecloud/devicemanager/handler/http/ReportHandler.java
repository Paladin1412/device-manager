package com.baidu.iot.devicecloud.devicemanager.handler.http;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.service.ReportService;
import com.baidu.iot.devicecloud.devicemanager.service.extractor.ReportMessageExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_REQUEST;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_ALIVE_INTERVAL;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CLT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_LOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_MESSAGE_TIMESTAMP;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_MESSAGE_TYPE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_SN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_STANDBY_DEVICE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_STATUS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_STREAMING_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_UNEXPECTED_FAILURE_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.MessageType.BASE;
import static com.baidu.iot.devicecloud.devicemanager.constant.MessageType.DATA_POINT;
import static com.baidu.iot.devicecloud.devicemanager.constant.MessageType.PUSH_MESSAGE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedDataPointResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getFirst;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.isCoapOk;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/2/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class ReportHandler {
    private final ReportMessageExtractor extractor;
    private final ReportService reportService;

    @Value("${heartbeat.between.dm.dh: 60}")
    private int aliveInterval;

    @Autowired
    ReportHandler(ReportMessageExtractor extractor,
                  ReportService reportService) {
        this.extractor = extractor;
        this.reportService = reportService;
    }

    @NonNull
    public Mono<ServerResponse> deal(ServerRequest request) {
        String messageType = getFirst(request, HEADER_MESSAGE_TYPE);
        String logId = getFirst(request, HEADER_LOG_ID);

        ServerResponse.BodyBuilder builder =
                ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .header(HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()));

        Optional.ofNullable(messageType).ifPresent(
                t -> builder.header(HEADER_MESSAGE_TYPE, t)
        );

        Optional.ofNullable(getFirst(request, HEADER_CLT_ID)).ifPresent(
                cktId -> builder.header(HEADER_CLT_ID, cktId)
        );

        Optional.ofNullable(getFirst(request, HEADER_SN)).ifPresent(
                sn -> builder.header(HEADER_SN, sn)
        );

        Optional.ofNullable(logId).ifPresent(
                log -> builder.header(HEADER_LOG_ID, log)
        );

        Optional.ofNullable(getFirst(request, HEADER_STANDBY_DEVICE_ID)).ifPresent(
                standby -> builder.header(HEADER_STANDBY_DEVICE_ID, standby)
        );

        Optional.ofNullable(getFirst(request, HEADER_STREAMING_VERSION)).ifPresent(
                version -> builder.header(HEADER_STREAMING_VERSION, version)
        );
        return Mono.from(this.extractor.handle(request))
                .filter(o -> o instanceof BaseMessage)
                .map(BaseMessage.class::cast)
                .flatMap(this.reportService::handle)
                .flatMap(o -> {
                    // decorate the response according to the received object
                    if (o instanceof DataPointMessage) {
                        DataPointMessage message = (DataPointMessage) o;
                        if (isCoapOk.test(message)) {
                            builder
                                    .header(HEADER_ALIVE_INTERVAL, Integer.toString(aliveInterval));
                            succeeded.accept(builder);
                        } else {
                            failed.accept(builder);
                        }
                        return builder.syncBody(o);
                    } else if (o instanceof BaseResponse) {
                        BaseResponse response = (BaseResponse) o;
                        if (response.getCode() == MESSAGE_SUCCESS_CODE) {
                            succeeded.accept(builder);
                        } else {
                            failed.accept(builder);
                        }
                        return builder.build();
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    failed.accept(builder);
                    return builder.build();
                }))
                .onErrorResume(
                        e -> {
                            String em = e.getMessage();
                            log.error("Handling the reported message failed", e);
                            unexpectedFailed.accept(builder);
                            if (isDataPoint.test(messageType)) {
                                return builder.body(
                                        Mono.just(
                                                failedDataPointResponses.get()
                                                        .apply(COAP_RESPONSE_CODE_DUER_MSG_RSP_BAD_REQUEST, em)
                                        ),
                                        DataPointMessage.class
                                );
                            }
                            return builder.body(
                                    Mono.just(
                                            failedResponses.apply(logId, em)
                                    ),
                                    BaseResponse.class
                            );
                        }
                );
    }

    private int parseMessageType(String messageType) {
        int mt = BASE;
        if (StringUtils.isEmpty(messageType)) {
            return mt;
        }
        try {
            mt = Integer.valueOf(messageType);
        } catch (NumberFormatException ignore) {}
        return mt;
    }

    private BiConsumer<ServerResponse.BodyBuilder, String> state =
            (builder, code) -> builder.header(HEADER_STATUS_CODE, code);

    private Consumer<ServerResponse.BodyBuilder> succeeded =
            builder -> state.accept(builder, Integer.toString(MESSAGE_SUCCESS_CODE));

    private Consumer<ServerResponse.BodyBuilder> failed =
            builder -> state.accept(builder, Integer.toString(MESSAGE_FAILURE_CODE));

    private Consumer<ServerResponse.BodyBuilder> unexpectedFailed =
            builder -> state.accept(builder, Integer.toString(MESSAGE_UNEXPECTED_FAILURE_CODE));

    private Predicate<String> isDataPoint =
            type -> {
                int[] dataPointTypes = {DATA_POINT, PUSH_MESSAGE};
                return Arrays.stream(dataPointTypes).anyMatch(t -> t == parseMessageType(type));
    };
}

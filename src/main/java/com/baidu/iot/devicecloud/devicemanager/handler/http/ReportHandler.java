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
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_ALIVE_INTERVAL;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CLT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_LOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_MESSAGE_TIMESTAMP;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_MESSAGE_TYPE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_SN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_STANDBY_DEVICE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_STATUS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_STREAMING_VERSION;
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
        ServerResponse.BodyBuilder builder =
                ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .header(HEADER_MESSAGE_TYPE, getFirst(request, HEADER_MESSAGE_TYPE))
                        .header(HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                        .header(HEADER_CLT_ID, getFirst(request, HEADER_CLT_ID))
                        .header(HEADER_SN, getFirst(request, HEADER_SN));
        Optional.ofNullable(getFirst(request, HEADER_LOG_ID)).ifPresent(
                logId -> builder.header(HEADER_LOG_ID, logId)
        );

        Optional.ofNullable(getFirst(request, HEADER_STANDBY_DEVICE_ID)).ifPresent(
                standby -> builder.header(HEADER_STANDBY_DEVICE_ID, standby)
        );

        Optional.ofNullable(getFirst(request, HEADER_STREAMING_VERSION)).ifPresent(
                version -> builder.header(HEADER_STREAMING_VERSION, version)
        );
        return Mono.from(this.extractor.handle(request))
                .filter(o -> o instanceof BaseMessage)
                .flatMap(o -> Mono.just((BaseMessage) o))
                .flatMap(this.reportService::handle)
                .doOnNext(o -> {
                    if (o instanceof DataPointMessage) {

                        DataPointMessage message = (DataPointMessage) o;

                        if (isCoapOk.test(message)) {
                            builder
                                    .header(HEADER_ALIVE_INTERVAL, Integer.toString(aliveInterval))
                                    .header(HEADER_STATUS_CODE, "0");
                        } else {
                            builder
                                    .header(HEADER_STATUS_CODE, "-1");
                        }
                    } else if (o instanceof BaseResponse) {
                        BaseResponse response = (BaseResponse) o;
                        if (response.getCode() == 0) {
                            builder
                                    .header(HEADER_STATUS_CODE, "0");
                        } else {
                            builder
                                    .header(HEADER_STATUS_CODE, "-1");
                        }
                    }
                })
                .flatMap(builder::syncBody);
    }
}

package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.Date;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE_DH2;
import static com.baidu.iot.devicecloud.devicemanager.service.DeviceSessionService.KEY_LAST_HEARTBEAT;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponsesWithMessage;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class HearBeatService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private final DeviceSessionService sessionService;

    @Autowired
    public HearBeatService(DeviceSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    boolean canHandle(BaseMessage message) {
        return message.getMessageType() == MessageType.HEARTBEAT;
    }

    @Override
    Mono<Object> work(BaseMessage message) {
        return Mono.from(
                Mono.justOrEmpty(message)
                        .doOnNext(next -> {
                            String cuid = message.getDeviceId();
                            log.info("[Heartbeat] cuid:{} ip:{} port:{}",
                                    message.getDeviceId(), message.getDeviceIp(), message.getDevicePort());
                            DproxyClientProvider.getInstance().setexAsync(KEY_LAST_HEARTBEAT + cuid, -1, buildCacheData(message));
                            log.debug("Refreshing session. cuid:{}", cuid);
                            sessionService.freshSession(cuid);
                        })
                        .flatMap(msg -> {
                            BaseResponse response = successResponsesWithMessage.apply(msg);
                            response.setStatus(MESSAGE_SUCCESS_CODE_DH2);
                            return Mono.just(response);
                        })
                        .switchIfEmpty(Mono.defer(() ->Mono.error(new ServerWebInputException("No heartbeat"))))
        );
    }

    private DeviceSessionService.CacheData buildCacheData(BaseMessage message) {
        DeviceSessionService.CacheData data = new DeviceSessionService.CacheData();
        data.setTime(new Date());
        if (message != null) {
            data.setDevicehub(String.format("%s:%s", message.getDeviceIp(), message.getDevicePort()));
        }
        return data;
    }
}

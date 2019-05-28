package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponsesWithMessage;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class HearBeatService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private final AccessTokenService accessTokenService;

    @Autowired
    public HearBeatService(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
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
                            log.info("[Heartbeat] cuid:{} ip:{} port:{}",
                                    message.getDeviceId(), message.getDeviceIp(), message.getDevicePort());
                            this.accessTokenService.getAccessToken(message.getDeviceId(), message.getLogId());
                        })
                        .flatMap(msg -> Mono.just(successResponsesWithMessage.apply(msg)))
                        .switchIfEmpty(Mono.defer(() ->Mono.error(new ServerWebInputException("No heartbeat"))))
        );
    }
}

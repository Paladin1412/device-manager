package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
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
public class DefaultService extends AbstractLinkableHandlerAdapter<BaseMessage> {

    @Override
    boolean canHandle(BaseMessage message) {
        return message.getMessageType() == MessageType.BASE;
    }

    @Override
    Mono<Object> work(BaseMessage message) {
        return Mono.justOrEmpty(message)
                .doOnNext(msg -> log.info(msg.toString()))
                .switchIfEmpty(Mono.error(new ServerWebInputException("No message")))
                .flatMap(msg -> Mono.just(successResponsesWithMessage.apply(msg)));
    }
}

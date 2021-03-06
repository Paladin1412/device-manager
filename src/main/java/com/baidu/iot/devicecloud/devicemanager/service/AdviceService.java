package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AdviceService extends AbstractLinkableHandlerAdapter<BaseMessage> {

    @Override
    boolean canHandle(BaseMessage message) {
        return message.getMessageType() == MessageType.ADVICE;
    }

    @Override
    Mono<Object> work(BaseMessage message) {
        return null;
    }
}

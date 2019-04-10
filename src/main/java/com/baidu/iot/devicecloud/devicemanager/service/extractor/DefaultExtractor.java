package com.baidu.iot.devicecloud.devicemanager.service.extractor;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/22.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DefaultExtractor extends AbstractLinkableExtractor {
    @Override
    boolean canHandle(int type) {
        return type == MessageType.BASE
                || type == MessageType.SYS_DISCONNECTED
                || type == MessageType.HEARTBEAT
                || type == MessageType.SYS_EXCEPTION
                || type == MessageType.PUSH_MESSAGE;
    }

    @Override
    Mono<Object> work(ServerRequest t) {
        BaseMessage message = new BaseMessage();
        assembleFromHeader(t, message);
        return Mono.justOrEmpty(message);
    }
}

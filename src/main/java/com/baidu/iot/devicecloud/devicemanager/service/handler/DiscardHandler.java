package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponses;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/4/24.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DiscardHandler extends AbstractLinkableDataPointHandler {

    @Override
    boolean canHandle(String type) {
        return true;
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        log.debug("This message will be just discarded.");
        return Mono.just(successResponses.apply(message.getLogId()));
    }
}

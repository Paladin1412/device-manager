package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.processor.EventProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_EVENT;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DuerEventHandler extends AbstractLinkableDataPointHandler {
    private final EventProcessor eventProcessor;

    @Autowired
    public DuerEventHandler(EventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @Override
    boolean canHandle(String type) {
        return DATA_POINT_DUER_EVENT.equalsIgnoreCase(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        return eventProcessor.process(message);
    }
}

package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.service.LinkableHandler;
import com.baidu.iot.devicecloud.devicemanager.service.ReactorDispatcherHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Component
public class DataPointChainedHandler implements ReactorDispatcherHandler<DataPointMessage> {
    private LinkableHandler<DataPointMessage> handler;

    @Autowired
    DataPointChainedHandler(DuerEventHandler duerEventHandler) {
        // The first link in chain is supposed to handle the most requests
        this.handler = duerEventHandler;
        // Link the chain up

    }

    @Override
    public Mono<Object> handle(DataPointMessage message) {
        return handler.handle(message);
    }
}

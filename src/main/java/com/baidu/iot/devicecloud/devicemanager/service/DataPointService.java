package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.service.handler.DataPointChainedHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DataPointService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private final DataPointChainedHandler dataPointChainedHandler;

    @Autowired
    public DataPointService(DataPointChainedHandler dataPointChainedHandler) {
        this.dataPointChainedHandler = dataPointChainedHandler;
    }

    @Override
    boolean canHandle(BaseMessage message) {
        return message.getMessageType() == MessageType.DATA_POINT;
    }

    @Override
    Mono<Object> work(BaseMessage message) {
        DataPointMessage msg = (DataPointMessage) message;
        return dataPointChainedHandler.handle(msg);
    }
}

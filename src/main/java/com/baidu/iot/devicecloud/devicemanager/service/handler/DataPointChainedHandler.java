package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.service.LinkableHandler;
import com.baidu.iot.devicecloud.devicemanager.service.ReactorDispatcherHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.isCoapRequest;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Component
public class DataPointChainedHandler implements ReactorDispatcherHandler<DataPointMessage> {
    private LinkableHandler<DataPointMessage> requestHandler;
    private LinkableHandler<DataPointMessage> responseHandler;

    @Autowired
    DataPointChainedHandler(DuerEventHandler duerEventHandler,
                            DuerBindUtokenHandler duerBindUtokenHandler,
                            Sending2BigpipeHandler sending2BigpipeHandler,
                            DeviceStatusHandler deviceStatusHandler,
                            DuerLogHandler duerLogHandler,
                            DataPointAdviceHandler adviceHandler,
                            DiscardHandler discardHandler) {
        // The first link in chain is supposed to handle the most requests
        this.requestHandler = duerEventHandler;
        // Link the chain up
        this.requestHandler
                .linkWith(duerBindUtokenHandler)
                .linkWith(sending2BigpipeHandler)
                .linkWith(deviceStatusHandler)
                .linkWith(duerLogHandler)
                .linkWith(adviceHandler)
                .linkWith(discardHandler);

        this.responseHandler = discardHandler;
    }

    @Override
    public Mono<Object> handle(DataPointMessage message) {
        if (isCoapRequest.test(message.getCode())) {
            return requestHandler.handle(message);
        }
        return responseHandler.handle(message);
    }
}

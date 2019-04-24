package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Component
public class ReportService implements ReactorDispatcherHandler<BaseMessage> {
    private LinkableHandler<BaseMessage> handler;

    @Autowired
    ReportService(DataPointService dataPointService,
                  AuthenticationService authenticationService,
                  DisconnectedService disconnectedService,
                  HearBeatService hearBeatService,
                  AdviceService adviceService,
                  DefaultService defaultService) {
        // The first link in chain is supposed to handle the most requests
        this.handler = dataPointService;
        // Link the chain up
        this.handler
                .linkWith(authenticationService)
                .linkWith(disconnectedService)
                .linkWith(hearBeatService)
                .linkWith(adviceService)
                .linkWith(defaultService);
    }

    @Override
    public Mono<Object> handle(BaseMessage message) {
        return handler.handle(message);
    }
}

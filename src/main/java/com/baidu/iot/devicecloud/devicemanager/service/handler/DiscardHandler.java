package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_ACCEPTABLE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.transformedDataPointResponses;

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
        log.debug("This message will be just discarded. logid:{}", message.getLogId());
        return Mono.just(transformedDataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_ACCEPTABLE));
    }
}

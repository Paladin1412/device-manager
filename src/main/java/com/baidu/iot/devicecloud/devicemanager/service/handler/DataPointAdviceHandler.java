package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.service.PushService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_IMPLEMENTED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_NEED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_SECRET_KEY;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_ACK;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/4/24.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DataPointAdviceHandler extends AbstractLinkableDataPointHandler {
    private final PushService pushService;

    @Autowired
    public DataPointAdviceHandler(PushService pushService) {
        this.pushService = pushService;
    }

    @Override
    boolean canHandle(String type) {
        return DATA_POINT_DUER_ACK.equalsIgnoreCase(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        boolean flag = try2Advice(message);
        message.setMisc(null);
        message.setPath(null);
        message.setCode(flag ? COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID : COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_IMPLEMENTED);
        return Mono.just(message);
    }

    private boolean try2Advice(DataPointMessage message) {
        try {
            if (message != null && StringUtils.hasText(message.getMisc())) {
                String misc = message.getMisc();
                JsonNode miscNode = JsonUtil.readTree(misc);
                JsonNode needAckNode = miscNode.path(MESSAGE_ACK_NEED);
                if (!needAckNode.isNull()) {
                    boolean needAck = false;
                    if (needAckNode.isTextual()) {
                        needAck = Boolean.valueOf(needAckNode.asText());
                    } else if (needAckNode.isBoolean()) {
                        needAck = needAckNode.asBoolean();
                    }
                    String secretKey = miscNode.path(MESSAGE_ACK_SECRET_KEY).asText();
                    if (needAck && StringUtils.hasText(secretKey)) {
                        pushService.advice(secretKey, message);
                        log.debug("The advice has been submitted successfully. secretKey:{}", secretKey);
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
        }
        log.debug("Submitting this ack has failed.");
        return false;
    }
}

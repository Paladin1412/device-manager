package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceStatus;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_FOUND;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DEVICE_STATUS;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.dataPointResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.writeDeviceResourceToRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/9/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DeviceStatusHandler extends AbstractLinkableDataPointHandler {
    @Value("${expire.resource.session: 600}")
    private long sessionExpire;

    @Override
    boolean canHandle(String type) {
        return DATA_POINT_DEVICE_STATUS.equalsIgnoreCase(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        if (message == null) {
            return Mono.just(dataPointResponses(null, COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT, null));
        }
        if (StringUtils.isEmpty(message.getPayload())) {
            return Mono.just(dataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT,
                    String.format("No payload found. id:%d cuid:%s", message.getId(), message.getDeviceId())));
        }

        JsonNode tree = JsonUtil.readTree(message.getPayload());
        if (tree.isNull()) {
            return Mono.just(dataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT,
                    String.format("Payload is not json. id:%d cuid:%s", message.getId(), message.getDeviceId())));
        }

        String uuid = message.getDeviceId();

        DeviceResource deviceResource = getDeviceInfoFromRedis(uuid);
        if (deviceResource == null) {
            return Mono.just(dataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_FOUND,
                    String.format("Device may not online. id:%d cuid:%s", message.getId(), message.getDeviceId())));
        }

        DeviceStatus deviceStatus = JsonUtil.deserialize(message.getPayload(), DeviceStatus.class);
        if (deviceStatus != null) {
            deviceResource.setDeviceStatus(deviceStatus);
            writeDeviceResourceToRedis(deviceResource, sessionExpire);
        }
        return Mono.just(dataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID, null));
    }
}

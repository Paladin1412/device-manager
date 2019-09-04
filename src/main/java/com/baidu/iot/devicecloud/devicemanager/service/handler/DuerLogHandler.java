package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceStatus;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceSystemInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.cesclient.CesClient;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_NOT_FOUND;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_LOG;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.dataPointResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/9/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DuerLogHandler extends AbstractLinkableDataPointHandler {
    private final CesClient cesClient;

    @Autowired
    public DuerLogHandler(CesClient cesClient) {
        this.cesClient = cesClient;
    }

    @Override
    boolean canHandle(String type) {
        return DATA_POINT_DUER_LOG.equalsIgnoreCase(type);
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
        assemble(tree, deviceResource);
        cesClient.sendCesLogAsync(tree);
        return Mono.just(dataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID, null));
    }

    private void assemble(JsonNode tree, DeviceResource deviceResource) {
        ObjectNode logObject = (ObjectNode) tree;
        JsonNode dataNode = logObject.path("data");
        if (dataNode.isArray()) {
            ArrayNode dataArray = (ArrayNode) dataNode;
            double la = deviceResource.getLatitude();
            double lo = deviceResource.getLongitude();
            for (JsonNode node : dataArray) {
                ObjectNode on = (ObjectNode) node;
                on.set("la", DoubleNode.valueOf(la));
                on.set("lo", DoubleNode.valueOf(lo));
                on.set("city", TextNode.valueOf(""));
                on.set("net_type", TextNode.valueOf("1_0"));
            }
        }
        logObject.set("device_id", TextNode.valueOf(deviceResource.getCuid()));
        logObject.set("location_system", TextNode.valueOf(deviceResource.getGeoCoordinateSystem()));
        DeviceStatus deviceStatus = deviceResource.getDeviceStatus();
        if (deviceStatus != null) {
            DeviceSystemInfo deviceSystemInfo = deviceStatus.getSystemInfo();
            if (deviceSystemInfo != null) {
                logObject.set("from", TextNode.valueOf(deviceSystemInfo.getFrom()));
                logObject.set("client_id", TextNode.valueOf(deviceSystemInfo.getClientId()));
                logObject.set("sdk_version", TextNode.valueOf(deviceSystemInfo.getSdkVersion()));
                logObject.set("operation_system", TextNode.valueOf(deviceSystemInfo.getOperationSystem()));
                logObject.set("operation_system_version", TextNode.valueOf(deviceSystemInfo.getOperationSystemVersion()));
                logObject.set("device_brand", TextNode.valueOf(deviceSystemInfo.getDeviceBrand()));
                logObject.set("device_model", TextNode.valueOf(deviceSystemInfo.getDeviceModel()));
                logObject.set("ces_version", TextNode.valueOf(deviceSystemInfo.getCesVersion()));
                logObject.set("abtest", IntNode.valueOf(deviceSystemInfo.getAbTest()));
                logObject.set("real", IntNode.valueOf(deviceSystemInfo.getReal()));
            }
        }
    }
}

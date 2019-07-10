package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceBaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceMessageType;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.client.bigpipe.Sender;
import com.baidu.iot.devicecloud.devicemanager.client.bigpipe.SenderChannelType;
import com.baidu.iot.devicecloud.devicemanager.service.DlpService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_TRACE_INFO;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_OTA_EVENT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_PACKAGE_INFO;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.transformedDataPointResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.JsonUtil.assembleToClientUpdateProgress;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/7.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class Sending2BigpipeHandler extends AbstractLinkableDataPointHandler {
    private final List<String> canBeHandledDataPoints = Arrays.asList(
            DATA_POINT_PACKAGE_INFO,
            DATA_POINT_OTA_EVENT,
            DATA_POINT_DUER_TRACE_INFO
    );

    private final List<String> userDataPoints = Arrays.asList(
            DATA_POINT_PACKAGE_INFO,
            DATA_POINT_OTA_EVENT,
            DATA_POINT_DUER_TRACE_INFO
    );

    private final DlpService dlpService;
    private final Sender sender;

    @Autowired
    public Sending2BigpipeHandler(DlpService dlpService, Sender sender) {
        this.dlpService = dlpService;
        this.sender = sender;
    }

    @Override
    boolean canHandle(String type) {
        return canBeHandledDataPoints.contains(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        String path = PathUtil.dropOffPrefix(message.getPath(), SPLITTER_URL);
        // send ota_event to app
        if (DATA_POINT_OTA_EVENT.equalsIgnoreCase(path)) {
            dlpService.sendToDlp(message.getDeviceId(), assembleToClientUpdateProgress(JsonUtil.readTree(message.getPayload())));
        }
        DeviceBaseMessage baseMessage = assembleMessage(message);
        if (baseMessage != null) {
            sender.send(
                    userDataPoints.contains(path) ? SenderChannelType.USER : SenderChannelType.SYSTEM,
                    baseMessage,
                    message.getLogId()
            );
        }
        return Mono.just(transformedDataPointResponses(message, COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID));
    }

    @Nullable
    private DeviceBaseMessage assembleMessage(DataPointMessage message) {
        DeviceResource deviceResource = getDeviceInfoFromRedis(message.getDeviceId());
        if (deviceResource != null) {
            DeviceBaseMessage baseMessage = new DeviceBaseMessage();
            baseMessage.setAccountUuid(deviceResource.getAccountUuid());
            baseMessage.setBduss(deviceResource.getBduss());
            baseMessage.setCuid(deviceResource.getCuid());
            baseMessage.setGeoCoordinateSystem(deviceResource.getGeoCoordinateSystem());
            baseMessage.setLatitude(deviceResource.getLatitude());
            baseMessage.setLongitude(deviceResource.getLongitude());
            baseMessage.setToken(deviceResource.getToken());
            baseMessage.setUserId(deviceResource.getUserId());
            baseMessage.setUserIds(deviceResource.getUserIds());

            baseMessage.setMessageType(DeviceMessageType.DATA);
            baseMessage.setTime(new Date());
            baseMessage.setTimestamp(System.currentTimeMillis());

            baseMessage.setDeviceUuid(message.getDeviceId());
            baseMessage.setLogId(message.getLogId());

            String payload = message.getPayload();
            if (StringUtils.hasText(payload)) {
                baseMessage.setRawPayload(message.getPayload());
                Map<String, Object> map = JsonUtil.convert2Map(JsonUtil.readTree(payload));
                if (map != null) {
                    baseMessage.setData(new HashMap<String, Object>(){
                        {
                            put(PathUtil.dropOffPrefix(message.getPath(), SPLITTER_URL), map);
                        }
                    });
                }
            }

            if (StringUtils.hasText(message.getDeviceIp())) {
                baseMessage.setSourceHostAddress(message.getDeviceIp());
                try {
                    baseMessage.setSourcePort(Integer.parseInt(message.getDevicePort()));
                } catch (Exception ignore) {}
            } else {
                baseMessage.setSourceHostAddress(deviceResource.getIp());
                try {
                    baseMessage.setSourcePort(Integer.parseInt(deviceResource.getPort()));
                } catch (Exception ignore) {}
            }
            return baseMessage;
        }
        return null;
    }
}

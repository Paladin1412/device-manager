package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceBaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceMessageType;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.client.bigpipe.Sender;
import com.baidu.iot.devicecloud.devicemanager.client.bigpipe.SenderChannelType;
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

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_TRACE_INFO;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_OTA_EVENT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_PACKAGE_INFO;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;

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

    private final Sender sender;

    @Autowired
    public Sending2BigpipeHandler(Sender sender) {
        this.sender = sender;
    }

    @Override
    boolean canHandle(String type) {
        return canBeHandledDataPoints.contains(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        String path = PathUtil.dropOffPrefix(message.getPath(), SPLITTER_URL);
        DeviceBaseMessage baseMessage = assembleMessage(message);
        if (baseMessage != null) {
            sender.send(
                    userDataPoints.contains(path) ? SenderChannelType.USER : SenderChannelType.SYSTEM, baseMessage,
                    message.getLogId()
            );
        }
        return Mono.empty();
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
            baseMessage.setRawPayload(message.getPayload());
            baseMessage.setLogId(message.getLogId());
            baseMessage.setData(new HashMap<String, Object>(){
                {
                    put(PathUtil.dropOffPrefix(message.getPath(), SPLITTER_URL), message.getPayload());
                }
            });

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

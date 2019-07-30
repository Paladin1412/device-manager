package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.Location;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_LOCATION_SET_LOCATION;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.GET_STATUS;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deviceMayNotOnline;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.writeDeviceResourceToRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.JsonUtil.assembleDirective;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/7/24.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class LocationService {
    private final DlpService dlpService;

    @Value("${expire.resource.session: 600}")
    private long sessionExpire;

    @Autowired
    public LocationService(DlpService dlpService) {
        this.dlpService = dlpService;
    }

    public Mono<ServerResponse> deal(String uuid, String name, JsonNode toServer) {
        String requestId = toServer.path(DIRECTIVE_KEY_HEADER_DIALOG_ID).asText();
        DeviceResource deviceResource = getDeviceInfoFromRedis(uuid);
        if (deviceResource == null) {
            return deviceMayNotOnline.get().apply(uuid);
        }
        if (GET_STATUS.equalsIgnoreCase(name)) {
            Location location = new Location(deviceResource.getLongitude(), deviceResource.getLatitude(), deviceResource.getGeoCoordinateSystem());
            JsonNode toClient = assembleDirective("to_client", "dlp.location", "Status", null, requestId, null, location);
            dlpService.forceSendToDlp(uuid, toClient);
            return ServerResponse.status(HttpStatus.NO_CONTENT).build();
        }

        if (DLP_LOCATION_SET_LOCATION.equalsIgnoreCase(name)) {
            JsonNode payload = toServer.path("payload");
            Location location = JsonUtil.deserialize(payload.toString(), Location.class);
            if (location != null) {
                deviceResource.setLongitude(location.getLongitude());
                deviceResource.setLatitude(location.getLatitude());
                deviceResource.setGeoCoordinateSystem(location.getGeoCoordinateSystem());
                writeDeviceResourceToRedis(deviceResource, sessionExpire);
            }
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject("Setting location failed"));
        }
        return ServerResponse.status(HttpStatus.NOT_ACCEPTABLE).build();
    }
}

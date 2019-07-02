package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder.PrivateDlpBuilder;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.util.HttpUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DEVICE_OFFLINE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DEVICE_ONLINE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deleteDeviceResourceFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deleteSessionFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deviceOnlineStatus;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.writeDeviceResourceToRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/27.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Service
public class DeviceSessionService implements InitializingBean {
    private static final String KEY_LAST_CONNECTED = "last_connected:";
    static final String KEY_LAST_HEARTBEAT = "last_heartbeat:";
    private static final String KEY_LAST_DISCONNECTED = "last_disconnected:";

    private final AccessTokenService accessTokenService;
    private final DlpService dlpService;

    private ExecutorService commonSideExecutor;

    @Value("${expire.resource.session: 600}")
    private long sessionExpire;

    public DeviceSessionService(AccessTokenService accessTokenService, DlpService dlpService) {
        this.accessTokenService = accessTokenService;
        this.dlpService = dlpService;
    }

    @Override
    public void afterPropertiesSet() {
        commonSideExecutor = new ThreadPoolExecutor(0, 50,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    void setSession(DeviceResource deviceResource, String logId) {
        if (deviceResource != null) {
            commonSideExecutor.submit(() -> {
                DproxyClientProvider dproxyClient = DproxyClientProvider.getInstance();
                String cuid = Optional.ofNullable(deviceResource.getCuid()).orElse(deviceResource.getDeviceUuid());
                log.debug("Setting device session. cuid:{} logid:{}", cuid, logId);
                dproxyClient.setexAsync(KEY_LAST_CONNECTED + cuid, -1, buildCacheData(deviceResource));
                dproxyClient.setexAsync(KEY_LAST_HEARTBEAT + cuid, -1, buildCacheData(deviceResource));
                deviceOnlineStatus(cuid, true);
                accessTokenService.cacheAccessToken(
                        cuid,
                        deviceResource.getAccessToken(),
                        sessionExpire
                );

                writeDeviceResourceToRedis(deviceResource, sessionExpire);
                dlpService.forceSendToDlp(cuid, new PrivateDlpBuilder(DLP_DEVICE_ONLINE).getData());
            });
        }
    }

    void freshSession(String cuid) {
        HttpUtil.freshSession(cuid, sessionExpire);
    }

    void clearSession(String cuid, String cltId, String logId) {
        commonSideExecutor.submit(() -> {
            log.debug("Clearing device session. cuid:{} logid:{}", cuid, logId);
            deviceOnlineStatus(cuid, false);
            accessTokenService.releaseAccessToken(cuid, logId);

            DeviceResource deviceResource = getDeviceInfoFromRedis(cuid);
            // TODO: need to use atom operation
            /*if (StringUtils.hasText(cltId) && deviceResource != null && cltId.equalsIgnoreCase(deviceResource.getCltId())) {
                deleteSessionFromRedis(cuid);
            }*/

            DproxyClientProvider.getInstance().setex(KEY_LAST_DISCONNECTED + cuid, -1, buildCacheData(deviceResource));
            dlpService.forceSendToDlp(cuid, new PrivateDlpBuilder(DLP_DEVICE_OFFLINE).getData());

            // TODO: compatible code, need to delete at next version
            deleteDeviceResourceFromRedis(cuid);
            }
        );
    }

    private CacheData buildCacheData(DeviceResource deviceResource) {
        CacheData data = new CacheData();
        data.setTime(new Date());
        if (deviceResource != null) {
            data.setDevicehub(String.format("%s:%s", deviceResource.getIp(), deviceResource.getPort()));
        }
        return data;
    }

    @Data
    @EqualsAndHashCode
    @ToString
    static class CacheData {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private Date time;
        private String devicehub;
    }
}

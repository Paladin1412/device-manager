package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder.PrivateDlpBuilder;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DEVICE_OFFLINE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_DEVICE_ONLINE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deleteDeviceResourceFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deleteSessionFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deviceExist;
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
    private static final String KEY_LAST_HEARTBEAT = "last_heartbeat:";
    private static final String KEY_LAST_DISCONNECTED = "last_disconnected:";

    private final AccessTokenService accessTokenService;
    private final DlpService dlpService;

    private ExecutorService commonSideExecutor;

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
                        String cuid = Optional.ofNullable(deviceResource.getCuid()).orElse(deviceResource.getDeviceUuid());
                        log.debug("Setting device session. cuid:{} logid:{}", cuid, logId);
                        setAsync(KEY_LAST_CONNECTED + cuid, deviceResource);
                        setAsync(KEY_LAST_HEARTBEAT + cuid, deviceResource);
                        deviceOnlineStatus(cuid, true);
                        accessTokenService.cacheAccessToken(
                                cuid,
                                deviceResource.getAccessToken()
                        );
                        if (!deviceExist(cuid)) {
                            ProjectInfo projectInfo = deviceResource.getProjectInfo();
                            if (projectInfo != null
                                    && StringUtils.hasText(projectInfo.getVoiceId())
                                    && StringUtils.hasText(projectInfo.getVoiceKey())) {
                                writeDeviceResourceToRedis(deviceResource);
                            }
                        }
                        dlpService.forceSendToDlp(cuid, new PrivateDlpBuilder(DLP_DEVICE_ONLINE).getData());
                    }
            );
        }
    }

    void clearSession(String cuid, String logId) {
        commonSideExecutor.submit(() -> {
                log.debug("Clearing device session. cuid:{} logid:{}", cuid, logId);
                deviceOnlineStatus(cuid, false);
                accessTokenService.releaseAccessToken(cuid, logId);

                // TODO: compatible code, need to delete at next version
                deleteDeviceResourceFromRedis(cuid);

                deleteSessionFromRedis(cuid);
                setAsync(KEY_LAST_DISCONNECTED + cuid, getDeviceInfoFromRedis(cuid));
                dlpService.forceSendToDlp(cuid, new PrivateDlpBuilder(DLP_DEVICE_OFFLINE).getData());
            }
        );
    }

    private void setAsync(String key, DeviceResource deviceResource) {
        CompletableFuture<Response> future =
                DproxyClientProvider.getInstance().setexAsync(key, -1, buildCacheData(deviceResource));
        if (future != null) {
            future.handleAsync(
                    (r, t) -> {
                        close(r);
                        return null;
                    }
            );
        }
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
    private static class CacheData {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private Date time;
        private String devicehub;
    }
}

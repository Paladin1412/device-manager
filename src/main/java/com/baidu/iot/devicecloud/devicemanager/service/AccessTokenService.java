package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.AuthorizationMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.DeviceIamClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.util.HttpUtil;
import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deleteTokenFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getDeviceInfoFromRedis;

/**
 * <p>Common access token service.</p>
 * <br>
 * Obtains an access token in steps of:<br>
 *     <pre>
 *         1. If found in local cache
 *         2. Else if found in redis
 *         3. Else read the project info from redis(which written by {@link AuthenticationService#auth(AuthorizationMessage)}),
 *         get the id and key from project info, then query the remote IAM server
 *     </pre>
 *
 * Cache an access token in steps of:<br>
 *     <pre>
 *         1. Write to redis with a <code>1-day</code> time-to-live
 *         2. Write to local cache
 *     </pre>
 *
 * @apiNote Local cache has a <code>5-minute</code> time-to-live for every entry,
 * but last access will refresh it to another <code>5-minute</code><br><br>
 *
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@SuppressWarnings("JavadocReference")
@Slf4j
@Component
public class AccessTokenService {
    private final DeviceIamClient deviceIamClient;
    private Cache<String, String> atCache;

    // 24 * 60 * 60 = 86400
    @Value("${expire.token.access: 86400}")
    private long accessTokenExpire;

    @Autowired
    public AccessTokenService(DeviceIamClient deviceIamClient) {
        this.deviceIamClient = deviceIamClient;

        atCache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .removalListener(LogUtils.REMOVAL_LOGGER.apply(log))
                .build();
    }

    /**
     * Cache an access token
     * @apiNote Only after a device has been authorized legally
     * @param cuid the unique device id
     * @param accessToken being obtained after authorized legally
     */
    void cacheAccessToken(String cuid, String accessToken) {
        if (StringUtils.hasText(cuid) && StringUtils.hasText(accessToken)) {
            CompletableFuture<Response> future = writeTokenToRedis(cuid, accessToken);
            if (future != null) {
                future.handleAsync(
                        (r, t) -> {
                            if (r.isSuccessful()) {
                                atCache.put(cuid, accessToken);
                            }
                            close(r);
                            return null;
                        }
                );
            }
        }
    }

    /**
     * Obtains an access token for a specified device
     * @param cuid the unique device id
     * @param logId log id
     * @return the access token string, could be <code>null</code>
     */
    @Nullable
    public String getAccessToken(String cuid, String logId) {
        try {
            return atCache.get(cuid, () -> try2ObtainAccessToken(cuid, logId));
        } catch (Exception e) {
            log.error("Obtaining access token failed", e);
            return null;
        }
    }

    void releaseAccessToken(BaseMessage message) {
        String cuid = message.getDeviceId();
        String logId = message.getLogId();
        log.debug("Invalidating access token. cuid:{} logId:{}", cuid, logId);
        if (StringUtils.hasText(cuid)) {
            releaseAccessToken(cuid, logId);
        }
    }

    /**
     * Release an access token
     * @param cuid the unique device id
     * @param logId log id
     */
    private void releaseAccessToken(String cuid, String logId) {
        log.debug("Invalidating access token. cuid:{} logId:{}", cuid, logId);
        atCache.invalidate(cuid);
        deleteTokenFromRedis(cuid);
    }

    private String try2ObtainAccessToken(String cuid, String logId) {
        // try to get access token from redis
        String accessToken = HttpUtil.getTokenFromRedis(cuid);
        if (StringUtils.isEmpty(accessToken)) {
            // try to get project info from redis, which should'v been written into at authorization time.
            DeviceResource deviceResource = getDeviceInfoFromRedis(cuid);
            if (deviceResource != null) {
                ProjectInfo projectInfo = deviceResource.getProjectInfo();
                if (projectInfo != null
                        && StringUtils.hasText(projectInfo.getVoiceId())
                        && StringUtils.hasText(projectInfo.getVoiceKey())) {
                    String vId = projectInfo.getVoiceId();
                    String vKey = projectInfo.getVoiceKey();
                    // try to get access token by using project info
                    String at = deviceIamClient.getAccessToken(cuid, vId, vKey, logId);
                    if (StringUtils.hasText(at)) {
                        CompletableFuture<Response> future = writeTokenToRedis(cuid, at);
                        if (future != null) {
                            future.handleAsync(
                                    (r, t) -> {
                                        close(r);
                                        return null;
                                    }
                            );
                        }
                    }
                    return at;
                }
            }
        }

        return accessToken;
    }

    private CompletableFuture<Response> writeTokenToRedis(final String cuid, final String accessToken) {
        return DproxyClientProvider
                .getInstance()
                .setexAsync(CommonConstant.SESSION_KEY_PREFIX + cuid, -1, accessToken);
    }
}

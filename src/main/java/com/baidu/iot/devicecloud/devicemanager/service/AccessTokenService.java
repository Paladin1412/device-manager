package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.DeviceIamClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenResponse;
import com.baidu.iot.devicecloud.devicemanager.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Predicate;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getProjectInfoFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.refreshAccessTokenRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AccessTokenService {
    private final DeviceIamClient deviceIamClient;

    // 14 * 24 * 60 * 60 = 1209600
    @Value("${expire.token.access: 300}")
    private long accessTokenExpire;

    @Autowired
    public AccessTokenService(DeviceIamClient deviceIamClient) {
        this.deviceIamClient = deviceIamClient;
    }

    public AccessTokenResponse try2ObtainAccessToken(BaseMessage message) {
        return try2ObtainAccessToken(message.getDeviceId(), message.getLogId());
    }

    public AccessTokenResponse try2ObtainAccessToken(String cuid, String logId) {
        // try to get access token from redis
        AccessTokenResponse response = HttpUtil.getTokenFromRedis(cuid);
        if (!isLegal.test(response)) {
            // try to get project info from redis, which should'v been written into at authorization time.
            ProjectInfo projectInfo = getProjectInfoFromRedis(cuid);
            if (projectInfo != null
                    && StringUtils.hasText(projectInfo.getVoiceId())
                    && StringUtils.hasText(projectInfo.getVoiceKey())) {
                String vId = projectInfo.getVoiceId();
                String vKey = projectInfo.getVoiceKey();
                // try to get access token by using project info
                response = deviceIamClient.getAccessToken(cuid, vId, vKey, logId);
            }
        }

        return response;
    }

    public void refreshAccessToken(BaseMessage message) {
        refreshAccessTokenRedis(message.getDeviceId(), accessTokenExpire);
    }

    private Predicate<AccessTokenResponse> isLegal =
            (AccessTokenResponse response) -> response != null && StringUtils.hasText(response.getAccessToken());
}

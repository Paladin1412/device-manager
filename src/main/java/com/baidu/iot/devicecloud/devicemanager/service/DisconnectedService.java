package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.DcsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE_DH2;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.USER_STATE_DISCONNECTED;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.USER_STATE_EXCEPTION;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.dependentResponse;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DisconnectedService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private final DcsProxyClient dcsProxyClient;
    private final AccessTokenService accessTokenService;
    private final DeviceSessionService deviceSessionService;

    @Autowired
    public DisconnectedService(DcsProxyClient dcsProxyClient, AccessTokenService accessTokenService, DeviceSessionService deviceSessionService) {
        this.dcsProxyClient = dcsProxyClient;
        this.accessTokenService = accessTokenService;
        this.deviceSessionService = deviceSessionService;
    }

    @Override
    boolean canHandle(BaseMessage message) {
        int type = message.getMessageType();
        return type == MessageType.SYS_DISCONNECTED
                || type == MessageType.SYS_EXCEPTION;
    }

    @Override
    Mono<Object> work(BaseMessage message) {
        int type = message.getMessageType();
        return Mono.create(sink -> {
            try(Response response = informDcsProxy(message,
                    type == MessageType.SYS_DISCONNECTED ? USER_STATE_DISCONNECTED : USER_STATE_EXCEPTION)) {
                releaseResource(response, message);
                BaseResponse baseResponse = dependentResponse.apply(message, response);
                baseResponse.setStatus(MESSAGE_SUCCESS_CODE_DH2);
                sink.success(baseResponse);
            } catch (Exception e) {
                sink.error(e);
            }

            sink.success();
        });
    }

    private Response informDcsProxy(BaseMessage message, String stateName) throws IOException {
        String cuid = message.getDeviceId();
        log.debug("Getting access token from dproxy for cuid:{}", cuid);
        String accessToken = accessTokenService.getAccessToken(cuid, message.getLogId());
        if (StringUtils.hasText(accessToken)) {
            return dcsProxyClient.adviceUserState(message, accessToken, stateName);
        }
        log.error("Couldn't obtain the access token from dproxy, maybe caused by illegal request");
        return null;
    }

    private void releaseResource(Response response, BaseMessage message) {
        String deviceId = message.getDeviceId();
        try {
            HttpUrl url = response.request().url();
            log.debug("Releasing the assigned dcs address {}:{} from {}",
                    url.host(), url.port(), deviceId);
            AddressCache.cache.invalidate(AddressCache.getDcsAddressKey(deviceId));
            deviceSessionService.clearSession(deviceId, message.getLogId());
        } catch (Exception ignore) {
            log.warn("Releasing the assigned dcs address from {} failed", deviceId);
        }
    }
}

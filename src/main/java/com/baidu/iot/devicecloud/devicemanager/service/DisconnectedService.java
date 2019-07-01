package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.DcsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE_DH2;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.USER_STATE_DISCONNECTED;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.USER_STATE_EXCEPTION;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.dependentResponse;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;

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
        String cuid = message.getDeviceId();
        String accessToken = accessTokenService.getAccessToken(cuid, message.getLogId());
        log.debug("Getting access token from dproxy for {}:{}", cuid, accessToken);
        releaseResource(message);

        int type = message.getMessageType();
        if (StringUtils.hasText(accessToken)) {
            CompletableFuture<Response> future = dcsProxyClient.adviceUserStateAsync(message, accessToken, type == MessageType.SYS_DISCONNECTED ? USER_STATE_DISCONNECTED : USER_STATE_EXCEPTION);
            if (future != null) {
                return Mono.from(
                        Mono.fromFuture(future)
                                .flatMap(r -> {
                                    try {
                                        if (r.isSuccessful()) {
                                            log.debug("Informing dcs that {} has disconnected successfully", cuid);
                                        }
                                        BaseResponse baseResponse = dependentResponse.apply(message, r);
                                        baseResponse.setStatus(MESSAGE_SUCCESS_CODE_DH2);
                                        return Mono.just(baseResponse);
                                    } finally {
                                        close(r);
                                    }
                                })
                                .switchIfEmpty(Mono.defer(
                                        () -> failedResponse(message)
                                ))
                                .onErrorResume(t -> {
                                    log.error("Informing dcs failed", t);
                                    return failedResponse(message);
                                })
                );
            }
        }
        log.error("Couldn't obtain the access token from dproxy, maybe caused by illegal request");
        return Mono.from(failedResponse(message));
    }

    private void releaseResource(BaseMessage message) {
        String deviceId = message.getDeviceId();
        try {
            String key = AddressCache.getDcsAddressKey(deviceId);
            InetSocketAddress address = AddressCache.cache.get(key);
            if (address != null) {
                log.debug("Releasing the assigned dcs address {} for {}", address, deviceId);
                AddressCache.cache.invalidate(key);
            }
            deviceSessionService.clearSession(deviceId, message.getCltId(), message.getLogId());
        } catch (Exception ignore) {
            log.warn("Releasing the assigned dcs address from {} failed", deviceId);
        }
    }

    private Mono<BaseResponse> failedResponse(BaseMessage message) {
        BaseResponse baseResponse = failedResponses.apply(message.getLogId(), "Informing dcs failed");
        baseResponse.setStatus(MESSAGE_SUCCESS_CODE_DH2);
        return Mono.just(baseResponse);
    }
}

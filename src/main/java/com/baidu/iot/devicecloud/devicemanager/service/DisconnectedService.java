package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.DcsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenResponse;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.USER_STATE_DISCONNECTED;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.USER_STATE_EXCEPTION;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.deleteTokenFromRedis;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.dependentResponse;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getTokenFromRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DisconnectedService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private final DcsProxyClient dcsProxyClient;

    @Autowired
    public DisconnectedService(DcsProxyClient dcsProxyClient) {
        this.dcsProxyClient = dcsProxyClient;
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
            Response response = null;
            try {
                response = informDcsProxy(message,
                        type == MessageType.SYS_DISCONNECTED ? USER_STATE_DISCONNECTED : USER_STATE_EXCEPTION);
                if (response != null) {
                    releaseResource(response, message);
                    sink.success(dependentResponse.apply(message, response));
                }
            } catch (Exception e) {
                e.printStackTrace();
                sink.error(e);
            } finally {
                HttpUtil.close(response);
            }

            sink.success();
        });
    }

    private Response informDcsProxy(BaseMessage message, String stateName) {
        String cuid = message.getDeviceId();
        log.debug("Getting access token from dproxy for cuid:{}", cuid);
        AccessTokenResponse response = getTokenFromRedis(cuid);
        if (response != null && StringUtils.hasText(response.getAccessToken())) {
            return dcsProxyClient.adviceUserState(message,
                    response.getAccessToken(), stateName);
        }
        log.error("Couldn't obtain the access token from dproxy, maybe caused by illegal request. AccessTokenResponse={}",
                response);
        return null;
    }

    private void releaseResource(Response response, BaseMessage message) {
        try {
            HttpUrl url = response.request().url();
            log.debug("Releasing the assigned dcs address {}:{} from {}",
                    url.host(), url.port(), message.getDeviceId());
            AddressCache.cache.invalidate(AddressCache.getDcsAddressKey(message.getDeviceId()));
            deleteTokenFromRedis(message.getDeviceId());
        } catch (Exception ignore) {
            log.warn("Releasing the assigned dcs address from {} failed", message.getDeviceId());
        }
    }
}

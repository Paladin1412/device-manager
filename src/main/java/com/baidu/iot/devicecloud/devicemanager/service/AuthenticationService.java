package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.AuthorizationMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.LocalServerInfo;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.DcsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.DeviceIamClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.HttpUtil;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_ALIVE_INTERVAL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_PRIVATE_ERROR;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.isDcsOk;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.projectExist;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AuthenticationService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private static final int ONE_MINUTE_SECONDS = 60;
    private Cache<String, Optional<DeviceResource>> authCache;
    private DeviceIamClient client;
    private DcsProxyClient dcsProxyClient;
    private LocalServerInfo localServerInfo;

    // project info are almost immutable
    // 14 * 24 * 60 * 60 = 1209600
    @Value("${expire.resource.project: 1209600}")
    private long projectResourceExpire;

    @Value("${heartbeat.between.device.dh: 60}")
    private int aliveInterval;

    @Autowired
    public AuthenticationService(DeviceIamClient client,
                                 DcsProxyClient dcsProxyClient,
                                 LocalServerInfo localServerInfo) {
        this.client = client;
        this.dcsProxyClient = dcsProxyClient;
        this.localServerInfo = localServerInfo;

        authCache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(ONE_MINUTE_SECONDS, TimeUnit.SECONDS)
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .removalListener(n -> log.info("Removed ({}, {}), caused by: {}", n.getKey(), n.getValue(), n.getCause().toString()))
                .build();
    }

    @Override
    boolean canHandle(BaseMessage message) {
        return message.getMessageType() == MessageType.AUTHORIZATION;
    }

    @SuppressWarnings("unchecked")
    @Override
    Mono<Object> work(BaseMessage message) {
        AuthorizationMessage msg = (AuthorizationMessage) message;
        // update bns
        msg.setBns(localServerInfo.toString());

        Mono mono = Mono.justOrEmpty(auth(msg))
                .log()
                .filter(deviceResource -> deviceResource != null && StringUtils.hasText(deviceResource.getAccessToken()))
                .doOnNext(this::writeProjectInfoToDproxy)
                .flatMap(deviceResource -> {
                    Response response = null;
                    try {
                        response = informDcsProxy(deviceResource, msg);
                        if (response != null && isDcsOk.test(response)) {
                            assignAddr(response, deviceResource);
                            return Mono.just(successResponses.get());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        HttpUtil.close(response);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.just(failedResponses.get()));

        return Mono.from(mono);
    }

    private Optional<DeviceResource> auth(final AuthorizationMessage message) {
        String key = message.getDeviceId();

        try {
            return authCache.get(key, () -> {
                log.debug("Getting access token from remote server for {}", key);
                return Optional.ofNullable(client.auth(message));
            });
        } catch (Exception e) {
            log.error("Something goes wrong: {}", e);
            return Optional.empty();
        }
    }

    // TODO: if dcs respond a directive, then it should be sent to device
    private Response informDcsProxy(DeviceResource deviceResource, BaseMessage message) {
        return dcsProxyClient.adviceUserState(message,
                deviceResource.getAccessToken(), DCSProxyConstant.USER_STATE_CONNECTED);
    }

    private CompletableFuture<Response> informDcsProxyAsync(DeviceResource deviceResource, BaseMessage message) {
        return dcsProxyClient.adviceUserStateAsync(message,
                deviceResource.getAccessToken(), DCSProxyConstant.USER_STATE_CONNECTED);
    }

    private void writeProjectInfoToDproxy(DeviceResource deviceResource) {
        if (projectExist(deviceResource.getCuid())) {
            return;
        }
        ProjectInfo projectInfo = deviceResource.getProjectInfo();
        if (projectInfo != null
                && StringUtils.hasText(projectInfo.getVoiceId())
                && StringUtils.hasText(projectInfo.getVoiceKey())) {

            DproxyClientProvider
                    .getInstance()
                    .hset(CommonConstant.PROJECT_INFO_KEY_PREFIX + projectInfo.getId(),
                            projectResourceExpire,
                            CommonConstant.PROJECT_INFO,
                            deviceResource.getProjectInfo());
        }
    }

    private final Supplier<DataPointMessage> successResponses = () -> {
        DataPointMessage response = new DataPointMessage();
        response.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID);
        response.setId(IdGenerator.nextId());
        response.setPath(PathUtil.lookAfterPrefix(DATA_POINT_ALIVE_INTERVAL));
        response.setPayload(Integer.toString(aliveInterval));
        return response;
    };

    private final Supplier<DataPointMessage> failedResponses = () -> {
        DataPointMessage response = new DataPointMessage();
        response.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED);
        response.setId(IdGenerator.nextId());
        response.setPath(PathUtil.lookAfterPrefix(DATA_POINT_PRIVATE_ERROR));
        return response;
    };

    /**
     * Assign the dcs address to device only after it is authorized. This assigning will be removed after this device logout.
     * @param response the response from dcs
     * @param deviceResource which device the dcs address would be assigned to.
     */
    private void assignAddr(Response response, DeviceResource deviceResource) {
        try {
            HttpUrl url = response.request().url();
            InetSocketAddress successResponseAddress = new InetSocketAddress(InetAddress.getByName(url.host()), url.port());
            log.debug("Assigning this dcs address {}:{} to {}",
                    url.host(), url.port(), deviceResource.getCuid());
            AddressCache.cache.put(AddressCache.getDcsAddressKey(deviceResource.getCuid()), successResponseAddress);
        } catch (Exception ignore) {
            log.warn("Assigning dcs address to {} failed", deviceResource.getCuid());
        }
    }
}
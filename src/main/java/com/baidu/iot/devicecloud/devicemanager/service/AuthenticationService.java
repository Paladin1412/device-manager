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
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_DATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_ALIVE_INTERVAL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_PRIVATE_ERROR;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_STATUS;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedDataPointResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.projectExist;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AuthenticationService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private Cache<String, Optional<DeviceResource>> authCache;

    private final DeviceIamClient client;
    private final DcsProxyClient dcsProxyClient;
    private final AccessTokenService accessTokenService;
    private final LocalServerInfo localServerInfo;

    private ExecutorService commonSideExecutor;

    // project info are almost immutable
    // 14 * 24 * 60 * 60 = 1209600
    @Value("${expire.resource.project: 1209600}")
    private long projectResourceExpire;

    @Value("${heartbeat.between.device.dh: 60}")
    private int aliveInterval;

    @Autowired
    public AuthenticationService(DeviceIamClient client,
                                 DcsProxyClient dcsProxyClient,
                                 AccessTokenService accessTokenService,
                                 LocalServerInfo localServerInfo) {
        this.client = client;
        this.dcsProxyClient = dcsProxyClient;
        this.accessTokenService = accessTokenService;
        this.localServerInfo = localServerInfo;

        authCache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .removalListener(LogUtils.REMOVAL_LOGGER.apply(log))
                .build();

        commonSideExecutor = new ThreadPoolExecutor(0, 50,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    @Override
    boolean canHandle(BaseMessage message) {
        return message.getMessageType() == MessageType.AUTHORIZATION;
    }

    @Override
    Mono<Object> work(BaseMessage message) {
        AuthorizationMessage msg = (AuthorizationMessage) message;
        // update bns
        msg.setBns(localServerInfo.toString());

        return Mono.from(Mono.justOrEmpty(auth(msg))
                .filter(deviceResource -> deviceResource != null && StringUtils.hasText(deviceResource.getAccessToken()))
                .doOnNext(this::writeProjectInfoToDproxy)
                .flatMap(deviceResource ->
                        Mono.fromFuture(informDcsProxyAsync(deviceResource, msg))
                        .flatMap(response -> {
                            try(ResponseBody body = response.body()) {
                                if (response.isSuccessful() && body != null) {
                                    //noinspection BlockingMethodInNonBlockingContext
                                    JsonNode jsonNode = JsonUtil.readTree(body.bytes());
                                    log.debug("Dcs responses: {}", jsonNode.toString());
                                    if (jsonNode.path(PAM_PARAM_STATUS).asInt(MESSAGE_FAILURE_CODE) == MESSAGE_SUCCESS_CODE) {
                                        assignAddr(response, deviceResource);
                                        return Mono.just(successResponses.get());
                                    } else {
                                        ArrayNode data = (ArrayNode)jsonNode.path(JSON_KEY_DATA);
                                        if (data != null && data.size() > 0) {
                                            DataPointMessage failed = new DataPointMessage();
                                            failed.setVersion(DEFAULT_VERSION);
                                            failed.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED);
                                            failed.setId(IdGenerator.nextId());
                                            failed.setPath(PathUtil.lookAfterPrefix(DATA_POINT_PRIVATE_ERROR));
                                            failed.setPayload(data.get(0).toString());
                                            return Mono.just(failed);
                                        }
                                    }
                                }
                            } catch (Exception e){
                                log.error("Checking if the dcs response ok failed", e);
                            }
                            return Mono.empty();
                        }))
                .onErrorResume(Mono::error)
                .switchIfEmpty(
                        Mono.defer(() ->
                                Mono.just(
                                        failedDataPointResponses.get()
                                                .apply(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED, null)
                                )
                        )
                ));
    }

    private Optional<DeviceResource> auth(final AuthorizationMessage message) {
        String cuid = message.getCuid();
        String uuid = message.getUuid();
        String key = String.format("%s_%s_%s", uuid, cuid, message.getToken());

        try {
            return authCache.get(key, () -> {
                DeviceResource dr = client.auth(message);
                if (dr != null && StringUtils.hasText(dr.getAccessToken())) {
                    accessTokenService.cacheAccessToken(
                            Optional.ofNullable(cuid).orElse(uuid),
                            dr.getAccessToken()
                    );
                    return Optional.of(dr);
                }
                return Optional.empty();
            });
        } catch (Exception e) {
            log.error("Obtaining the access token failed", e);
            return Optional.empty();
        }
    }

    private CompletableFuture<Response> informDcsProxyAsync(DeviceResource deviceResource, BaseMessage message) {
        return dcsProxyClient.adviceUserStateAsync(message,
                deviceResource.getAccessToken(), DCSProxyConstant.USER_STATE_CONNECTED);
    }

    private void writeProjectInfoToDproxy(DeviceResource deviceResource) {
        commonSideExecutor.submit(() -> {
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
        );
    }

    private final Supplier<DataPointMessage> successResponses = () -> {
        DataPointMessage response = new DataPointMessage();
        response.setVersion(DEFAULT_VERSION);
        response.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID);
        response.setId(IdGenerator.nextId());
        response.setPath(PathUtil.lookAfterPrefix(DATA_POINT_ALIVE_INTERVAL));
        response.setPayload(Integer.toString(aliveInterval));
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

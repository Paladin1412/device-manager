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
import com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.baidu.iot.log.Log;
import com.baidu.iot.log.LogProvider;
import com.baidu.iot.log.Stopwatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_DATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_ALIVE_INTERVAL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_PRIVATE_ERROR;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_STATUS;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedDataPointResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.projectExist;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.writeProjectResourceToRedis;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AuthenticationService extends AbstractLinkableHandlerAdapter<BaseMessage> {
    private static final Logger infoLog = LoggerFactory.getLogger("infoLog");
    private static final LogProvider logProvider = LogProvider.getInstance();

    private Cache<String, Optional<DeviceResource>> authCache;

    private final DeviceIamClient client;
    private final DcsProxyClient dcsProxyClient;
    private final DeviceSessionService deviceSessionService;
    private final LocalServerInfo localServerInfo;

    // project info are almost immutable
    // 14 * 24 * 60 * 60 = 1209600
    @Value("${expire.resource.project: 1209600}")
    private long projectResourceExpire;

    @Value("${heartbeat.between.device.dh: 60}")
    private int aliveInterval;

    @Autowired
    public AuthenticationService(DeviceIamClient client,
                                 DcsProxyClient dcsProxyClient,
                                 DeviceSessionService deviceSessionService,
                                 LocalServerInfo localServerInfo) {
        this.client = client;
        this.dcsProxyClient = dcsProxyClient;
        this.deviceSessionService = deviceSessionService;
        this.localServerInfo = localServerInfo;

        authCache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .removalListener(LogUtils.REMOVAL_LOGGER.apply(log))
                .build();
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

        Log spanLog = logProvider.get(message.getLogId());
        spanLog.setCuId(message.getDeviceId());
        infoLog.info(spanLog.format(String.format("[AUTH] Authoring:%s", String.valueOf(msg))));

        return Mono.from(Mono.justOrEmpty(auth(msg))
                .filter(deviceResource -> deviceResource != null && StringUtils.hasText(deviceResource.getAccessToken()))
                .doOnNext(deviceResource -> {
                    Optional.ofNullable(message.getDeviceIp()).ifPresent(deviceResource::setIp);
                    Optional.ofNullable(message.getDevicePort()).ifPresent(deviceResource::setPort);
                    writeProject(deviceResource);
                })
                .flatMap(deviceResource -> {
                    Stopwatch dcsStopwatch = spanLog.time("dcs");
                    return Mono.fromFuture(informDcsProxyAsync(deviceResource, msg).handleAsync(
                            (response, throwable) -> {
                                try(ResponseBody body = response.body()) {
                                    dcsStopwatch.pause();
                                    infoLog.info(spanLog.format("[AUTH] Informed dcs"));
                                    if (response.isSuccessful() && body != null) {
                                        JsonNode jsonNode = JsonUtil.readTree(body.bytes());
                                        log.debug("Dcs responses: {}", jsonNode.toString());
                                        infoLog.info(spanLog.format(String.format("[AUTH] Dcs responses:%s", jsonNode.toString())));
                                        if (jsonNode.path(PAM_PARAM_STATUS).asInt(MESSAGE_FAILURE_CODE) == MESSAGE_SUCCESS_CODE) {
                                            assignAddr(response, deviceResource);
                                            deviceSessionService.setSession(deviceResource, message.getLogId());
                                            return successResponses.get();
                                        } else {
                                            ArrayNode data = (ArrayNode)jsonNode.path(JSON_KEY_DATA);
                                            if (data != null && data.size() > 0) {
                                                DataPointMessage failed = new DataPointMessage();
                                                failed.setVersion(DEFAULT_VERSION);
                                                failed.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED);
                                                failed.setId(IdGenerator.nextId());
                                                failed.setPath(PathUtil.lookAfterPrefix(DATA_POINT_PRIVATE_ERROR));
                                                failed.setPayload(data.get(0).toString());
                                                return failed;
                                            }
                                        }
                                    }
                                } catch (Exception e){
                                    log.error("Checking if the dcs response ok failed", e);
                                } finally {
                                    close(response);
                                }
                                return null;
                            }
                    ));
                }
                )
                .onErrorResume(Mono::error)
                .switchIfEmpty(
                        Mono.defer(() ->
                                Mono.just(
                                        failedDataPointResponses.get()
                                                .apply(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED, null)
                                )
                        )
                )
                .doFinally(signalType -> logProvider.revoke(message.getLogId()))
        );
    }

    private Optional<DeviceResource> auth(final AuthorizationMessage message) {
        String cuid = message.getCuid();
        String uuid = message.getUuid();
        String key = String.format("%s_%s_%s", uuid, cuid, message.getToken());

        try {
            return authCache.get(key, () -> {
                Log spanLog = logProvider.get(message.getLogId());
                spanLog.setCuId(message.getDeviceId());
                Stopwatch stopwatch = spanLog.time("di");
                DeviceResource dr = client.auth(message);
                stopwatch.pause();
                infoLog.info(spanLog.format(String.format("[AUTH] Read authorization info:%s", String.valueOf(dr))));
                if (dr != null && StringUtils.hasText(dr.getAccessToken())) {
                    dr.setCltId(message.getCltId());
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

    private void writeProject(DeviceResource deviceResource) {
        ProjectInfo projectInfo = deviceResource.getProjectInfo();
        if (projectInfo != null && !projectExist(projectInfo.getId())) {
            if (StringUtils.hasText(projectInfo.getVoiceId())
                    && StringUtils.hasText(projectInfo.getVoiceKey())) {
                writeProjectResourceToRedis(projectInfo, projectResourceExpire);
            }
        }
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

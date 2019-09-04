package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.LocalServerInfo;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_EQUALITY_SIGN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SEMICOLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_PRIVATE_ERROR;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_STATUS;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class HttpUtil {
    private static final DproxyClientProvider clientProvider = DproxyClientProvider.getInstance();
    public static void close(Response response) {
        try {
            if (response != null) {
                response.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static Predicate<Response> isDcsOk = response -> {
        try(ResponseBody body = response.body()){
            if (response.isSuccessful() && body != null) {
                JsonNode resp = JsonUtil.readTree(body.bytes());
                if (resp != null) {
                    log.debug("Dcs responses: {}", resp.toString());
                    return !resp.isNull() && resp.has(PAM_PARAM_STATUS)
                            && resp.get(PAM_PARAM_STATUS).asInt(MESSAGE_FAILURE_CODE) == MESSAGE_SUCCESS_CODE;
                }
            }
        } catch (Exception e) {
            log.error("Checking if the dcs response ok failed", e);
        }

        return false;
    };

    public static Predicate<Integer> isCoapRequest = code -> code >= CoapConstant.COAP_METHOD_EMPTY
            && code <= CoapConstant.COAP_METHOD_DELETE;

    public static Predicate<DataPointMessage> isCoapOk = message -> {
        int code = message.getCode();
        return code >= CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_CREATED
                && code <= CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_CONTINUE;
    };

    public static BiFunction<BaseMessage, Response, BaseResponse> dependentResponse =
            (BaseMessage message, Response response) -> {
                BaseResponse baseResponse = new BaseResponse();
                baseResponse.setLogId(message.getLogId());
                if (isDcsOk.test(response)) {
                    baseResponse.setCode(MESSAGE_SUCCESS_CODE);
                    baseResponse.setMessage(MESSAGE_SUCCESS);
                } else {
                    baseResponse.setCode(MESSAGE_FAILURE_CODE);
                    baseResponse.setMessage(MESSAGE_FAILURE);
                }
                return baseResponse;
            };

    private static BaseResponse baseResponse(Integer code, String message, String logId) {
        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setLogId(logId);
        baseResponse.setCode(code);
        baseResponse.setMessage(message);
        return baseResponse;
    }

    public static BiFunction<String, String, BaseResponse> successResponse =
            (String logId, String message) -> baseResponse(MESSAGE_SUCCESS_CODE, message, logId);

    public static BiFunction<String, DataPointMessage, BaseResponse> successResponseFromDP =
            (String logId, DataPointMessage message) -> {
                BaseResponse baseResponse = baseResponse(MESSAGE_SUCCESS_CODE, null, logId);
                baseResponse.setData(message.getPayload());
                return baseResponse;
            };

    private static Function<String, BaseResponse> successResponses =
            logId -> baseResponse(MESSAGE_SUCCESS_CODE, MESSAGE_SUCCESS, logId);

    public static BiFunction<String, String, BaseResponse> failedResponses =
            (String logId, String message) -> baseResponse(MESSAGE_FAILURE_CODE, message, logId);

    public static Function<BaseMessage, BaseResponse> successResponsesWithMessage =
            (BaseMessage message) -> successResponses.apply(message != null ? message.getLogId() : null);

    private static BiFunction<Integer, String, DataPointMessage> parseErrorDataPointMessage =
            (code, content) -> {
                DataPointMessage response = new DataPointMessage();
                response.setVersion(DEFAULT_VERSION);
                response.setCode(code);
                response.setId(IdGenerator.nextId());
                response.setPath(PathUtil.lookAfterPrefix(DATA_POINT_PRIVATE_ERROR));
                if (StringUtils.hasText(content)) {
                    response.setPayload(content);
                }
                return response;
            };

    public static DataPointMessage transformedDataPointResponses(DataPointMessage message, Integer code) {
        message.setPayload(null);
        message.setPath(null);
        message.setMisc(null);
        message.setCode(code);
        return message;
    }

    public static DataPointMessage dataPointResponses(DataPointMessage origin, int code, String message) {
        DataPointMessage response = new DataPointMessage();
        response.setVersion(origin == null ? DEFAULT_VERSION : origin.getVersion());
        response.setCode(code);
        response.setId(origin == null ? IdGenerator.nextId() : origin.getId());
        response.setPath(DataPointConstant.DATA_POINT_PRIVATE_ERROR);
        if (StringUtils.hasText(message)) {
            response.setPayload(message);
        }
        return response;
    }

    public static Supplier<Function<String, Mono<ServerResponse>>> deviceMayNotOnline =
            () -> uuid -> ServerResponse.badRequest().body(BodyInserters.fromObject(failedResponses.apply(null, String.format("This device may not online: %s", uuid))));

    public static Supplier<BiFunction<Integer, String, DataPointMessage>> failedDataPointResponses =
            () -> parseErrorDataPointMessage;

    public static BiConsumer<ServerRequest, BaseMessage> assembleFromHeader =
            (request, message) -> {
                if (message == null || request == null) {
                    return;
                }

                message.setBns(String.format("%s:%s", request.uri().getHost(), request.uri().getPort()));
                if (LocalServerInfo.localServerPort <= 0) {
                    LocalServerInfo.localServerPort = request.uri().getPort();
                }

                MediaType mediaType = request.headers().contentType().orElse(MediaType.APPLICATION_JSON_UTF8);
                message.setContentType(String.format("%s/%s", mediaType.getType(), mediaType.getSubtype()));
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_USER_AGENT))
                        .ifPresent(message::setUserAgent);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_ORIGIN_CLIENT_IP))
                        .ifPresent(message::setDeviceIp);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_ORIGIN_CLIENT_PORT))
                        .ifPresent(message::setDevicePort);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_LOG_ID)).ifPresent(message::setLogId);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_SN)).ifPresent(message::setSn);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_STANDBY_DEVICE_ID))
                        .ifPresent(message::setStandbyDeviceId);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_STREAMING_VERSION))
                        .ifPresent(message::setStreamingVersion);
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_CLT_ID)).ifPresent(clt_id -> {
                    message.setCltId(clt_id);

                    // clt_id = third_product_id$cuid$dh_ip$dh_port$limit_bytes
                    String[] items = clt_id.split(Pattern.quote(CommonConstant.SPLITTER_DOLLAR));
                    if (items.length > 1) {
                        message.setProductId(items[0]);
                        message.setDeviceId(items[1]);
                    }
                });
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_MESSAGE_TYPE)).ifPresent(
                        type -> {
                            try {
                                message.setMessageType(Integer.parseInt(type));
                            } catch (NumberFormatException ignore) {}
                        }
                );
                Optional.ofNullable(getFirst(request, CommonConstant.HEADER_MESSAGE_TIMESTAMP)).ifPresent(
                        ts -> {
                            try {
                                message.setTimestamp(Long.parseLong(ts));
                            } catch (NumberFormatException ignore) {}
                        }
                );
            };

    @Nullable
    public static String getFirst(ServerRequest request, String key) {
        ServerRequest.Headers headers = request.headers();
        List<String> values = headers.header(key);
        return values.size() > 0 ? values.get(0) : null;
    }

    public static String getSessionKey(String cuid, String cltId) {
        return String.format("%s:%s", CommonConstant.SESSION_KEY_PREFIX + cuid, cltId);
    }

    public static void deviceOnlineStatus(final String cuid, final boolean online) {
        clientProvider.hset(CommonConstant.SESSION_KEY_PREFIX + cuid, -1, CommonConstant.SESSION_DEVICE_ONLINE_STATUS, online);
    }

    public static boolean writeTokenToRedis(final String cuid, final String accessToken, final long expire) {
        return clientProvider.hset(CommonConstant.SESSION_KEY_PREFIX + cuid, expire, CommonConstant.SESSION_DEVICE_ACCESS_TOKEN, accessToken);
    }

    @Nullable
    public static String getTokenFromRedis(final String cuid) {
        return Optional.ofNullable(clientProvider
                .hget(CommonConstant.SESSION_KEY_PREFIX + cuid, CommonConstant.SESSION_DEVICE_ACCESS_TOKEN, String.class))
                .orElseGet(() -> clientProvider
                        .get(CommonConstant.SESSION_KEY_PREFIX + cuid));
    }

    public static void deleteTokenFromRedis(final String cuid) {
        clientProvider.hdel(CommonConstant.SESSION_KEY_PREFIX + cuid, CommonConstant.SESSION_DEVICE_ACCESS_TOKEN);
    }

    public static void freshSession(String cuid, long expire) {
        if (StringUtils.hasText(cuid) && expire > 0) {
            clientProvider.expire(CommonConstant.SESSION_KEY_PREFIX + cuid, expire);
        }
    }

    public static void deleteSessionFromRedis(final String cuid) {
        clientProvider.del(CommonConstant.SESSION_KEY_PREFIX + cuid);
    }

    public static boolean projectExist(final long id) {
        return clientProvider.exists(CommonConstant.PROJECT_RESOURCE_KEY_PREFIX + id, CommonConstant.PROJECT_INFO);
    }

    public static boolean deviceExist(final String cuid) {
        return clientProvider.exists(CommonConstant.SESSION_KEY_PREFIX + cuid, CommonConstant.SESSION_DEVICE_INFO) ||
                clientProvider.exists(CommonConstant.DEVICE_RESOURCE_KEY_PREFIX + cuid);
    }

    public static void writeDeviceResourceToRedis(DeviceResource deviceResource, long expire) {
        String cuid = Optional.ofNullable(deviceResource.getCuid()).orElse(deviceResource.getDeviceUuid());
        if (StringUtils.hasText(cuid)) {
            log.debug("Writing device resource to session. cuid:{}", cuid);
            clientProvider.hset(CommonConstant.SESSION_KEY_PREFIX + cuid,
                            expire,
                            CommonConstant.SESSION_DEVICE_INFO,
                            deviceResource);
        }
    }

    public static void writeProjectResourceToRedis(ProjectInfo projectInfo, long projectResourceExpire) {
        clientProvider.hset(CommonConstant.PROJECT_RESOURCE_KEY_PREFIX + projectInfo.getId(),
                projectResourceExpire,
                CommonConstant.PROJECT_INFO,
                projectInfo);
    }

    public static ProjectInfo getProjectResourceFromRedis(final String cuid) {
        if (StringUtils.hasText(cuid) && cuid.length() >= 4) {
            long projectId = IdGenerator.projectId(cuid);
            return clientProvider.hget(CommonConstant.PROJECT_RESOURCE_KEY_PREFIX + projectId, CommonConstant.PROJECT_INFO, ProjectInfo.class);
        }
        return null;
    }

    @Nullable
    public static DeviceResource getDeviceInfoFromRedis(final String cuid) {
        return Optional.ofNullable(clientProvider
                .hget(CommonConstant.SESSION_KEY_PREFIX + cuid, CommonConstant.SESSION_DEVICE_INFO, DeviceResource.class))
                .orElseGet(() -> clientProvider
                        .hget(CommonConstant.DEVICE_RESOURCE_KEY_PREFIX + cuid, CommonConstant.SESSION_DEVICE_INFO, DeviceResource.class));
    }

    public static void deleteDeviceResourceFromRedis(final String cuid) {
        clientProvider.del(CommonConstant.DEVICE_RESOURCE_KEY_PREFIX + cuid);
    }

    /**
     * Trying to get the boundary under convention, means no preconditions would be checked.
     * @param contentType the Content-Type header string
     * @return boundary
     */
    public static String getBoundary(String contentType) {
        String[] items = contentType.split(Pattern.quote(SPLITTER_SEMICOLON));
        return StringUtils.trimAllWhitespace(items[1].split(Pattern.quote(SPLITTER_EQUALITY_SIGN))[1]);
    }
}

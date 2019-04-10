package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.LocalServerInfo;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenRequest;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenResponse;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_FAILURE_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_EQUALITY_SIGN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SEMICOLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_STATUS;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class HttpUtil {
    public static void close(Response response) {
        try {
            if (response != null) {
                response.close();
            }
        } catch (Exception ignored) {
        }
    }

    public static Predicate<Response> isDcsOk = response -> {
        ResponseBody body = response.body();
        if (response.isSuccessful() && body != null) {
            try {
                JsonNode resp = JsonUtil.readTree(body.bytes());
                return resp != null && !resp.isNull() && resp.has(PAM_PARAM_STATUS)
                        && resp.get(PAM_PARAM_STATUS).asInt(-1) == 0;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    };

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

    public static Function<String, BaseResponse> successResponses =
            (String logId) -> {
                BaseResponse baseResponse = new BaseResponse();
                if (StringUtils.hasText(logId)) {
                    baseResponse.setLogId(logId);
                }
                baseResponse.setCode(MESSAGE_SUCCESS_CODE);
                baseResponse.setMessage(MESSAGE_SUCCESS);
                return baseResponse;
            };

    public static BiFunction<String, String, BaseResponse> failedResponses =
            (String logId, String message) -> {
                BaseResponse baseResponse = new BaseResponse();
                baseResponse.setLogId(logId);
                baseResponse.setCode(MESSAGE_FAILURE_CODE);
                baseResponse.setMessage(MESSAGE_FAILURE);
                baseResponse.setMessage(message);
                return baseResponse;
            };

    public static Function<BaseMessage, BaseResponse> successResponsesWithMessage =
            (BaseMessage message) -> successResponses.apply(message != null ? message.getLogId() : null);

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

                /*Optional.ofNullable(getFirst(request, CommonConstant.HEADER_NEED_ACK)).ifPresent(
                        needAck -> message.setNeedAck(Boolean.valueOf(needAck))
                );*/
            };

    @Nullable
    public static String getFirst(ServerRequest request, String key) {
        ServerRequest.Headers headers = request.headers();
        List<String> values = headers.header(key);
        return values.size() > 0 ? values.get(0) : null;
    }

    @Nullable
    public static AccessTokenResponse getTokenFromRedis(final AccessTokenRequest tokenRequest) {
        return getTokenFromRedis(tokenRequest.getCuid());
    }

    @Nullable
    public static AccessTokenResponse getTokenFromRedis(final String cuid) {
        return DproxyClientProvider
                .getInstance()
                .hget(CommonConstant.SESSION_KEY_PREFIX + cuid,
                        CommonConstant.SESSION_DEVICE_ACCESS_TOKEN, AccessTokenResponse.class);
    }

    public static void deleteTokenFromRedis(final String cuid) {
        DproxyClientProvider
                .getInstance()
                .del(CommonConstant.SESSION_KEY_PREFIX + cuid);
    }

    public static boolean projectExist(final String cuid) {
        if (StringUtils.hasText(cuid) && cuid.length() > 4) {
            int projectId = Integer.parseInt(cuid.substring(0, 4), 16);
            return DproxyClientProvider
                    .getInstance()
                    .exists(CommonConstant.PROJECT_INFO_KEY_PREFIX + projectId);
        }
        return false;
    }

    public static boolean refreshAccessTokenRedis(final String cuid, long expire) {
        return DproxyClientProvider
                .getInstance()
                .expire(CommonConstant.SESSION_KEY_PREFIX + cuid,
                        expire);
    }

    @Nullable
    public static ProjectInfo getProjectInfoFromRedis(final String cuid) {
        if (StringUtils.hasText(cuid) && cuid.length() > 4) {
            int projectId = Integer.parseInt(cuid.substring(0, 4), 16);
            return DproxyClientProvider
                    .getInstance()
                    .hget(CommonConstant.PROJECT_INFO_KEY_PREFIX + projectId,
                            CommonConstant.PROJECT_INFO, ProjectInfo.class);
        }
        return null;
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
package com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient;


import com.baidu.iot.devicecloud.devicemanager.bean.AuthorizationMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceResource;
import com.baidu.iot.devicecloud.devicemanager.bean.device.ProjectInfo;
import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenRequest;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Optional;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DeviceIamClient extends AbstractHttpClient {
    private static final String DEVICE_IAM_API_VERSION = "v1";
    private static final String[] DEVICE_AUTH_PATH = {"auth"};
    private static final String[] DEVICE_ACCESS_TOKEN_PATH = {"device", "accessToken"};

    // 14 * 24 * 60 * 60 = 1209600
    @Value("${expire.token.access: 300}")
    private long accessTokenExpire;

    @Value("${di.scheme:http://}")
    private String diScheme;

    @Value("${di.url:}")
    private String diUrl;

    @Nullable
    public DeviceResource auth(AuthorizationMessage authRequest) {
        try(Response response = getDeviceResource(authRequest)) {
            if (response != null && response.isSuccessful()) {
                ResponseBody body = response.body();
                Assert.notNull(body, "Authorization Response body is null");
                DeviceResource deviceResource = JsonUtil.deserialize(body.string(), DeviceResource.class);
                Assert.notNull(deviceResource, "Device resource is null");
                log.debug("Read authorization info:\n{}", deviceResource);

                deviceResource.setCuid(Optional.ofNullable(authRequest.getCuid()).orElse(authRequest.getUuid()));
                Optional.ofNullable(authRequest.getDeviceIp()).ifPresent(deviceResource::setIp);
                Optional.ofNullable(authRequest.getDevicePort()).ifPresent(deviceResource::setPort);

                String accessToken = getAccessToken(deviceResource, authRequest);
                if (StringUtils.hasText(accessToken)) {
                    deviceResource.setAccessToken(accessToken);
                    return deviceResource;
                }
            }
        } catch (Exception e) {
            log.error("Authoring failed", e);
        }

        return null;
    }

    @Retryable(value = {SocketTimeoutException.class}, backoff = @Backoff(200))
    private Response getDeviceResource(AuthorizationMessage authRequest) {
        Request request = buildRequest(authRequest, DEVICE_AUTH_PATH, HttpMethod.POST);
        Assert.notNull(request, "Authorization Request is null");
        return sendSync(request);
    }

    private String getAccessToken(DeviceResource deviceResource, AuthorizationMessage authRequest) {
        String cuid = deviceResource.getCuid();
        ProjectInfo projectInfo = deviceResource.getProjectInfo();
        Assert.notNull(projectInfo, "Project is null");
        String vId = projectInfo.getVoiceId();
        String vKey = projectInfo.getVoiceKey();
        return getAccessToken(cuid, vId, vKey, authRequest.getLogId());
    }

    /**
     * A service to get access token, if get, write to redis.
     * @param cuid device cuid
     * @param vId project voice id
     * @param vKey project voice key
     * @param logId log id, in generally, sn
     * @return access token string
     */
    public String getAccessToken(String cuid, String vId, String vKey, String logId) {
        AccessTokenRequest accessTokenRequest = new AccessTokenRequest(cuid, vId, vKey, logId);
        String accessToken = getAccessTokenFromIAM(accessTokenRequest);
        if (StringUtils.hasText(accessToken)) {
            log.debug("Obtain access token succeeded, writing to redis. logid:{}", logId);
            return accessToken;
        }
        log.debug("Obtain access token failed. logid:{}", logId);
        return null;
    }



    @Nullable
    @Retryable(value = {SocketTimeoutException.class}, backoff = @Backoff(200))
    private String getAccessTokenFromIAM(AccessTokenRequest accessTokenRequest) {
        Request request = buildRequest(accessTokenRequest, DEVICE_ACCESS_TOKEN_PATH, HttpMethod.GET);
        Assert.notNull(request, "Access Token Request is null");
        try(Response response = sendSync(request)) {
            if (response != null && response.isSuccessful()) {
                ResponseBody body = response.body();
                Assert.notNull(body, "Access Token Response body is null");
                String accessToken = body.string();
                if (StringUtils.hasText(accessToken)) {
                    log.debug("Read access token info: {}", accessToken);
                    return accessToken;
                }
            }
        } catch (Exception e) {
            log.error("Obtaining access token failed", e);
        }

        return null;
    }

    @Nullable
    private Request buildRequest(Object params, String[] path, HttpMethod method) {
        RequestBody requestBody = buildRequestBody(params);
        if (requestBody == null) {
            return null;
        }
        String httpUrl = getFullPath(path);
        Request.Builder requestBuilder = new Request.Builder()
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE);

        if (method == HttpMethod.POST) {
            requestBuilder
                    .url(httpUrl)
                    .post(requestBody).build();
        } else {
            UriComponentsBuilder urlBuilder;
            try {
                urlBuilder = UriComponentsBuilder.fromHttpUrl(httpUrl);
                JsonUtil.appendAsQueryParams(urlBuilder, params);
            } catch (Exception e) {
                log.error("Appending query params failed", e);
                return null;
            }
            requestBuilder
                    .url(String.valueOf(urlBuilder.build()))
                    .get();
        }

        return requestBuilder.build();
    }

    private String getFullPath(String[] path) {
        String domainAddress = getDomainAddress();
        if (!StringUtils.startsWithIgnoreCase(domainAddress, diScheme)) {
            domainAddress = diScheme + domainAddress;
        }
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(domainAddress),
                getFullRelativePath(path)
        );
    }

    /**
     * Resolve dcs address according to specified {@code message}
     * @return {@code ip:port}
     */
    @NotNull
    private String getDomainAddress() {
        String domainAddress = null;
        InetSocketAddress hashedAddress = BnsCache.getRandomDiAddress();
        if (hashedAddress != null) {
            domainAddress = PathUtil.dropOffPrefix(hashedAddress.toString(), SPLITTER_URL);
        } else if (StringUtils.hasText(diUrl)) {
            domainAddress = diUrl;
        }
        Preconditions.checkArgument(StringUtils.hasText(domainAddress), "Couldn't find any di address");
        return domainAddress;
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(DEVICE_IAM_API_VERSION),
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

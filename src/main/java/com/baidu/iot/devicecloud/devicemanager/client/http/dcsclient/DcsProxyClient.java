package com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean.DcsUserStateRequestBody;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean.DcsUserStateRequestHeader;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.builder.DcsUserStateRequestBodyBuilder;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.builder.DcsUserStateRequestHeaderBuilder;
import com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DcsProxyClient extends AbstractHttpClient {
    private static final String DCS_PROXY_ROOT = "dcs";
    private static final String[] DCS_PROXY_STATE_PATH = {"puffer2", "state"};

    @Value("${dcs.proxy.api}")
    private String dcsProxyApi;
    @Value("${dcs.proxy.bns:}")
    private String dcsProxyBns;
    @Value("${dcs.proxy.scheme:http://}")
    private String dcsProxyScheme;
    @Value("${dcs.proxy.address.state:}")
    private String dcsProxyStateAddress;

    public Response adviceUserState(BaseMessage message, String accessToken, String stateName) {
        DcsUserStateRequestHeader header = DcsUserStateRequestHeaderBuilder.buildFrom(message, accessToken);
        DcsUserStateRequestBody body = DcsUserStateRequestBodyBuilder.build(stateName, message.getCltId());
        log.debug("DcsUserStateRequestHeader: {} logid:{}", header, message.getLogId());
        log.debug("DcsUserStateRequestBody: {} logid:{}", body, message.getLogId());

        return sendSync(header, body, message);
    }

    /**
     * Advice user state to DCS Proxy by sending an async http call.
     * @param message used to assemble the async http call's header
     * @param accessToken device access token
     * @param stateName user state name
     */
    public CompletableFuture<Response> adviceUserStateAsync(BaseMessage message, String accessToken, String stateName) {
        DcsUserStateRequestHeader header = DcsUserStateRequestHeaderBuilder.buildFrom(message, accessToken);
        DcsUserStateRequestBody body = DcsUserStateRequestBodyBuilder.build(stateName, message.getCltId());
        log.debug("DcsUserStateRequestHeader: {} logid:{}", header, message.getLogId());
        log.debug("DcsUserStateRequestBody: {} logid:{}", body, message.getLogId());

        return sendAsync(header, body, message);
    }

    private Response sendSync(DcsUserStateRequestHeader dcsUserStateRequestHeader,
                              DcsUserStateRequestBody dcsUserStateRequestBody,
                              BaseMessage message) {
        Request request = buildRequest(dcsUserStateRequestHeader, dcsUserStateRequestBody, message);
        Assert.notNull(request, "DCS Proxy Request is null");
        return sendSync(request);
    }

    private CompletableFuture<Response> sendAsync(DcsUserStateRequestHeader dcsUserStateRequestHeader,
                                                  DcsUserStateRequestBody dcsUserStateRequestBody,
                                                  BaseMessage message) {
        Request request = buildRequest(dcsUserStateRequestHeader, dcsUserStateRequestBody, message);
        Assert.notNull(request, "DCS Proxy Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    @Nullable
    private Request buildRequest(DcsUserStateRequestHeader header,
                                 DcsUserStateRequestBody params,
                                 BaseMessage message) {
        RequestBody requestBody = buildRequestBody(params);
        if (requestBody == null) {
            return null;
        }
        Request.Builder builder = new Request.Builder()
                .url(getFullPath(DCS_PROXY_STATE_PATH, message))
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .header(DCSProxyConstant.HEADER_CLIENT_ID, header.getCltId())
                .header(DCSProxyConstant.HEADER_AUTHORIZATION, header.getAuthorization())
                .header(DCSProxyConstant.HEADER_CALLER_BNS, header.getBns())
                .header(DCSProxyConstant.HEADER_SN, header.getSn())
                .header(DCSProxyConstant.HEADER_CONTENT_TYPE, header.getContentType())
                .header(DCSProxyConstant.HEADER_DUEROS_DEVICE_ID, header.getDuerosDeviceId())
                .post(requestBody);

        Optional.ofNullable(header.getPid()).ifPresent(
                pid -> builder.header(DCSProxyConstant.HEADER_PRODUCT_ID, pid)
        );

        Optional.ofNullable(header.getUserAgent()).ifPresent(
                userAgent -> builder.header(DCSProxyConstant.HEADER_USER_AGENT, userAgent)
        );

        Optional.ofNullable(header.getStandbyDeviceId()).ifPresent(
                standby -> builder.header(DCSProxyConstant.HEADER_STANDBY_DEVICE_ID, standby)
        );

        builder.header(
                DCSProxyConstant.HEADER_STREAMING_VERSION,
                Optional.ofNullable(header.getStreamingVersion()).orElse("1")
        );
        return builder.build();
    }

    private String getFullPath(String[] path, BaseMessage message) {
        String domainAddress = getDomainAddress(message);
        if (!StringUtils.startsWithIgnoreCase(domainAddress, dcsProxyScheme)) {
            domainAddress = dcsProxyScheme + domainAddress;
        }
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(domainAddress),
                getFullRelativePath(path)
        );
    }

    /**
     * Resolve dcs address according to specified {@code message}
     * @param message a message contain connection info
     * @return {@code ip:port}
     */
    private String getDomainAddress(BaseMessage message) {
        String domainAddress;
        InetSocketAddress hashedAddress;
        if (StringUtils.hasText(dcsProxyStateAddress)) {
            String[] items = dcsProxyStateAddress.split(Pattern.quote(SPLITTER_COLON));
            try {
                hashedAddress = new InetSocketAddress(InetAddress.getByName(items[0]), Integer.valueOf(items[1]));
            } catch (UnknownHostException e) {
                hashedAddress = BnsCache.getHashedDcsHttpAddress(message);
            }
        } else {
            hashedAddress = BnsCache.getHashedDcsHttpAddress(message);
        }
        Preconditions.checkArgument(hashedAddress != null, "Couldn't find any dcs address");
        domainAddress = PathUtil.dropOffPrefix(hashedAddress.toString(), SPLITTER_URL);
        return domainAddress;
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(DCS_PROXY_ROOT),
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

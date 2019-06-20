package com.baidu.iot.devicecloud.devicemanager.client.http.dhclient;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CLT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_LOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_SN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/30.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DhClient extends AbstractHttpClient {
    private static final String DH_ROOT = "";
    private static final String[] DH_PUSH_PATH = {"dh2_push"};

    @Value("${dh.scheme:http://}")
    private String dhScheme;

    @Retryable(value = {SocketTimeoutException.class}, backoff = @Backoff(200))
    public Response pushMessage(DataPointMessage message) throws IOException {
        Request request = buildRequest(message);
        log.debug("Pushing {} to dh", JsonUtil.serialize(message));
        log.debug("Request dh {}", request);
        return sendSync(request);
    }

    public CompletableFuture<Response> pushMessageAsync(DataPointMessage message) {
        Request request = buildRequest(message);
        log.debug("Pushing {} to dh", JsonUtil.serialize(message));
        log.debug("Request dh {}", request);
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    private Request buildRequest(DataPointMessage message) {
        RequestBody requestBody = buildRequestBody(message);
        if (requestBody == null) {
            return null;
        }

        Request.Builder builder = new Request.Builder()
                .url(getFullPath(DH_PUSH_PATH, message))
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .header(CommonConstant.HEADER_MESSAGE_TYPE, Integer.toString(MessageType.PUSH_MESSAGE))
                .header(CommonConstant.HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                .post(requestBody);

        Optional.ofNullable(message.getCltId()).ifPresent(
                cltId -> builder.header(HEADER_CLT_ID, cltId)
        );
        Optional.ofNullable(message.getSn()).ifPresent(
                sn -> builder.header(HEADER_SN, sn)
        );
        Optional.ofNullable(message.getLogId()).ifPresent(
                logId -> builder.header(HEADER_LOG_ID, logId)
        );

        return builder.build();
    }

    private String getFullPath(String[] path, BaseMessage message) {
        String domainAddress = getDomainAddress(message);
        if (!StringUtils.startsWithIgnoreCase(domainAddress, dhScheme)) {
            domainAddress = dhScheme + domainAddress;
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
    @NotNull
    private String getDomainAddress(BaseMessage message) {
        String domainAddress = null;
        InetSocketAddress hashedAddress = BnsCache.getHashedDhHttpAddress(message);
        if (hashedAddress != null) {
            domainAddress = PathUtil.dropOffPrefix(hashedAddress.toString(), SPLITTER_URL);
        }
        Preconditions.checkArgument(StringUtils.hasText(domainAddress), "Couldn't find any dh address");
        return domainAddress;
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(DH_ROOT),
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

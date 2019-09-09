package com.baidu.iot.devicecloud.devicemanager.client.http.cesclient;

import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/9/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class CesClient extends AbstractHttpClient {
    private static final String[] CES_LOG_PATH = {"click"};

    @Value("${ces.scheme:http://}")
    private String cesScheme;

    @Value("${log.ces.url:}")
    private String cesUrl;

    public CompletableFuture<Response> sendCesLogAsync(JsonNode cesLog) {
        Request request = buildRequest(cesLog);
        Assert.notNull(request, "The CES Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    private Request buildRequest(JsonNode cesLog) {
        RequestBody requestBody = buildRequestBody(cesLog);

        if (requestBody == null) {
            return null;
        }

        String url = getFullPath();
        if (StringUtils.isEmpty(url)) {
            return null;
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .post(requestBody);

        return builder.build();
    }

    private String getFullPath() {
        String domainAddress = getDomainAddress();
        if (StringUtils.startsWithIgnoreCase(domainAddress, cesScheme) ||
                StringUtils.startsWithIgnoreCase(domainAddress, "bns://")) {
            return domainAddress;
        }
        domainAddress = cesScheme + domainAddress;
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(domainAddress),
                StringUtils.arrayToDelimitedString(CesClient.CES_LOG_PATH, SPLITTER_URL)
        );
    }

    /**
     * Resolve dcs address according to specified {@code message}
     * @return {@code ip:port}
     */
    @NotNull
    private String getDomainAddress() {
        String domainAddress = null;
        InetSocketAddress hashedAddress = BnsCache.getRandomCesAddress();
        if (hashedAddress != null) {
            domainAddress = PathUtil.dropOffPrefix(hashedAddress.toString(), SPLITTER_URL);
        } else if (StringUtils.hasText(cesUrl)) {
            domainAddress = cesUrl;
        }
        Preconditions.checkArgument(StringUtils.hasText(domainAddress), "Couldn't find any di address");
        return domainAddress;
    }
}

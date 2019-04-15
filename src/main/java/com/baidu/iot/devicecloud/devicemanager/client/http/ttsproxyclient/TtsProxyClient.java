package com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CUID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_SN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_PRE_TTS;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_TTS;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/30.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class TtsProxyClient extends AbstractHttpClient {
    private static final String TTS_PROXY_ROOT = "api/v1";
    private static final String[] TTS_PROXY_PATH = {"tts"};

    @Value("${tts.proxy.scheme:http://}")
    private String ttsProxyScheme;

    public CompletableFuture<Response> requestTtsAsync(TtsRequest message, boolean isPre) {
        Request request = buildRequest(message, isPre);
        Assert.notNull(request, "TTS Proxy Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public Response requestTtsSync(TtsRequest message, boolean isPre) {
        Request request = buildRequest(message, isPre);
        Assert.notNull(request, "TTS Proxy Request is null");
        return sendSync(request);
    }

    private Request buildRequest(TtsRequest message, boolean isPre) {
        TlvMessage tlv = message.getMessage();
        BinaryNode valueBin = tlv.getValue();
        JsonNode valueNode = JsonUtil.readTree(valueBin.binaryValue());
        Object ttsArray = valueNode.path(isPre ? JSON_KEY_PRE_TTS : JSON_KEY_TTS);
        Preconditions.checkArgument(
                ttsArray instanceof ArrayNode && ((ArrayNode) ttsArray).size() > 0,
                String.format("No tts info found: %s", String.valueOf(valueNode))
        );
        RequestBody requestBody = buildRequestBody(ttsArray);

        if (requestBody == null) {
            return null;
        }

        Request.Builder builder = new Request.Builder()
                .url(getFullPath(TTS_PROXY_PATH))
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .header(CommonConstant.HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                .header(CommonConstant.HEADER_PRE_TTS, Boolean.toString(isPre))
                .post(requestBody);

        Optional.ofNullable(message.getCuid()).ifPresent(
                cuid -> builder.header(HEADER_CUID, cuid)
        );

        Optional.ofNullable(message.getSn()).ifPresent(
                sn -> builder.header(HEADER_SN, sn)
        );

        return builder.build();
    }

    public String getTTSProxyURL() {
        return getFullPath(TTS_PROXY_PATH);
    }

    private String getFullPath(String[] path) {
        String domainAddress = getDomainAddress();
        if (!StringUtils.startsWithIgnoreCase(domainAddress, ttsProxyScheme)) {
            domainAddress = ttsProxyScheme + domainAddress;
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
        InetSocketAddress hashedAddress = BnsCache.getRandomTtsProxyAddress();
        if (hashedAddress != null) {
            domainAddress = PathUtil.dropOffPrefix(hashedAddress.toString(), SPLITTER_URL);
        }
        Preconditions.checkArgument(StringUtils.hasText(domainAddress), "Couldn't find any tts proxy address");
        return domainAddress;
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(TTS_PROXY_ROOT),
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

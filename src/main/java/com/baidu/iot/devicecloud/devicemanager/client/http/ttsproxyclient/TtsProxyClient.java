package com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CLT_ID;
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
    private static final String[] TTS_PROXY_CACHE_PATH = {"cache"};
    private static final String[] TTS_PROXY_TEXT_TO_VOICE_PATH = {"text2voice"};

    @Value("${tts.proxy.scheme:http://}")
    private String ttsProxyScheme;

    public CompletableFuture<Response> requestTtsAsync(TtsRequest message, boolean isPre, Map<String, String> keysMap) {
        Request request = buildJsonTtsRequest(message, isPre, keysMap);
        Assert.notNull(request, "TTS Proxy Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public CompletableFuture<Response> cacheAudioAsync(TtsRequest message, String contentId, String key) {
        Request request = buildStreamRequest(message, contentId, key);
        Assert.notNull(request, "TTS Proxy Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public Response requestTtsSync(TtsRequest message, boolean isPre, Map<String, String> keysMap) throws IOException {
        Request request = buildJsonTtsRequest(message, isPre, keysMap);
        Assert.notNull(request, "TTS Proxy Request is null");
        return sendSync(request);
    }

    public Response requestAudioUrl(String cuid, String sn, String cid, int messageType) throws IOException {
        Request request = buildRequest(cuid, sn, cid, messageType);
        Assert.notNull(request, "TTS Proxy Request is null");
        return sendSync(request);
    }

    public Response requestText2VoiceAsync(String content, DataPointMessage message) throws IOException {
        Request request = buildText2VoiceRequest(content, message);
        Assert.notNull(request, "Text to Voice Request is null");
        return sendSync(request);
    }

    private Request buildJsonTtsRequest(TtsRequest message, boolean isPre, Map<String, String> keysMap) {
        BinaryNode valueBin = message.getData();
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
                .url(getFullPath(message.getCuid(), null, TTS_PROXY_PATH))
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

        if (keysMap != null && !keysMap.isEmpty()) {
            keysMap.entrySet()
                    .parallelStream()
                    .forEach(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                            builder.header(key, value);
                        }
                    });
        }

        return builder.build();
    }

    private Request buildStreamRequest(TtsRequest message, String contentId, String key) {
        BinaryNode valueBin = message.getData();
        RequestBody requestBody = RequestBody.create(
                MediaType.get(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE),
                valueBin.binaryValue()
        );

        String[] pathItems = Stream.concat(Stream.of(TTS_PROXY_CACHE_PATH), Stream.of(contentId)).toArray(String[]::new);
        String url = getFullPath(message.getCuid(), null, pathItems);
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);

        // agreements with the tts-proxy service
        if (StringUtils.hasText(key)) {
            uriComponentsBuilder.queryParam("key", key);
        }
        if (MessageType.PUSH_MESSAGE == message.getMessageType()) {
            uriComponentsBuilder.queryParam("storageStrategy", "buffer");
        }

        url = uriComponentsBuilder.build().toString();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(CommonConstant.HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                .post(requestBody);
        Optional.ofNullable(message.getCuid()).ifPresent(
                cuid -> builder.header(HEADER_CUID, cuid)
        );

        Optional.ofNullable(message.getSn()).ifPresent(
                sn -> builder.header(HEADER_SN, sn)
        );

        return builder.build();
    }

    private Request buildRequest(String cuid, String sn, String cid, int messageType) {
        String[] pathItems = Stream.concat(Stream.of(TTS_PROXY_PATH), Stream.of(cid)).toArray(String[]::new);
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(getFullPath(cuid, null, pathItems));

        // agreements with the tts-proxy service
        if (MessageType.PUSH_MESSAGE == messageType) {
            uriComponentsBuilder.queryParam("storageStrategy", "buffer");
        }

        Request.Builder builder = new Request.Builder()
                .url(uriComponentsBuilder.build().toString())
                .header(CommonConstant.HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                .get();
        if (StringUtils.hasText(cuid)) {
            builder.header(HEADER_CUID, cuid);
        }
        if (StringUtils.hasText(sn)) {
            builder.header(HEADER_SN, sn);
        }
        return builder.build();
    }

    private Request buildText2VoiceRequest(String content, DataPointMessage message) {
        String cuid = message.getDeviceId();
        String cltId = message.getCltId();
        String sn = message.getSn();
        String[] pathItems = Stream.concat(Stream.of(TTS_PROXY_TEXT_TO_VOICE_PATH), Stream.of(cuid)).toArray(String[]::new);
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(getFullPath(cuid, null, pathItems));

        ObjectNode payload = JsonUtil.createObjectNode();
        payload.set("txt", TextNode.valueOf(content));
        RequestBody requestBody = RequestBody.create(
                MediaType.get(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_UTF8_VALUE),
                payload.toString()
        );

        Request.Builder builder = new Request.Builder()
                .url(uriComponentsBuilder.build().toString())
                .header(CommonConstant.HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                .post(requestBody);

        if (StringUtils.hasText(cuid)) {
            builder.header(HEADER_CUID, cuid);
        }

        if (StringUtils.hasText(cltId)) {
            builder.header(HEADER_CLT_ID, cltId);
        }

        if (StringUtils.hasText(sn)) {
            builder.header(HEADER_SN, sn);
        }

        return builder.build();
    }

    private String getFullPath(String cuid, String cltId, String[] path) {
        String domainAddress = getDomainAddress(cuid, cltId);
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
    private String getDomainAddress(String cuid, String cltId) {
        String domainAddress = null;
        InetSocketAddress hashedAddress =
                StringUtils.isEmpty(cuid) && StringUtils.isEmpty(cltId) ?
                        BnsCache.getRandomTtsProxyAddress() : BnsCache.getHashedTtsProxyAddress(cuid, cltId);
        if (hashedAddress != null) {
            domainAddress = PathUtil.dropOffPrefix(hashedAddress.toString(), SPLITTER_URL);
        }
        Preconditions.checkArgument(StringUtils.hasText(domainAddress), "Couldn't find any tts proxy address");
        if (!StringUtils.startsWithIgnoreCase(domainAddress, ttsProxyScheme)) {
            domainAddress = ttsProxyScheme + domainAddress;
        }
        return domainAddress;
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(TTS_PROXY_ROOT),
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

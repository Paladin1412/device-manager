package com.baidu.iot.devicecloud.devicemanager.processor;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.Part;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CONTENT_TYPE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_AUDIO;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CONTENT_DISPOSITION;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CONTENT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_METADATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_EQUALITY_SIGN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SEMICOLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.COMMAND_SPEAK;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_CONTENT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_TTS;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.PRIVATE_PROTOCOL_DIALOGUE_FINISHED;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.assembleDuerPrivateDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.getBoundary;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.isDirectiveTlv;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.isPreTTSTlv;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.isTTSTlv;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/4/1.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DirectiveProcessor implements InitializingBean {
    private static final int ONE_MINUTE_SECONDS = 60;
    private static final String SPEAK_URL_MAPPING_KEY_PATTERN = "%s_%s_%s";
    private final TtsService ttsService;

    // cuid_sn_cid:url
    // url pattern: http://domain/path/to/{key}.mp3
    private static Cache<String, String> speakUrls;

    @Autowired
    public DirectiveProcessor(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    public Flux<JsonNode> process(String cuid, String sn, TlvMessage message) {
        if (message != null) {
            BinaryNode valueBin = message.getValue();
            byte[] bytes = valueBin.binaryValue();
            TtsRequest request = assembleTtsRequest(cuid, sn, bytes);
            if (request != null) {
                if (isPreTTSTlv.test(message)) {
                    // all directive packages(0xF006)
                    ttsService.requestTTSSync(request, true, null);
                } else if (isTTSTlv.test(message)) {
                    // all directive packages(0xF004)
                    // final tts 可能有多条，且多于两条时是multipart形式的metadata和audio
                    JsonNode valueNode = JsonUtil.readTree(bytes);
                    if (valueNode.isNull()) {
                        // regard as multipart stream
                        return processMultipart(cuid, sn, bytes)
                                .flatMap(jsonNode -> {
                                    visitMetadata(cuid, sn, jsonNode);
                                    return Flux.just(jsonNode);
                                });
                    }
                    JsonNode tts = valueNode.path(JSON_KEY_TTS);
                    if (tts instanceof ArrayNode) {
                        ArrayNode ttsArray = (ArrayNode) tts;
                        if (ttsArray.size() > 0) {
                            Map<String, String> keysMap = new HashMap<>();
                            ttsArray.forEach(jsonNode -> {
                                String contentId = jsonNode.path(JSON_KEY_CONTENT_ID).asText();
                                String contentKey = getBaseName(assembleKey(cuid, sn, contentId));
                                if (StringUtils.hasText(contentId) && StringUtils.hasText(contentKey)) {
                                    keysMap.put(contentId, contentKey);
                                }
                            });
                            Map<String, String> cid_url_mappings = ttsService.requestTTSSync(request, false, keysMap);
                            if (cid_url_mappings != null) {
                                cid_url_mappings.entrySet()
                                        .parallelStream()
                                        .forEach(entry -> {
                                            String key = assembleKey(cuid, sn, entry.getKey());
                                            speakUrls.put(key, entry.getValue());
                                        });
                            }
                        }
                    }
                } else if (isDirectiveTlv.test(message)) {
                    return processMultipart(cuid, sn, bytes)
                            .flatMap(jsonNode -> {
                                visitMetadata(cuid, sn, jsonNode);
                                return Flux.just(jsonNode);
                            });
                }
            }
        }
        return Flux.empty();
    }

    private Flux<JsonNode> processMultipart(String cuid, String sn, byte[] bytes) {
        // all directive packages(0xF003)
        if (bytes != null && bytes.length > 0) {
            ByteBuf bb = Unpooled.copiedBuffer(bytes);
            MultipartFormDataProcessor multipartFormDataProcessor = new MultipartFormDataProcessor(bb);
            HttpResponse response = multipartFormDataProcessor.response();
            if (response.status() == HttpResponseStatus.OK && response.headers().contains(HEADER_CONTENT_TYPE)) {
                String boundary = getBoundary(response.headers().get(HEADER_CONTENT_TYPE));
                MultipartStream multipartStream =
                        new MultipartStream(
                                new ByteBufInputStream(multipartFormDataProcessor.slice()),
                                boundary.getBytes(),
                                bb.readableBytes(),
                                null
                        );
                return processMultipartStream(cuid, sn, multipartStream);
            }
        }

        return Flux.empty();
    }

    public Flux<JsonNode> processMultiparts(String cuid, String sn, Flux<Part> parts) {
        AtomicInteger index = new AtomicInteger(0);
        Map<String, String> ids = new HashMap<>();
        return parts
                .flatMapSequential(part -> {
                    String name = part.name();
                    HttpHeaders headers = part.headers();
                    if (!PARAMETER_METADATA.equalsIgnoreCase(name)) {
                        if (PARAMETER_AUDIO.equalsIgnoreCase(name)) {
                            String contentId = headers.getFirst(PARAMETER_CONTENT_ID);
                            if (StringUtils.hasText(contentId)) {
                                return part.content()
                                        .publishOn(Schedulers.single())
                                        .doOnNext(dataBuffer -> uploadAudio(cuid, sn, contentId, dataBuffer.asByteBuffer().array()))
                                        .thenMany(Flux.empty());
                            }
                        }
                    } else {
                        return DataBufferUtils.join(part.content())
                                .flatMap(dataBuffer -> {
                                    JsonNode directive = JsonUtil.readTree(dataBuffer.asByteBuffer().array());
                                    if (null != directive && !directive.isNull()) {
                                        log.debug("Publishing directive: {}", directive);

                                        String dialogueRequestId = getDialogueRequestId.apply(directive);
                                        if (StringUtils.hasText(dialogueRequestId)) {
                                            ids.put("dialogueFinishedRequestId", dialogueRequestId);
                                        }
                                        String messageId = getMessageId.apply(directive);
                                        if (StringUtils.hasText(messageId)) {
                                            ids.put("dialogueFinishedMessageId", messageId);
                                        }
                                        return Mono.just(addExtraInfo(directive, index.incrementAndGet()));
                                    }
                                    return Mono.empty();
                                });
                    }
                    return Flux.empty();
                })
                .flatMap(directive -> {
                    visitMetadata(cuid, sn, directive);
                    return Flux.just(directive);
                })
                .concatWith(Mono.just(assembleDuerPrivateDirective(
                        PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                        ids.get("dialogueFinishedRequestId"),
                        ids.get("dialogueFinishedMessageId"),
                        index.incrementAndGet()
                )));
    }

    private TtsRequest assembleTtsRequest(String cuid, String sn, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        TtsRequest ttsRequest = new TtsRequest();
        ttsRequest.setCuid(cuid);
        ttsRequest.setSn(sn);
        ttsRequest.setData(new BinaryNode(bytes));
        return ttsRequest;
    }

    private void visitMetadata(String cuid, String sn, JsonNode metadata) {
        if (isSpeak.test(metadata) && needRewrite.test(metadata)) {
            String cid = getContentId.apply(metadata);
            String key = assembleKey(cuid, sn, cid);
            String audioUrl;
            try {
                audioUrl = speakUrls.get(key, () -> {
                    log.debug("Obtaining speak url from tts proxy for key:{}", key);
                    Map<String, String> mappings = ttsService.requestAudioUrl(cuid, sn, cid);
                    if (mappings != null) {
                        return mappings.getOrDefault(cid, "");
                    }
                    return "";
                });
                rewrite.accept(metadata, audioUrl);
            } catch (ExecutionException e) {
                log.error("Obtaining the audio url failed", e);
            }
        }
    }

    /**
     * <p>
     * Multipart stream内容（parts）为通过Content-ID唯一对应的metadata和audio，会出现在以下情况中：
     * <ul>
     *     <li>
     *          EVENT和ASR通路的下行数据0xF003和0xF004，其中当0xF004（final tts）多于2条的时候将以multipart形式下发
     *      </li>
     *      <li>
     *          推送的内容为multipart stream
     *      </li>
     * </ul>
     * </p>
     * @param multipartStream the encapsulated {@link MultipartStream}
     * @return {@link Flux}&lt;{@link JsonNode}&gt;
     */
    private Flux<JsonNode> processMultipartStream(String cuid, String sn, MultipartStream multipartStream) {
        return Flux.push(fluxSink -> {
            String dialogueFinishedMessageId = null;
            String dialogueFinishedRequestId = null;
            int index = 0;
            try {
                boolean hasNextPart = multipartStream.skipPreamble();
                while (hasNextPart) {
                    Map<String, String> headers = getPartHeaders(multipartStream);
                    if (headers != null && headers.containsKey(PARAMETER_CONTENT_DISPOSITION)) {
                        String name = getPartName(headers.get(PARAMETER_CONTENT_DISPOSITION));
                        byte[] partBytes = getPartBytes(multipartStream);
                        if (!PARAMETER_METADATA.equalsIgnoreCase(name)) {
                            if (PARAMETER_AUDIO.equalsIgnoreCase(name)) {
                                String contentId = getMultipartHeaderValue(headers, PARAMETER_CONTENT_ID);
                                if (StringUtils.hasText(contentId)) {
                                    uploadAudio(cuid, sn, contentId, partBytes);
                                }
                            }
                            hasNextPart = multipartStream.readBoundary();
                            continue;
                        }
                        if (isPartJSON(headers)) {
                            JsonNode directive = JsonUtil.readTree(partBytes);
                            if (null != directive && !directive.isNull()) {
                                log.debug("Publishing directive: {}", directive);
                                fluxSink.next(addExtraInfo(directive, ++index));

                                String messageId = getMessageId.apply(directive);
                                String dialogueRequestId = getDialogueRequestId.apply(directive);
                                if (StringUtils.hasText(messageId)) {
                                    dialogueFinishedMessageId = messageId;
                                }
                                if (StringUtils.hasText(dialogueRequestId)) {
                                    dialogueFinishedRequestId = dialogueRequestId;
                                }
                            }
                        }
                    }

                    hasNextPart = multipartStream.readBoundary();
                }
                // append dialogue finished directive
                log.debug("Publishing directive dialogueFinished");
                fluxSink.next(assembleDuerPrivateDirective(
                        PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                        dialogueFinishedRequestId,
                        dialogueFinishedMessageId,
                        ++index
                ));

                fluxSink.complete();
            } catch (IOException e) {
                log.error("Getting metadata of directives from the multiple stream failed", e);
                fluxSink.error(e);
            }
        });
    }

    private void uploadAudio(String cuid, String sn, String contentId, byte[] partBytes) {
        TtsRequest ttsRequest = assembleTtsRequest(cuid, sn, partBytes);
        if (ttsRequest != null) {
            CompletableFuture<Response> future =
                    ttsService.cacheAudioAsync(ttsRequest, contentId, getBaseName(assembleKey(cuid, sn, contentId)));
            future.handleAsync(
                    (r, t) -> {
                        if (r != null && r.isSuccessful()) {
                            ResponseBody body = r.body();
                            if (body != null) {
                                Map<String, String> urlmap = new HashMap<>();
                                try {
                                    ttsService.decodeTtsProxyResponse(body.string(), urlmap);
                                    urlmap.entrySet()
                                            .parallelStream()
                                            .forEach(entry -> {
                                                String key = assembleKey(cuid, sn, entry.getKey());
                                                String url = entry.getValue();
                                                if (StringUtils.hasText(url) && !url.equals(speakUrls.getIfPresent(key))) {
                                                    speakUrls.put(key, entry.getValue());
                                                }
                                            });
                                } catch (IOException e) {
                                    log.error("Requesting tts in async way failed", e);
                                } finally {
                                    close(r);
                                }
                            }
                        }
                        return null;
                    }
            );
        }
    }

    private JsonNode addExtraInfo(JsonNode directive, int index) {
        ObjectNode data = (ObjectNode) directive;
        ObjectNode extraData = JsonUtil.createObjectNode();
        extraData.set("timestamp", TextNode.valueOf(Long.toString(System.currentTimeMillis())));
        extraData.set("index", IntNode.valueOf(index));
        data.set("iot_cloud_extra", extraData);
        return data;
    }

    private Map<String, String> getPartHeaders(MultipartStream multipartStream) throws IOException {
        String headers = multipartStream.readHeaders();
        BufferedReader reader = new BufferedReader(new StringReader(headers));
        Map<String, String> headerMap = new HashMap<>();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            line = line.trim();
            if (StringUtils.hasText(line) && line.contains(SPLITTER_COLON)) {
                int colon = line.indexOf(SPLITTER_COLON);
                String headerName = line.substring(0, colon).trim();
                String headerValue = line.substring(colon + 1).trim();
                headerMap.put(headerName.toLowerCase(), headerValue);
            }
        }

        return headerMap;
    }

    private byte[] getPartBytes(MultipartStream multipartStream) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        multipartStream.readBodyData(data);

        return data.toByteArray();
    }

    private boolean isPartJSON(Map<String, String> headers) {
        String contentType = getMultipartHeaderValue(headers, HttpHeaders.CONTENT_TYPE);
        return contentType.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String getMultipartHeaderValue(Map<String, String> headers, String searchHeader) {
        return headers.get(searchHeader.toLowerCase());
    }

    private String getPartName(String contentDisposition) {
        if (StringUtils.hasText(contentDisposition)) {
            String[] items = contentDisposition.split(Pattern.quote(SPLITTER_SEMICOLON));
            if (items.length > 1) {
                String[] nameKV = items[1].split(Pattern.quote(SPLITTER_EQUALITY_SIGN));
                if (nameKV.length > 1 && PARAMETER_NAME.equalsIgnoreCase(StringUtils.trimAllWhitespace(nameKV[0])) && StringUtils.hasText(nameKV[1])) {
                    return StringUtils.trimTrailingCharacter(StringUtils.trimLeadingCharacter(nameKV[1], '"'), '"');
                }
            }
        }
        return "";
    }

    @Nullable
    private String getBaseName(String key) {
        String urlString = speakUrls.getIfPresent(key);
        if (StringUtils.hasText(urlString)) {
            try {
                URL url = new URL(urlString);
                return FilenameUtils.getBaseName(url.getPath());
            } catch (MalformedURLException e) {
                log.error("Obtaining the base name from {} failed", urlString, e);
            }
        }
        return null;
    }

    private String assembleKey(String cuid, String sn, String cid) {
        return String.format(SPEAK_URL_MAPPING_KEY_PATTERN, cuid, sn, cid);
    }

    private Predicate<JsonNode> isSpeak =
            node ->
                    COMMAND_SPEAK.equalsIgnoreCase(node
                            .path(DIRECTIVE_KEY_DIRECTIVE)
                            .path(DIRECTIVE_KEY_HEADER)
                            .path(DIRECTIVE_KEY_HEADER_NAME).asText());

    private Predicate<JsonNode> needRewrite =
            node -> StringUtils.startsWithIgnoreCase(
                    node
                            .path(DIRECTIVE_KEY_DIRECTIVE)
                            .path(DIRECTIVE_KEY_PAYLOAD)
                            .path(DIRECTIVE_KEY_PAYLOAD_URL).asText(),
                    PARAMETER_CID
            );

    private Function<JsonNode, String> getContentId =
            node -> {
                String url = node
                        .path(DIRECTIVE_KEY_DIRECTIVE)
                        .path(DIRECTIVE_KEY_PAYLOAD)
                        .path(DIRECTIVE_KEY_PAYLOAD_URL)
                        .asText();
                if (StringUtils.hasText(url)) {
                    String[] items = StringUtils.split(url, SPLITTER_COLON);
                    if (items!= null && items.length > 1) {
                        return items[1];
                    }
                }
                return null;
            };

    private Function<JsonNode, String> getMessageId =
            node -> node
                    .path(DIRECTIVE_KEY_DIRECTIVE)
                    .path(DIRECTIVE_KEY_HEADER)
                    .path(DIRECTIVE_KEY_HEADER_MESSAGE_ID)
                    .asText();

    private Function<JsonNode, String> getDialogueRequestId =
            node -> node
                    .path(DIRECTIVE_KEY_DIRECTIVE)
                    .path(DIRECTIVE_KEY_HEADER)
                    .path(DIRECTIVE_KEY_HEADER_DIALOG_ID)
                    .asText();

    private BiConsumer<JsonNode, String> rewrite =
            (node, url) -> {
                if (StringUtils.isEmpty(url)) {
                    return;
                }
                JsonNode payloadNode = node.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_PAYLOAD);
                if (payloadNode != null && payloadNode.isObject()) {
                    ObjectNode payload = (ObjectNode) payloadNode;
                    payload.set(DIRECTIVE_KEY_PAYLOAD_URL, TextNode.valueOf(url));
                }
            };

    @Override
    public void afterPropertiesSet() {
        speakUrls = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(ONE_MINUTE_SECONDS, TimeUnit.SECONDS)
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .removalListener(n -> log.debug("Removed ({}, {}), caused by: {}", n.getKey(), n.getValue(), n.getCause().toString()))
                .build();
    }
}

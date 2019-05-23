package com.baidu.iot.devicecloud.devicemanager.processor;

import com.baidu.iot.devicecloud.devicemanager.adapter.Adapter;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.constant.MessageType;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.Part;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_AUDIO;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CONTENT_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_METADATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
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
import static com.baidu.iot.devicecloud.devicemanager.util.BufferUtil.joinBuffers;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.addExtraInfo;
import static com.baidu.iot.devicecloud.devicemanager.util.DirectiveUtil.assembleDuerPrivateDirective;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isPreTTSTlv;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/4/1.
 *
 * @apiNote 1 instance can only used to process 1 multipart stream
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class DirectiveProcessor {
    private static final int ONE_MINUTE_SECONDS = 60;
    private static final String SPEAK_URL_MAPPING_KEY_PATTERN = "%s_%s_%s";
    private static final String REQUEST_ID = "dialogueFinishedRequestId";
    private static final String MESSAGE_ID = "dialogueFinishedMessageId";
    private final TtsService ttsService;

    // cuid_sn_cid:url
    // url pattern: http://domain/path/to/{key}.mp3
    private Cache<String, String> speakUrls;
    private MultipartStreamDecoder candidate;
    private Supplier<MultipartStreamDecoder> decoderSupplier;
    private boolean hasFirstFinalTTSCame = false;
    private boolean isCandidateInitialized = false;
    private boolean isMultipart = false;

    public DirectiveProcessor(TtsService ttsService) {
        this.ttsService = ttsService;
        this.decoderSupplier = MultipartStreamDecoder::new;

        this.speakUrls = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(ONE_MINUTE_SECONDS, TimeUnit.SECONDS)
                .expireAfterAccess(Duration.ofSeconds(ONE_MINUTE_SECONDS / 2))
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .removalListener(LogUtils.REMOVAL_LOGGER.apply(log))
                .build();
    }

    public Flux<TlvMessage> processAsr(String cuid, String sn, Flux<TlvMessage> messages) {
        if (messages != null) {
            return messages.groupBy(TlvMessage::getType)
                    .flatMap(group -> {
                        Integer groupKey = group.key();
                        if (groupKey != null) {
                            switch (groupKey) {
                                case TlvConstant.TYPE_DOWNSTREAM_ASR: {
                                    return group.flatMap(Mono::just);
                                }
                                case TlvConstant.TYPE_DOWNSTREAM_PRE_TTS: {
                                    // all directive packages(0xF006)
                                }
                                case TlvConstant.TYPE_DOWNSTREAM_TTS: {
                                    return group.flatMap(tlv -> {
                                        BinaryNode valueBin = tlv.getValue();
                                        if (valueBin != null && !valueBin.isNull()) {
                                            //noinspection BlockingMethodInNonBlockingContext
                                            byte[] bytes = valueBin.binaryValue();
                                            if (bytes.length > 0) {
                                                TtsRequest request = assembleTtsRequest(cuid, sn, bytes, MessageType.BASE);
                                                if (isPreTTSTlv.test(tlv)) {
                                                    // all directive packages(0xF006)
                                                    ttsService.requestTTSAsync(request, true, null);

                                                } else {
                                                    // all directive packages(0xF004)
                                                    // final tts 可能有多条，且多于两条时是multipart形式的metadata和audio
                                                    if (!hasFirstFinalTTSCame) {
                                                        // according the first final tts package to decide whether final tts is in the form of json or multipart
                                                        isMultipart = checkFormat(bytes);
                                                        hasFirstFinalTTSCame = true;
                                                    }
                                                    if (isMultipart) {
                                                        if (!isCandidateInitialized) {
                                                            candidate = decoderSupplier.get();
                                                        }
                                                        // TODO composites te Flux<Part>
                                                        return processMultiparts(cuid, sn, candidate.decode(bytes), MessageType.BASE, true)
                                                                .flatMap(directive -> Mono.justOrEmpty(Adapter.directive2DataPointTLV(directive, groupKey)));
                                                    }

                                                    if (request != null) {
                                                        processTTS(request);
                                                    }
                                                }
                                            }
                                        }
                                        return Flux.empty();
                                    });
                                }
                                case TlvConstant.TYPE_DOWNSTREAM_DUMI: {
                                    MultipartStreamDecoder decoderDumi = decoderSupplier.get();
                                    return processMultiparts(cuid, sn, parseParts(decoderDumi, group), MessageType.BASE, true)
                                            .flatMap(directive -> {
                                                log.debug("Publishing asr directive:\n{}", directive);
                                                return Mono.justOrEmpty(Adapter.directive2DataPointTLV(directive, groupKey));
                                            });
                                }
                            }
                        }
                        return Flux.empty();
                    });
        }
        return Flux.empty();
    }

    public Flux<DataPointMessage> processEvent(String cuid, String sn, Flux<TlvMessage> messages, DataPointMessage origin) {
        if (messages != null) {
            return messages.groupBy(TlvMessage::getType)
                    .flatMap(group -> {
                        Integer groupKey = group.key();
                        if (groupKey != null) {
                            switch (groupKey) {
                                case TlvConstant.TYPE_DOWNSTREAM_TTS: {
                                    return group.flatMap(tlv -> {
                                        BinaryNode valueBin = tlv.getValue();
                                        if (valueBin != null && !valueBin.isNull()) {
                                            //noinspection BlockingMethodInNonBlockingContext
                                            byte[] bytes = valueBin.binaryValue();
                                            if (bytes.length > 0) {
                                                // all directive packages(0xF004)
                                                // final tts 可能有多条，且多于两条时是multipart形式的metadata和audio
                                                if (!hasFirstFinalTTSCame) {
                                                    // according the first final tts package to decide whether final tts is in the form of json or multipart
                                                    isMultipart = checkFormat(bytes);
                                                    hasFirstFinalTTSCame = true;
                                                }
                                                if (isMultipart) {
                                                    if (!isCandidateInitialized) {
                                                        candidate = decoderSupplier.get();
                                                    }
                                                    // TODO composites te Flux<Part>
                                                    return processMultiparts(cuid, sn, candidate.decode(tlv), MessageType.DATA_POINT, false)
                                                            .flatMap(directive -> Mono.justOrEmpty(Adapter.directive2DataPoint(directive, origin)));
                                                }
                                                TtsRequest request = assembleTtsRequest(cuid, sn, bytes, origin.getMessageType());
                                                if (request != null) {
                                                    processTTS(request);
                                                }
                                            }
                                        }
                                        return Flux.empty();
                                    });
                                }
                                case TlvConstant.TYPE_DOWNSTREAM_DUMI: {
                                    MultipartStreamDecoder decoderDumi = decoderSupplier.get();
                                    return processMultiparts(cuid, sn, parseParts(decoderDumi, group), origin.getMessageType(), false)
                                            .flatMap(directive -> {
                                                log.debug("Publishing event directive:\n{}", directive);
                                                return Mono.justOrEmpty(Adapter.directive2DataPoint(directive, origin));
                                            });
                                }
                            }
                        }
                        return Flux.empty();
                    });
        }
        return Flux.empty();
    }

    /**
     * @apiNote Process tts request in sync way
     * @param request {@link TtsRequest}
     */
    private void processTTS(TtsRequest request) {
        Map<String, String> keysMap = new HashMap<>();
        String cuid = request.getCuid();
        String sn = request.getSn();
        log.debug("Processing final TTS for cuid:{} sn:{}", cuid, sn);
        JsonNode jsonTree = JsonUtil.readTree(request.getData().binaryValue());
        JsonNode ttsJsonNode = jsonTree.path(JSON_KEY_TTS);
        if (ttsJsonNode instanceof ArrayNode) {
            ArrayNode ttsArray = (ArrayNode) ttsJsonNode;
            if (ttsArray.size() > 0) {
                ttsArray.forEach(jsonNode -> {
                    String contentId = jsonNode.path(JSON_KEY_CONTENT_ID).asText();
                    String contentKey = getBaseName(assembleKey(cuid, sn, contentId));
                    if (StringUtils.hasText(contentKey)) {
                        keysMap.put(contentId, contentKey);
                    }
                });
                Map<String, String> cid_url_mappings = ttsService.requestTTSSync(request, false, keysMap);
                if (cid_url_mappings != null) {
                    cid_url_mappings.entrySet()
                            .parallelStream()
                            .forEach(entry -> {
                                String key = assembleKey(cuid, sn, entry.getKey());
                                String value = entry.getValue();
                                if (StringUtils.hasText(value) && !value.equals(speakUrls.getIfPresent(key))) {
                                    speakUrls.put(key, value);
                                }
                            });
                }
            }
        }
    }

    private boolean checkFormat(byte[] bytes) {
        for (byte b : bytes) {
            short c = (short) (b & 0xFF);
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                return b != JsonToken.START_OBJECT.asByteArray()[0]
                        && b != JsonToken.START_ARRAY.asByteArray()[0];
            }
        }
        return false;
    }

    private Flux<Part> parseParts(MultipartStreamDecoder decoder, Flux<TlvMessage> messages) {
        return Flux.merge(Flux.push(partFluxSink -> messages
                .doOnNext(tlv -> partFluxSink.next(decoder.decode(tlv)))
                .doFinally(signalType -> partFluxSink.complete())
                .subscribe()));
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
     * @param cuid the device id
     * @param sn the device connection id
     * @param parts the encapsulated {@link MultipartStream}
     * @return {@link Flux}&lt;{@link JsonNode}&gt;
     */
    public Flux<JsonNode> processMultiparts(String cuid, String sn, Flux<Part> parts, int messageType, boolean needAppendDialogueFinished) {
        Map<String, String> ids = new HashMap<>();
        AtomicInteger index = new AtomicInteger(0);
        Flux<JsonNode> directives = parts
                .flatMapSequential(part -> {
                    String name = part.name();
                    HttpHeaders headers = part.headers();
                    if (!PARAMETER_METADATA.equalsIgnoreCase(name)) {
                        if (PARAMETER_AUDIO.equalsIgnoreCase(name)) {
                            String contentId = headers.getFirst(PARAMETER_CONTENT_ID);
                            if (StringUtils.hasText(contentId)) {
                                return joinBuffers(part.content())
                                        .publishOn(Schedulers.single())
                                        .doOnNext(dataBuffer -> {
                                            log.debug("Upload audio for message type: {}", messageType);
                                            uploadAudio(cuid, sn, contentId, dataBuffer.asByteBuffer().array(), messageType);
                                        })
                                        .thenMany(Flux.empty());
                            }
                        }
                    } else {
                        return joinBuffers(part.content())
                                .flatMap(dataBuffer -> {
                                    JsonNode directive = JsonUtil.readTree(dataBuffer.asByteBuffer().array());
                                    if (null != directive && !directive.isNull()) {
                                        String dialogueRequestId = getDialogueRequestId.apply(directive);
                                        if (StringUtils.hasText(dialogueRequestId)) {
                                            ids.put(REQUEST_ID, dialogueRequestId);
                                        }
                                        String messageId = getMessageId.apply(directive);
                                        if (StringUtils.hasText(messageId)) {
                                            ids.put(MESSAGE_ID, messageId);
                                        }
                                        return Mono.just(addExtraInfo(directive, index.incrementAndGet()));
                                    }
                                    return Mono.empty();
                                });
                    }
                    return Flux.empty();
                })
                .flatMap(directive -> {
                    visitMetadata(cuid, sn, directive, messageType);
                    return Flux.just(directive);
                });
        if (needAppendDialogueFinished) {
            return directives.concatWith(Mono.defer(() -> Mono.just(assembleDuerPrivateDirective(
                    PRIVATE_PROTOCOL_DIALOGUE_FINISHED,
                    ids.get(REQUEST_ID),
                    ids.get(MESSAGE_ID),
                    index.incrementAndGet()))));
        }
        return directives;
    }

    private TtsRequest assembleTtsRequest(String cuid, String sn, byte[] bytes, int messageType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        TtsRequest ttsRequest = new TtsRequest();
        ttsRequest.setCuid(cuid);
        ttsRequest.setSn(sn);
        ttsRequest.setData(new BinaryNode(bytes));
        ttsRequest.setMessageType(messageType);
        return ttsRequest;
    }

    private void visitMetadata(String cuid, String sn, JsonNode metadata, int messageType) {
        if (isSpeak.test(metadata) && needRewrite.test(metadata)) {
            String cid = getContentId.apply(metadata);
            String key = assembleKey(cuid, sn, cid);
            String audioUrl;
            try {
                audioUrl = speakUrls.get(key, () -> {
                    log.debug("Obtaining speak url from tts proxy for key:{}", key);
                    Map<String, String> mappings = ttsService.requestAudioUrl(cuid, sn, cid, messageType);
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

    private void uploadAudio(String cuid, String sn, String contentId, byte[] partBytes, int messageType) {
        TtsRequest ttsRequest = assembleTtsRequest(cuid, sn, partBytes, messageType);
        if (ttsRequest != null) {
            CompletableFuture<Response> future =
                    ttsService.cacheAudioAsync(ttsRequest, contentId, getBaseName(assembleKey(cuid, sn, contentId)));
            future.handleAsync(
                    (r, t) -> {
                        try(ResponseBody body = r.body()) {
                            if (r.isSuccessful() && body != null) {
                                Map<String, String> urlmap = new HashMap<>();
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
                            }
                        } catch (IOException e) {
                            log.error("Requesting tts in async way failed", e);
                        }
                        return null;
                    }
            );
        }
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
}

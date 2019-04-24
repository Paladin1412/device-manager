package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.dhclient.DhClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.dproxy.DproxyClientProvider;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.TtsProxyClient;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.EXTENSION_MP3;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_DIALOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_MESSAGE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD_URL;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.isCoapOk;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponsesWithMessage;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class PushService implements InitializingBean {
    private static final String TTS_BYTES_KEY_PATTERN = "iot:duer:ttsproxy:tts:bytes:%s";

    private final DhClient client;
    private final TtsProxyClient ttsProxyClient;
    private final SecurityService securityService;
    private Map<String, UnicastProcessor<DataPointMessage>> pooledMonoSignals;

    private DataBufferFactory dataBufferFactory;

    @Value("${expire.tts.bytes:1800}")
    private Integer ttsExpire;

    @Autowired
    public PushService(DhClient client, TtsProxyClient ttsProxyClient, SecurityService securityService) {
        this.client = client;
        this.ttsProxyClient = ttsProxyClient;
        this.securityService = securityService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        pooledMonoSignals = new ConcurrentHashMap<>();
        dataBufferFactory = new DefaultDataBufferFactory();
    }

    public String pool(BaseMessage message) {
        if (message.isNeedAck()) {
            String key = securityService.nextSecretKey(message.getDeviceId());
            UnicastProcessor<DataPointMessage> signal = UnicastProcessor.create(Queues.<DataPointMessage>xs().get());
            pool(key, signal);
            return key;
        }
        return null;
    }

    private void pool(String key, UnicastProcessor<DataPointMessage> signal) {
        if (StringUtils.hasText(key) && signal != null) {
            signal
                    .timeout(Duration.ofSeconds(5))
                    .doFinally(signalType -> pooledMonoSignals.remove(key));
            pooledMonoSignals.put(key, signal);
        }
    }

    public void advice(String key, DataPointMessage message) {
        UnicastProcessor<DataPointMessage> signal = pooledMonoSignals.get(key);
        if (signal != null && !signal.isTerminated()) {
            signal.onNext(message);
            signal.dispose();
            pooledMonoSignals.remove(key);
        }
    }

    private Mono<BaseResponse> push(DataPointMessage message) {
        return Mono.from(Mono.justOrEmpty(
                this.client.pushMessage(message))
                .flatMap(response -> {
                    if (response != null && response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            try {
                                byte[] bytes = body.bytes();
                                log.debug("DH response:\n{}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(bytes)));
                            } catch (IOException ignore) { }
                        }
                        return Mono.just(successResponsesWithMessage.apply(message));
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.just(failedResponses.apply(message.getLogId(), "Pushing dh failed")))
        );
    }

    public Mono<BaseResponse> push(List<DataPointMessage> messages) {
        if (messages == null || messages.size() < 1) {
            return Mono.empty();
        }
        final AtomicInteger failed = new AtomicInteger(0);
        final int size = messages.size();
        final String logId;
        DataPointMessage first = messages.get(0);
        if (first != null) {
            logId = first.getLogId();
        } else {
            logId = null;
        }

        return Mono.from(
                Flux.fromIterable(messages)
                    .doOnNext(dataPointMessages -> {
                        boolean isDialogueFinished = dataPointMessages.isDialogueFinished();
                        if (!isDialogueFinished) {
                            failed.incrementAndGet();
                        }
                        Mono.from(push(dataPointMessages))
                                .subscribe(baseResponse -> {
                                    if (baseResponse.getCode() == CommonConstant.MESSAGE_SUCCESS_CODE
                                            && !isDialogueFinished) {
                                        failed.decrementAndGet();
                                    }
                                });
                    })
                    .flatMap(list -> {
                        int fails = failed.get();
                        if (fails == 0) {
                            return Mono.just(successResponses.apply(logId));
                        }
                        String message = String.format("Should've sent %d messages, but %d failed", size, fails);
                        return Mono.just(failedResponses.apply(logId, message));
                    })
        );
    }

    public Mono<BaseResponse> check(BaseMessage message, String key, List<Integer> stub) {
        if (message == null) {
            return Mono.just(successResponses.apply(null));
        }
        if (stub == null || stub.size() < 1 || !message.isNeedAck()) {
            return Mono.just(successResponses.apply(message.getLogId()));
        }
        UnicastProcessor<DataPointMessage> signal = pooledMonoSignals.get(key);
        if (signal == null) {
            return Mono.just(failedResponses.apply(message.getLogId(), "No signal"));
        }

        return Mono.create(sink -> signal.subscribe(new BaseSubscriber<DataPointMessage>(){
            @Override
            protected void hookOnNext(DataPointMessage ack) {
                if (ack != null) {
                    int id = ack.getId();
                    if (isCoapOk.test(ack) && stub.contains(id)) {
                        stub.remove(id);
                    }
                    if (stub.isEmpty()) {
                        sink.success(successResponsesWithMessage.apply(message));
                    }
                }
            }

            @Override
            protected void hookOnComplete() {
                sink.success();
            }
        }));
    }

    public List<JsonNode> fixUrl(List<Part> metadata, List<Part> audios) {
        List<JsonNode> metadataJson = readJson(metadata);
        Map<String, Part> partMap = partMap(audios);
        return metadataJson.stream()
                .map(jsonNode -> {
                    JsonNode payloadJsonNode = jsonNode.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_PAYLOAD);
                    ObjectNode payloadNode = null;
                    if (payloadJsonNode != null) {
                        if (payloadJsonNode.isObject()) {
                            payloadNode = (ObjectNode)payloadJsonNode;
                        } else if (payloadJsonNode.isTextual()) {
                            payloadNode = (ObjectNode) JsonUtil.readTree(payloadJsonNode.asText());
                        }
                    }
                    if (payloadNode == null) {
                        return jsonNode;
                    }
                    ObjectNode finalPayloadNode = payloadNode;
                    String url = jsonNode
                            .path(DIRECTIVE_KEY_DIRECTIVE)
                            .path(DIRECTIVE_KEY_PAYLOAD)
                            .path(DIRECTIVE_KEY_PAYLOAD_URL)
                            .asText();
                    if (StringUtils.hasText(url) && StringUtils.startsWithIgnoreCase(url,"cid:")) {
                        String cid = url.split(Pattern.quote(SPLITTER_COLON))[1];
                        JsonNode header = jsonNode.path(DIRECTIVE_KEY_DIRECTIVE).path(DIRECTIVE_KEY_HEADER);
                        String dialogRequestId = header.path(DIRECTIVE_KEY_HEADER_DIALOG_ID).asText();
                        String messageId = header.path(DIRECTIVE_KEY_HEADER_MESSAGE_ID).asText();
                        String audioKey = DigestUtils.md5Hex(String.format("%s_%s_%s", dialogRequestId, messageId, cid));
                        Part desired = partMap.get(cid);
                        if (desired != null) {
                            desired.content()
                                    .reduce(new InputStream() {
                                        @Override
                                        public int read() throws IOException {
                                            return -1;
                                        }
                                    }, (InputStream t, DataBuffer d) -> new SequenceInputStream(t, d.asInputStream()))
                                    .subscribe(in -> {
                                        cacheAudio(audioKey, in);
                                        String ttsProxyUrl = ttsProxyClient.getTTSProxyURL();
                                        String finalUrl =
                                                StringUtils.applyRelativePath(
                                                        PathUtil.lookAfterSuffix(ttsProxyUrl),
                                                        String.format("%s%s", audioKey, EXTENSION_MP3)
                                                );
                                        finalPayloadNode.set(DIRECTIVE_KEY_PAYLOAD_URL, TextNode.valueOf(finalUrl));
                                    });
                        }
                    }
                    return jsonNode;
                })
                .collect(Collectors.toList());
    }

    public List<JsonNode> readJson(List<Part> parts) {
        List<JsonNode> result = new ArrayList<>();
        parts.forEach(part ->
                part.content()
                        .reduce(new InputStream() {
                            @Override
                            public int read() throws IOException {
                                return -1;
                            }
                        }, (InputStream t, DataBuffer d) -> new SequenceInputStream(t, d.asInputStream()))
                        .subscribe(in -> {
                            JsonNode read = JsonUtil.readTree(in);
                            if (!read.isNull()) {
                                log.debug("read: {}", read.toString());
                                result.add(read);
                            }
                        }));
        return result;
    }

    private Map<String, Part> partMap(List<Part> audios) {
        Map<String, Part> map = new HashMap<>();
        audios.forEach(part -> {
            HttpHeaders headers = part.headers();
            String contentId = headers.getFirst("Content-ID");
            if(StringUtils.hasText(contentId)) {
                map.put(contentId, part);
            }
        });
        return map;
    }

    private void cacheAudio(String key, InputStream stream) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        DataBuffer dataBuffer = readAs(stream);
        if (dataBuffer != null && dataBuffer.readableByteCount() > 0) {
            saveTtsBytesToRedis(key, dataBuffer);
            log.debug("Cached {}", key);
        }
    }

    private DataBuffer readAs(InputStream in) {
        try {
            DataBuffer dataBuffer = dataBufferFactory.allocateBuffer();
            byte[] buff = new byte[4096];
            int bytesRead;
            while((bytesRead = in.read(buff)) != -1) {
                dataBuffer.write(buff, 0, bytesRead);
            }
            return dataBuffer.slice(dataBuffer.readPosition(), dataBuffer.readableByteCount());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
            }
        }
        return null;
    }

    private void saveTtsBytesToRedis(String key, DataBuffer dataBuffer) {
        byte[] bytes = dataBuffer.asByteBuffer().array();
        if (bytes.length > 0) {
            String base64String = Base64.encodeBase64String(bytes);
            DproxyClientProvider
                    .getInstance()
                    .setex(getTtsBytesDproxyKey(key), ttsExpire, base64String);
        }
    }

    private String getTtsBytesDproxyKey(String key) {
        return String.format(TTS_BYTES_KEY_PATTERN, key);
    }
}

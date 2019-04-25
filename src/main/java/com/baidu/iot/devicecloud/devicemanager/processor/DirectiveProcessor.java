package com.baidu.iot.devicecloud.devicemanager.processor;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.MultipartStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CONTENT_TYPE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_CONTENT_DISPOSITION;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_METADATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_EQUALITY_SIGN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SEMICOLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.COMMAND_SPEAK;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD_URL;
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
public class DirectiveProcessor {
    private final TtsService ttsService;

    @Autowired
    public DirectiveProcessor(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    public List<JsonNode> process(String cuid, String sn, List<TlvMessage> messages) {
        if (messages != null && messages.size() > 0) {
            processPreTTS(cuid, sn, messages);
            Map<String, String> mapper = processFinalTTS(cuid, sn, messages);
            List<JsonNode> metadata = processMetadata(messages);
            if (metadata != null && metadata.size() > 0) {
                visitMetadata(metadata, mapper);
                return metadata;
            }
        }
        return Collections.emptyList();
    }

    private void processPreTTS(String cuid, String sn, List<TlvMessage> messages) {
        // all directive packages(0xF006)
        messages.stream()
                .filter(isPreTTSTlv)
                .forEach(tlv -> ttsService.requestTTSSync(assembleTtsRequest(cuid, sn, tlv), true));
    }

    private Map<String, String> processFinalTTS(String cuid, String sn, List<TlvMessage> messages) {
        Map<String, String> result = new HashMap<>();
        // all directive packages(0xF004)
        messages.stream()
                .filter(isTTSTlv)
                .forEach(tlv -> result.putAll(ttsService.requestTTSSync(assembleTtsRequest(cuid, sn, tlv), false)));
        return result;
    }

    private List<JsonNode> processMetadata(List<TlvMessage> messages) {
        List<ByteBuf> dumis = new ArrayList<>();
        // all directive packages(0xF003)
        messages.stream()
                .filter(isDirectiveTlv)
                .forEach(tlv -> {
                    BinaryNode valueBin = tlv.getValue();
                    if (valueBin != null) {
                        dumis.add(Unpooled.copiedBuffer(valueBin.binaryValue()));
                    }
                });

        int size = dumis.size();
        if (size > 0) {
            ByteBuf merged = size == 1 ? dumis.get(0) : Unpooled.wrappedBuffer(dumis.toArray(new ByteBuf[size]));
            MultipartFormDataProcessor multipartFormDataProcessor = new MultipartFormDataProcessor(merged);
            HttpResponse response = multipartFormDataProcessor.response();
            if (response.status() == HttpResponseStatus.OK && response.headers().contains(HEADER_CONTENT_TYPE)) {
                String boundary = getBoundary(response.headers().get(HEADER_CONTENT_TYPE));
                MultipartStream multipartStream =
                        new MultipartStream(
                                new ByteBufInputStream(multipartFormDataProcessor.slice()),
                                boundary.getBytes(),
                                merged.readableBytes(),
                                null
                        );
                return getDirective(multipartStream);
            }
        }
        return null;
    }

    private TtsRequest assembleTtsRequest(String cuid, String sn, TlvMessage messages) {
        TtsRequest ttsRequest = new TtsRequest();
        ttsRequest.setCuid(cuid);
        ttsRequest.setSn(sn);
        ttsRequest.setMessage(messages);
        return ttsRequest;
    }

    private void visitMetadata(List<JsonNode> metadata, Map<String, String> mapper) {
        if (metadata.size() < 1 || mapper.size() < 1) {
            return;
        }
        metadata
                .forEach(m -> {
                    if (isSpeak.test(m) && needRewrite.test(m)) {
                        String audioUrl = mapper.get(getContentId.apply(m));
                        rewrite.accept(m, audioUrl);
                    }
                });
    }

    private List<JsonNode> getDirective(MultipartStream multipartStream) {
        List<JsonNode> directives = new ArrayList<>();
        try {
            boolean hasNextPart = multipartStream.skipPreamble();

            while (hasNextPart) {
                Map<String, String> headers = getPartHeaders(multipartStream);

                if (headers != null && headers.containsKey(PARAMETER_CONTENT_DISPOSITION)) {
                    String name = getPartName(headers.get(PARAMETER_CONTENT_DISPOSITION));
                    if (!PARAMETER_METADATA.equalsIgnoreCase(name)) {
                        hasNextPart = multipartStream.readBoundary();
                        continue;
                    }
                    byte[] partBytes = getPartBytes(multipartStream);
                    if (isPartJSON(headers)) {
                        JsonNode directive = JsonUtil.readTree(partBytes);
                        if (null != directive && !directive.isNull()) {
                            directives.add(directive);
                        }
                    }
                }

                hasNextPart = multipartStream.readBoundary();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return directives;
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
            String items[] = contentDisposition.split(Pattern.quote(SPLITTER_SEMICOLON));
            if (items.length > 1) {
                String[] nameKV = items[1].split(Pattern.quote(SPLITTER_EQUALITY_SIGN));
                if (nameKV.length > 1 && PARAMETER_NAME.equalsIgnoreCase(StringUtils.trimAllWhitespace(nameKV[0])) && StringUtils.hasText(nameKV[1])) {
                    return StringUtils.trimTrailingCharacter(StringUtils.trimLeadingCharacter(nameKV[1], '"'), '"');
                }
            }
        }
        return "";
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

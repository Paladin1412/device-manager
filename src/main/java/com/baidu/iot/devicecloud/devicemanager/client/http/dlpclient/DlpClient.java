package com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient;

import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder.DcsToDlpBuilder;
import com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder.DlpStatusBuilder;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_AUTH_TOKEN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.AUDIO_PLAYER_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.AUDIO_PLAYER_STUTTER_NAME;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_CLIENT_CONTEXT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_DIRECTIVE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_EVENT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_HEADER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_KEY_PAYLOAD;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_TO_CLIENT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_TO_SERVER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.SPEAK_CONTROLLER_NAMESPACE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/1.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DlpClient extends AbstractHttpClient {
    private static final String DLP_ROOT = "/v1/proxy";
    private static final String[] DLP_MESSAGE_PATH = {"message"};

    @Value("${dlp.connect.proxy.api:}")
    private String dlpApi;

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(30);

    public void sendToDlp(final String uuid, final JsonNode data) {
        if (data.has("event")) {
            JsonNode header = data.path("event").path("header");
            String namespace = header.path("namespace").asText();
            String name = header.path("name").asText();
            if (header.isNull() || StringUtils.isEmpty(namespace)) {
                return;
            }

            if (AUDIO_PLAYER_NAMESPACE.equalsIgnoreCase(namespace)) {
                if (AUDIO_PLAYER_STUTTER_NAME.contains(name)) {
                    return;
                }
            } else if (!SPEAK_CONTROLLER_NAMESPACE.equalsIgnoreCase(namespace)) {
                return;
            }
        }

        threadPool.execute(() -> {
            log.debug("------------dlp deal with data--------------");
            try {
                if (data.get(DIRECTIVE_KEY_EVENT) != null) {
                    uploadEvent(uuid, data.path(DIRECTIVE_KEY_EVENT));
                }
                if (data.get(DIRECTIVE_KEY_CLIENT_CONTEXT) != null) {
                    uploadClientContext(uuid, (ArrayNode) data.path(DIRECTIVE_KEY_CLIENT_CONTEXT));
                }
                if (data.get(DIRECTIVE_KEY_DIRECTIVE) != null) {
                    uploadDirective(uuid, data.path(DIRECTIVE_KEY_DIRECTIVE));
                }
                if (data.get(DLP_TO_SERVER) != null || data.get(DLP_TO_CLIENT) != null) {
                    uploadDLP(uuid, data);
                }

            } catch (Exception e) {
                log.error("sendToDlpProxy Exception", e);
            }
        });
    }

    private void uploadDLP(String uuid, JsonNode data) {
        ArrayNode list = JsonUtil.createArrayNode();
        list.add(data);
        requestDlpServer(uuid, list);
    }

    private void uploadDirective(String uuid, JsonNode directive) {
        if (directive.has(DIRECTIVE_KEY_HEADER)) {
            ArrayNode list = JsonUtil.createArrayNode();
            DcsToDlpBuilder builder = new DcsToDlpBuilder();
            builder.header((ObjectNode) directive.path(DIRECTIVE_KEY_HEADER), false);
            builder.payload(directive.get(DIRECTIVE_KEY_PAYLOAD));
            list.add(builder.getData());
            requestDlpServer(uuid, list);
        }
    }

    private void uploadEvent(String uuid, JsonNode event) {
        ArrayNode list = JsonUtil.createArrayNode();
        DlpStatusBuilder statusBuilder = new DlpStatusBuilder();
        statusBuilder.payload(event.path(DIRECTIVE_KEY_PAYLOAD))
                .supplyStatus(event.path(DIRECTIVE_KEY_HEADER));
        log.debug("uploadEventStatus result={}", statusBuilder.getData());
        list.add(statusBuilder.getData());

        DcsToDlpBuilder eventBuilder = new DcsToDlpBuilder();
        eventBuilder.payload(event.path(DIRECTIVE_KEY_PAYLOAD));
        eventBuilder.header((ObjectNode) event.path(DIRECTIVE_KEY_HEADER), true);
        log.debug("DlpEventBuilder result={}", eventBuilder.getData());
        list.add(eventBuilder.getData());
        requestDlpServer(uuid, list);
    }

    private void uploadClientContext(String uuid, ArrayNode clientContext) {
        if (clientContext.size() > 0) {
            ArrayNode list = JsonUtil.createArrayNode();
            for (int i = 0; i < clientContext.size(); i++) {
                JsonNode context = clientContext.get(i);
                if (context.get(DIRECTIVE_KEY_HEADER) != null) {
                    DlpStatusBuilder builder = new DlpStatusBuilder();
                    builder.payload(context.get(DIRECTIVE_KEY_PAYLOAD))
                            .supplyStatus(context.path(DIRECTIVE_KEY_HEADER));
                    log.debug("uploadClientContext no={} result={}", i, builder.getData());
                    list.add(builder.getData());
                }
            }
            if (list.size() > 0) {
                requestDlpServer(uuid, list);
            }
        }
    }

    @Retryable(value = {SocketTimeoutException.class}, backoff = @Backoff(200))
    private void requestDlpServer(String uuid, ArrayNode data) {
        if (data == null || data.size() < 1) {
            return;
        }
        Request request = buildRequest(uuid, data);
        sendAsyncWithFuture(request, new CallbackFuture()).handleAsync(
                (r, t) -> {
                    try(ResponseBody body = r.body()) {
                        if (body != null) {
                            log.debug("[{}] requestDlpServer res, code={}, body={}",
                                    uuid, r.code(), body.string());
                        }
                    } catch (IOException ignore) {
                    } finally {
                        close(r);
                    }
                    return null;
                }
        );
    }

    private Request buildRequest(String uuid, ArrayNode data) {
        ObjectNode dataNode = JsonUtil.createObjectNode();
        dataNode.set("data", data);
        RequestBody requestBody = buildRequestBody(dataNode);
        if (requestBody == null) {
            return null;
        }

        String url = getFullPath();
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
        uriComponentsBuilder.queryParam("type", "DLP");
        uriComponentsBuilder.queryParam("deviceId", uuid);
        url = uriComponentsBuilder.build().toString();

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header(HEADER_AUTH_TOKEN, "test")
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .post(requestBody);

        return builder.build();
    }

    private String getFullPath() {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(dlpApi),
                getFullRelativePath()
        );
    }

    private String getFullRelativePath() {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(DLP_ROOT),
                StringUtils.arrayToDelimitedString(DLP_MESSAGE_PATH, SPLITTER_URL)
        );
    }
}

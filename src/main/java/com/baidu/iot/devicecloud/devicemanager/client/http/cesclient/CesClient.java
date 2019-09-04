package com.baidu.iot.devicecloud.devicemanager.client.http.cesclient;

import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/9/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class CesClient extends AbstractHttpClient {
    @Value("${log.ces.url:}")
    private String cesUrl;

    public CompletableFuture<Response> sendCesLogAsync(JsonNode cesLog) {
        Request request = buildRequest(cesLog);
        Assert.notNull(request, "The Redirecting Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    private Request buildRequest(JsonNode cesLog) {
        RequestBody requestBody = buildRequestBody(cesLog);

        if (requestBody == null) {
            return null;
        }

        Request.Builder builder = new Request.Builder()
                .url(cesUrl)
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .post(requestBody);

        return builder.build();
    }
}

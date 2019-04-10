package com.baidu.iot.devicecloud.devicemanager.client.http.callable;

import com.baidu.iot.devicecloud.devicemanager.client.http.OkHttpH1ClientPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public abstract class HttpCall<T> implements Callable<T> {
    private final OkHttpClient client;
    private final Request request;

    public HttpCall(Request request) {
        this(OkHttpH1ClientPool.getInstance(), request);
    }

    private HttpCall(OkHttpClient client, Request request) {
        if (client == null) {
            throw new NullPointerException("OkHttpClient is null.");
        }
        if (request == null) {
            throw new NullPointerException("Request is null.");
        }
        this.client = client;
        this.request = request;
    }

    @Override
    public final T call() throws Exception {
        return sendSync(client, request);
    }

    protected abstract T sendSync(final OkHttpClient client, final Request request);

    protected final T parseResponse(Response response, Class<T> t) throws IOException {
        if (response.isSuccessful() && response.body() != null) {
            return new ObjectMapper().readValue(response.body().string(), t);
        }
        log.error("Something may goes wrong: {}", response.toString());
        return null;
    }
}

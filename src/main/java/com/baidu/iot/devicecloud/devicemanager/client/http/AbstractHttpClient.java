package com.baidu.iot.devicecloud.devicemanager.client.http;


import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public abstract class AbstractHttpClient implements IHttpClient {
    private OkHttpClient client;

    protected AbstractHttpClient() {
        this.client = OkHttpH1ClientPool.getInstance();
    }

    @Override
    public Response sendSync(@NotNull Request request) {
        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            log.error("Sending a sync call failed", e);
        }
        return null;
    }

    @Override
    public void sendAsync(Request request, Callback callback) {
        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    @Override
    public CompletableFuture<Response> sendAsyncWithFuture(Request request, CallbackFuture callback) {
        Call call = client.newCall(request);
        call.enqueue(callback);
        return callback;
    }

    @Nullable
    protected RequestBody buildRequestBody(Object params) {
        String serialized = JsonUtil.serialize(params);
        if (StringUtils.isEmpty(serialized)) {
            return null;
        }
        return RequestBody.create(
                MediaType.get(org.springframework.http.MediaType.APPLICATION_JSON_UTF8.toString()),
                serialized);
    }
}

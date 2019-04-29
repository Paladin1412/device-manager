package com.baidu.iot.devicecloud.devicemanager.client.http.callback;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.internal.annotations.EverythingIsNonNull;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/21.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class CallbackFuture extends CompletableFuture<Response> implements Callback {
    @Override
    @EverythingIsNonNull
    public void onFailure(Call call, IOException e) {
        log.error("Request to dcs proxy has failed. request: {}", call.request());
        log.error("The stack traces listed below", e);
        super.completeExceptionally(e);
    }

    @Override
    @EverythingIsNonNull
    public void onResponse(Call call, Response response) throws IOException {
        log.debug("Received an response {} to request {}", response.body(), call.request());
        super.complete(response);
    }
}

package com.baidu.iot.devicecloud.devicemanager.client.http;

import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public interface IHttpClient {
    Response sendSync(Request request);
    void sendAsync(Request request, Callback callback);
    CompletableFuture sendAsyncWithFuture(Request request, CallbackFuture callback);
}

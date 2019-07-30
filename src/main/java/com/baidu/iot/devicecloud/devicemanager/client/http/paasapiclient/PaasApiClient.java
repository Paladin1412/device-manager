package com.baidu.iot.devicecloud.devicemanager.client.http.paasapiclient;

import com.baidu.iot.devicecloud.devicemanager.bean.OtaTaskCreateRequest;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_AUTH_TOKEN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class PaasApiClient extends AbstractHttpClient {
    private static final String PAAS_API_DEVICE_ROOT = "v1/device";
    private static final String PAAS_API_OTA_TASK_ROOT = "v1/ota/task";
    private static final String PAAS_API_PACKAGE_ROOT = "v1/package";
    private static final String[] DEVICE_OTA_PATH = {"ota"};

    @Value("${paas.api.url:}")
    private String paasApiUrl;

    public CompletableFuture<Response> checkOtaStatus(String uuid) {
        Request request = buildCheckOtaRequest(uuid);
        Assert.notNull(request, "Checking OTA Result Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public CompletableFuture<Response> getPackage(long id) {
        Request request = buildPackageRequest(id);
        Assert.notNull(request, "Getting package Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public CompletableFuture<Response> createTask(OtaTaskCreateRequest message) {
        Request request = buildCreateTaskRequest(message);
        Assert.notNull(request, "Creating OTA Task Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public CompletableFuture<Response> getTask(String uuid) {
        Request request = buildTaskRequest(uuid);
        Assert.notNull(request, "Getting package Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    public CompletableFuture<Response> getTaskById(long id) {
        Request request = buildTaskRequest(id);
        Assert.notNull(request, "Getting package Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    @Nullable
    private Request buildCheckOtaRequest(String uuid) {
        String httpUrl = getFullPath();

        Request.Builder requestBuilder = new Request.Builder();

        UriComponentsBuilder urlBuilder;
        try {
            urlBuilder = UriComponentsBuilder.fromHttpUrl(PathUtil.lookAfterSuffix(httpUrl));
            urlBuilder.path(uuid);
        } catch (Exception e) {
            log.error("Appending query params failed", e);
            return null;
        }
        requestBuilder
                .url(String.valueOf(urlBuilder.build()))
                .header(HEADER_AUTH_TOKEN, "test")
                .get();

        return requestBuilder.build();
    }

    private String getFullPath() {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(paasApiUrl),
                getFullRelativePath()
        );
    }

    private String getFullRelativePath() {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(PAAS_API_DEVICE_ROOT),
                StringUtils.arrayToDelimitedString(PaasApiClient.DEVICE_OTA_PATH, SPLITTER_URL)
        );
    }

    @Nullable
    private Request buildPackageRequest(long id) {
        String httpUrl = StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(paasApiUrl),
                PaasApiClient.PAAS_API_PACKAGE_ROOT
        );
        Request.Builder requestBuilder = new Request.Builder();

        UriComponentsBuilder urlBuilder;
        try {
            urlBuilder = UriComponentsBuilder.fromHttpUrl(PathUtil.lookAfterSuffix(httpUrl));
            urlBuilder.path(String.valueOf(id));
        } catch (Exception e) {
            log.error("Appending query params failed", e);
            return null;
        }
        requestBuilder
                .url(String.valueOf(urlBuilder.build()))
                .header(HEADER_AUTH_TOKEN, "test")
                .get();

        return requestBuilder.build();
    }

    @Nullable
    private Request buildCreateTaskRequest(OtaTaskCreateRequest message) {
        String httpUrl = StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(paasApiUrl),
                PaasApiClient.PAAS_API_OTA_TASK_ROOT
        );

        RequestBody requestBody = buildRequestBody(message);
        if (requestBody == null) {
            return null;
        }

        return new Request.Builder()
                .url(httpUrl)
                .header(HEADER_AUTH_TOKEN, "test")
                .post(requestBody)
                .build();
    }

    @Nullable
    private Request buildTaskRequest(String uuid) {
        String httpUrl = StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(paasApiUrl),
                PaasApiClient.PAAS_API_OTA_TASK_ROOT
        );
        Request.Builder requestBuilder = new Request.Builder();

        UriComponentsBuilder urlBuilder;
        try {
            urlBuilder = UriComponentsBuilder.fromHttpUrl(httpUrl);
            urlBuilder.queryParam("limit", 1);
            urlBuilder.queryParam("inStatus", "0,1,6");
            urlBuilder.queryParam("deviceUuid", uuid);
        } catch (Exception e) {
            log.error("Appending query params failed", e);
            return null;
        }
        requestBuilder
                .url(String.valueOf(urlBuilder.build()))
                .header(HEADER_AUTH_TOKEN, "test")
                .get();

        return requestBuilder.build();
    }

    @Nullable
    private Request buildTaskRequest(long id) {
        String httpUrl = StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(paasApiUrl),
                PaasApiClient.PAAS_API_OTA_TASK_ROOT
        );
        Request.Builder requestBuilder = new Request.Builder();

        UriComponentsBuilder urlBuilder;
        try {
            urlBuilder = UriComponentsBuilder.fromHttpUrl(PathUtil.lookAfterSuffix(httpUrl));
            urlBuilder.path(Long.toString(id));
        } catch (Exception e) {
            log.error("Appending path params failed", e);
            return null;
        }
        requestBuilder
                .url(String.valueOf(urlBuilder.build()))
                .header(HEADER_AUTH_TOKEN, "test")
                .get();

        return requestBuilder.build();
    }
}

package com.baidu.iot.devicecloud.devicemanager.client.http.redirectclient;

import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.callback.CallbackFuture;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CUID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_LOG_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_SN;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;
import static com.baidu.iot.devicecloud.devicemanager.constant.MessageType.DATA_POINT;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/30.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class RedirectClient extends AbstractHttpClient {
    private static final String DM_ROOT = "api/v2";
    private static final String[] DM_REPORT_PATH = {"report"};

    @Value("${dm.scheme:http://}")
    private String dmScheme;

    @Retryable(value = {SocketTimeoutException.class}, backoff = @Backoff(200))
    public CompletableFuture<Response> redirectDataPointAsync(String ip, String port, DataPointMessage message) {
        Request request = buildRequest(ip, port, message);
        Assert.notNull(request, "The Redirecting Request is null");
        return sendAsyncWithFuture(request, new CallbackFuture());
    }

    private Request buildRequest(String ip, String port, DataPointMessage message) {
        RequestBody requestBody = buildRequestBody(message);

        if (requestBody == null) {
            return null;
        }

        Request.Builder builder = new Request.Builder()
                .url(getFullPath(ip, port, DM_REPORT_PATH))
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .header(CommonConstant.HEADER_MESSAGE_TIMESTAMP, Long.toString(System.currentTimeMillis()))
                .header(CommonConstant.HEADER_MESSAGE_TYPE, Integer.toString(DATA_POINT))
                .post(requestBody);

        Optional.ofNullable(message.getDeviceId()).ifPresent(
                cuid -> builder.header(HEADER_CUID, cuid)
        );

        Optional.ofNullable(message.getSn()).ifPresent(
                sn -> builder.header(HEADER_SN, sn)
        );

        Optional.ofNullable(message.getLogId()).ifPresent(
                logId -> builder.header(HEADER_LOG_ID, logId)
        );

        return builder.build();
    }

    private String getFullPath(String ip, String port, String[] path) {
        String domainAddress = String.format("%s%s:%s", dmScheme, ip, port);
        return StringUtils.applyRelativePath(
                domainAddress,
                getFullRelativePath(path)
        );
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                DM_ROOT,
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

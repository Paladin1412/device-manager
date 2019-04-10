package com.baidu.iot.devicecloud.devicemanager.client.http.dproxy;

import com.baidu.iot.devicecloud.devicemanager.client.http.AbstractHttpClient;
import com.baidu.iot.devicecloud.devicemanager.util.BnsUtil;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.annotations.EverythingIsNonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


import java.io.IOException;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DproxyClient extends AbstractHttpClient {
    private static final String DPROXY_ROOT = "DproxyServer";
    private static final String[] DPROXY_COMMAND_PATH = {"cmd"};

    @Value("${dproxy.api}")
    private String dproxyApi;
    @Value("${dproxy.bns:}")
    private String dproxyBns;
    @Value("${dproxy.scheme:http://}")
    private String dproxyScheme;

    public DproxyResponse sendCommand(DproxyRequest dproxyRequest) {
        Response response = null;
        try {
            Request request = buildDproxyRequest(dproxyRequest);
            Assert.notNull(request, "Dproxy Request is null");
            response = sendSync(request);
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                Assert.notNull(body, "Dproxy Response body is null");
                DproxyResponse commandInfo = JsonUtil.deserialize(body.string(), DproxyResponse.class);
                log.debug("Read command info: {}", commandInfo);
                return commandInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(response);
        }

        log.error("Auth failed, response from remote: {}", response);
        return null;
    }

    public DproxyResponse sendCommandAsync(DproxyRequest dproxyRequest) {
        Request request;
        request = buildDproxyRequest(dproxyRequest);
        sendAsync(request, new Callback() {
            @Override
            @EverythingIsNonNull
            public void onFailure(Call call, IOException e) {
                log.error("Sending command to dproxy failed. request:{} error:{}",
                        call.request().toString(), e.getMessage());
            }

            @Override
            @EverythingIsNonNull
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    log.debug("Sending command to dproxy succeed, request:{}", call.request().toString());
                }
            }
        });
        return null;
    }

    @Nullable
    private Request buildDproxyRequest(DproxyRequest params) {
        RequestBody requestBody = buildRequestBody(params);
        if (requestBody == null) {
            return null;
        }
        Request.Builder builder = new Request.Builder()
                .url(getFullPath(DPROXY_COMMAND_PATH))
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                .post(requestBody);

        return builder.build();
    }

    private String getFullPath(String[] path) {
        String root = BnsUtil.getBNSOrUrl(dproxyScheme, dproxyBns, dproxyApi);
        if (!StringUtils.startsWithIgnoreCase(root, dproxyScheme)) {
            root = dproxyScheme + root;
        }
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(root),
                getFullRelativePath(path)
        );
    }

    private String getFullRelativePath(String[] path) {
        return StringUtils.applyRelativePath(
                PathUtil.lookAfterSuffix(DPROXY_ROOT),
                StringUtils.arrayToDelimitedString(path, SPLITTER_URL)
        );
    }
}

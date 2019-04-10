package com.baidu.iot.devicecloud.devicemanager.client.http.callable.deviceiam;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.client.http.callable.HttpCall;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class DeviceIamCall extends HttpCall<BaseResponse> {
    public DeviceIamCall(Request request) {
        super(request);
    }

    @Override
    protected BaseResponse sendSync(OkHttpClient client, Request request) {
        Call call = client.newCall(request);
        Response response = null;
        try {
            response = call.execute();
            return super.parseResponse(response, BaseResponse.class);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception ignore) {}
            }
        }
        return null;
    }
}

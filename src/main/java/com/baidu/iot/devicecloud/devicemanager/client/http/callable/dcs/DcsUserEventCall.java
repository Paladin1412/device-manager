package com.baidu.iot.devicecloud.devicemanager.client.http.callable.dcs;

import com.baidu.iot.devicecloud.devicemanager.client.http.callable.HttpCall;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean.DcsUserStateResponse;
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
public class DcsUserEventCall extends HttpCall<DcsUserStateResponse> {
    public DcsUserEventCall(Request request) {
        super(request);
    }

    @Override
    protected DcsUserStateResponse sendSync(OkHttpClient client, Request request) {
        Call call = client.newCall(request);
        Response response = null;
        try {
            response = call.execute();
            return super.parseResponse(response, DcsUserStateResponse.class);
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

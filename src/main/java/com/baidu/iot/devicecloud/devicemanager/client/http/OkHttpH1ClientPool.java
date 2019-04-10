package com.baidu.iot.devicecloud.devicemanager.client.http;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/13.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class OkHttpH1ClientPool {
    private static OkHttpClient ourInstance;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        ourInstance = builder
                .retryOnConnectionFailure(true)
                .readTimeout(5000,    TimeUnit.MILLISECONDS)
                .writeTimeout(5000,   TimeUnit.MILLISECONDS)
                .connectTimeout(10000, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES))
                .build();
    }

    public static OkHttpClient getInstance() {
        return ourInstance;
    }

    private OkHttpH1ClientPool() {
    }
}

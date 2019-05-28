package com.baidu.iot.devicecloud.devicemanager.client.http;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

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
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        ourInstance = builder
                .addInterceptor(logging)
                .retryOnConnectionFailure(true)
                .readTimeout(4000, TimeUnit.MILLISECONDS)
                .writeTimeout(4000, TimeUnit.MILLISECONDS)
                .connectTimeout(4000, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES))
                .build();
    }

    public static OkHttpClient getInstance() {
        return ourInstance;
    }

    private OkHttpH1ClientPool() {
    }
}

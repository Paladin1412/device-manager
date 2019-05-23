package com.baidu.iot.devicecloud.devicemanager.client.http.dproxy;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.RetryException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;

import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;

/**
 * DproxyClientProvider
 *
 * @author Long Yunxiang (longyunxiang@baidu.com)
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DproxyClientProvider implements InitializingBean {

    private final JsonUtil jsonUtil;
    private static final String prefix = "IOT\u0001";
    private final DproxyClient dproxyClient;
    @Getter
    private static DproxyClientProvider instance;

    @Value("${dproxy.expireSeconds:600}")
    private int expireSeconds;

    @Override
    public void afterPropertiesSet() {
        instance = this;
    }

    private DproxyResponse getConnection(DproxyRequest request) {
        return dproxyClient.sendCommand(request);
    }

    public void set(String key, Object value) {
        setex(key, expireSeconds, value);
    }

    public void setex(String key, long seconds, Object value) {
        if (seconds == 0) {
            return;
        }
        DproxyRequest request;
        if (seconds < 0) {
            request = new DproxyRequest("SET", prefix + key, value);
        } else {
            request = new DproxyRequest("SETEX", prefix + key, seconds, value);
        }
        try {
            getConnection(request);
        } catch (Exception e) {
            // pass
            log.warn("dproxy setex {} failed, {}", key, e);
        }
    }

    @Retryable(value = {RetryException.class}, backoff = @Backoff(200))
    public CompletableFuture<Response> setexAsync(String key, long seconds, Object value) {
        if (seconds == 0) {
            return null;
        }
        DproxyRequest request;
        if (seconds < 0) {
            request = new DproxyRequest("SET", prefix + key, value);
        } else {
            request = new DproxyRequest("SETEX", prefix + key, seconds, value);
        }
        try {
            return dproxyClient.sendCommandAsync(request).handleAsync(
                    (r, t) -> {
                        if (!r.isSuccessful()) {
                            close(r);
                            throw new RetryException("Retry");
                        }
                        return r;
                    }
            );
        } catch (Exception e) {
            // pass
            log.warn("dproxy setex {} failed, {}", key, e);
            return null;
        }
    }

    public <T> T get(String key, Class<T> type) {
        if (key == null || "".equals(key)) {
            return null;
        }
        DproxyResponse response;
        DproxyRequest request = new DproxyRequest("GET", prefix + key);
        try {
            response = getConnection(request);
            if (null != response) {
                T result = null;
//                log.info("dproxy res={}", response.getRes());
                if (null != response.getRes() && !response.getRes().equals("null")) {
                    if (type == String.class) {
                        return (T) response.getRes();
                    }
                    result = jsonUtil.deserialize(String.valueOf(response.getRes()), type);
                }
                return result;
            }
        } catch (Exception e) {
            // pass
            log.warn("dproxy get {} failed", key);
        }
        return null;
    }

    public void del(String key) {
        DproxyRequest request = new DproxyRequest("DEL", prefix + key);
        try {
            dproxyClient.sendCommandAsync(request).handleAsync(
                    (r, t) -> {
                        if (r.isSuccessful()) {
                            log.debug("Deleting {} from redis succeeded", key);
                        }
                        close(r);
                        return null;
                    }
            );
        } catch (Exception e) {
            log.error("Deleting {} from redis failed", key, e);
        }
    }

    private boolean expire(String key, long expire) {
        boolean ok = false;
        if (StringUtils.isEmpty(key)) {
            return false;
        }

        if (exists(key)) {
            DproxyResponse response;
            DproxyRequest request = new DproxyRequest("EXPIRE", prefix + key, expire);
            try {
                response = getConnection(request);
                if (null != response) {
//                    log.info("dproxy res={}", response.getRes());
                    Object res = response.getRes();
                    ok = res instanceof Number && (Integer) res == 1;
                }
            } catch (Exception e) {
                // pass
                log.warn("dproxy set an expiration of {}s on {} failed", expire, key);
            }

        }
        return ok;
    }

    public boolean exists(String key) {
        DproxyResponse response;
        boolean ok = false;
        DproxyRequest request = new DproxyRequest("EXISTS", prefix + key);
        try {
            response = getConnection(request);
            if (null != response) {
                log.info("dproxy exists res={}", response.getRes());
                Object res = response.getRes();
                ok = res instanceof Number && (Integer) res == 1;
            }
        } catch (Exception e) {
            // pass
            log.warn("dproxy exists {} failed", key);
        }
        return ok;
    }

    public String get(String key) {
        return get(key, String.class);
    }

    public boolean hset(String key, long seconds, String hKey, Object value) {
        if (seconds == 0) {
            return false;
        }
        try {
            DproxyRequest request = new DproxyRequest("HSET", prefix + key, hKey, value);
            DproxyResponse response = getConnection(request);
            if (response != null && response.getStatus() == 0) {
                log.debug("hset successfully res={}", response.getRes());
                return expire(key, seconds);
            }
        } catch (Exception e) {
            // pass
            log.warn("redis hset {} failed", key);
        }
        return false;
    }

    public <T> T hget(String key, String hKey, Class<T> type) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(hKey)) {
            return null;
        }
        try {
            DproxyRequest request = new DproxyRequest("HGET", prefix + key, hKey);
            DproxyResponse response = getConnection(request);
            if (response != null && response.getStatus() == 0) {
                log.debug("hget successfully res={}", response.getRes());
                if (type == String.class) {
                    return (T)response.getRes();
                }
                return jsonUtil.deserialize(String.valueOf(response.getRes()), type);
            }
        } catch (Exception e) {
            // pass
            log.warn("redis hget {} : {} failed", key, hKey);
        }
        return null;
    }
}

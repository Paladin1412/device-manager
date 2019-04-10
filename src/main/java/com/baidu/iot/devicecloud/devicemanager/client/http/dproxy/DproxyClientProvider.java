package com.baidu.iot.devicecloud.devicemanager.client.http.dproxy;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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

    public DproxyResponse getConnection(DproxyRequest request) {
        return dproxyClient.sendCommand(request);
    }

    public Boolean setNX(String key, Object value) {
        DproxyResponse response = null;
        Boolean ok = false;
        DproxyRequest request = new DproxyRequest("SETNX", prefix + key, value);
        try {
            response = getConnection(request);
            if (null != response) {
//                log.info("dproxy res={}", response.getRes());
                //res 0 false 1 true
                Object res = response.getRes();
                ok = res instanceof Number && (Integer) res == 1;
            }
        } catch (Exception e) {
            // pass
            log.warn("dproxy setNX {} failed", key);
        }
        return ok;
    }

    public void set(String key, Object value) {
        setex(key, expireSeconds, value);
    }


    public void rpush(String key, Object value) {
        DproxyResponse response = null;
        try {
            DproxyRequest request = new DproxyRequest("RPUSH", prefix + key, value);
            response = getConnection(request);
        } catch (Exception e) {
            log.warn("dproxy setex {} failed, {}", key, e);
        }
    }

    public void setex(String key, long seconds, Object value) {
        if (seconds == 0) {
            return;
        }
        DproxyResponse response = null;
        DproxyRequest request = null;
        if (seconds < 0) {
            request = new DproxyRequest("SET", prefix + key, value);
        } else {
            request = new DproxyRequest("SETEX", prefix + key, seconds, value);
        }
        try {
            response = getConnection(request);

//            if (null != response) {
//                log.info("dproxy res={}", response.getRes());
//            }
        } catch (Exception e) {
            // pass
            log.warn("dproxy setex {} failed, {}", key, e);
        }
    }

    public void setexAsync(String key, long seconds, Object value) {
        if (seconds == 0) {
            return;
        }
        DproxyRequest request = null;
        if (seconds < 0) {
            request = new DproxyRequest("SET", prefix + key, value);
        } else {
            request = new DproxyRequest("SETEX", prefix + key, seconds, value);
        }
        try {
            dproxyClient.sendCommandAsync(request);
        } catch (Exception e) {
            // pass
            log.warn("dproxy setex {} failed, {}", key, e);
        }
    }

    public <T> T get(String key, Class<T> type) {
        if (key == null || "".equals(key)) {
            return null;
        }
        DproxyResponse response = null;
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
        DproxyResponse response = null;
        DproxyRequest request = new DproxyRequest("DEL", prefix + key);
        try {
            response = getConnection(request);
        } catch (Exception e) {
            // pass
            log.warn("dproxy del {} failed", key);
        }
    }

    public boolean expire(String key, long expire) {
        boolean ok = false;
        if (key == null || "".equals(key)) {
            return ok;
        }

        if (exists(key)) {
            DproxyResponse response = null;
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
        DproxyResponse response = null;
        Boolean ok = false;
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

    public Long ttl(String key) {
        Long result = -3L;
        if (key == null || "".equals(key)) {
            return result;
        }

        DproxyResponse response = null;
        DproxyRequest request = new DproxyRequest("TTL", prefix + key);
        try {
            response = getConnection(request);
            if (null != response) {
                log.info("dproxy ttl res={}", response.getRes());
                result = Long.parseLong(response.getRes().toString());
            }
        } catch (Exception e) {
            // pass
            log.warn("dproxy ttl failed", key);
        }

        return result;
    }


    public String get(String key) {
        return get(key, String.class);
    }


    public boolean setNX(String key, String value, long expireSeconds) {
        if (key == null || "".equals(key) || value == null || "".equals(value)) {
            return false;
        }

        boolean result = setNX(key, value);
        if (result) {
            result = expire(key, expireSeconds);
        }

        return result;
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

    public boolean hmset(String key, long seconds, Object... values) {
        if (seconds == 0) {
            return false;
        }
        try {
            DproxyRequest request = new DproxyRequest("HMSET", prefix + key, values);
            DproxyResponse response = getConnection(request);
            if (response != null && response.getStatus() == 0) {
                log.debug("hmset successfully res={}", response.getRes());
                return expire(key, seconds);
            }
        } catch (Exception e) {
            // pass
            log.warn("redis hmset {} failed", key);
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

    public boolean hdel(String key, String hKey) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(hKey)) {
            return false;
        }
        try {
            DproxyRequest request = new DproxyRequest("HDEL", prefix + key, hKey);
            DproxyResponse response = getConnection(request);
            if (response != null && response.getStatus() == 0) {
                return true;
            }
        } catch (Exception e) {
            // pass
            log.warn("redis hdel {} : {} failed", key, hKey);
        }
        return false;
    }

    public <T> List<T> hmget(String key, Class<T> type, String... hKeys) {
        if (StringUtils.isEmpty(key) || hKeys.length == 0) {
            return null;
        }
        try {
            DproxyRequest request = new DproxyRequest("HMGET", prefix + key, hKeys);
            DproxyResponse response = getConnection(request);
            if (response != null && response.getStatus() == 0 && !response.getRes().equals("null")) {
                log.debug("hmget successfully res={}", response.getRes());
                if (type == String.class) {
                    return (List<T>)response.getRes();
                } else {
                    List<T> result = new ArrayList<>();
                    List res = (List)response.getRes();
                    for (Object r : res) {
                        if (null == r) {
                            result.add(null);
                        } else {
                            result.add(jsonUtil.deserialize(String.valueOf(r), type));
                        }
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            // pass
            log.warn("redis hmget {} failed", key);
        }
        return null;
    }

}

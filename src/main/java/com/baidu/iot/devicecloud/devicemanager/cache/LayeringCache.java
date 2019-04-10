package com.baidu.iot.devicecloud.devicemanager.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class LayeringCache extends AbstractValueAdaptingCache {
    private final String name;

    private boolean usedFirstCache = true;

    private final GuavaCache l1;

    private final DproxyCache l2;

    public LayeringCache(LayeringCacheConfig config) {
        super(true);

        if (config == null) {
            throw new NullPointerException("LayeringCacheConfig is null.");
        }

        this.name = config.getName();
        this.l1 = new GuavaCache(config.getGuavaCacheConfig());
        this.l2 = new DproxyCache(config.getDproxyCacheConfig());
    }

    @Nullable
    @Override
    protected Object lookup(Object key) {
        Object value = null;

        if (usedFirstCache) {
            value = l1.lookup(key);
            log.debug("Level 1st cache lookup the value for {}: {}", key, value);
        }

        if (value == null) {
            value = l2.lookup(key);
            log.debug("Level 2nd cache lookup the value for {}: {}", key, value);
        }
        return value;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Nullable
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        T value;

        if (usedFirstCache) {
            value = l1.get(key, () -> l2.get(key, valueLoader));
            log.debug("Level 1st cache gets the value for {}: {}", key, value);
        } else {
            value = l2.get(key, valueLoader);
            log.debug("Level 2nd cache gets the value for {}: {}", key, value);
        }
        return value;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (usedFirstCache) {
            l1.put(key, value);
        }
        l2.put(key, value);
    }

    @Nullable
    @Override
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        if (usedFirstCache) {
            l1.putIfAbsent(key, value);
        }
        return l2.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        l2.evict(key);

        if (usedFirstCache) {
            l1.evict(key);
        }
    }

    @Override
    public void clear() {
        l2.clear();

        if (usedFirstCache) {
            l1.clear();
        }
    }
}

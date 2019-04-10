package com.baidu.iot.devicecloud.devicemanager.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class GuavaCache extends AbstractValueAdaptingCache {
    private final String name;
    private final Cache<Object, Object> cache;

    public GuavaCache(String name) {
        this("GuavaCache",
                500,
                50_000L,
                300,
                1,
                TimeUnit.MINUTES);
    }

    public GuavaCache(String name, int initialCapacity, long maximumSize, int concurrencyLevel, long ttl, TimeUnit ttlUnit) {
        super(true);
        this.name = name;
        this.cache = CacheBuilder.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .concurrencyLevel(concurrencyLevel)
                .expireAfterWrite(ttl, ttlUnit)
                .build();
    }

    public GuavaCache(GuavaCacheConfig config) {
        super(true);

        if (config == null) {
            throw new NullPointerException("GuavaCacheConfig is null.");
        }

        this.name = config.getName();
        this.cache = CacheBuilder.newBuilder()
                .initialCapacity(config.getInitialCapacity())
                .maximumSize(config.getMaximumSize())
                .concurrencyLevel(config.getConcurrencyLevel())
                .expireAfterWrite(config.getTtl(), config.getTtlUnit())
                .build();
    }

    @Nullable
    @Override
    protected Object lookup(Object key) {
        Object value = cache.getIfPresent(key);
        log.debug("{} lookup the value for {}: {}", this.getClass().getSimpleName(), key, value);
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

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            T value = (T) cache.get(key, valueLoader);

            if (value instanceof NullValue) {
                return null;
            }

            return value;
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        cache.put(key, value);
    }

    @Nullable
    @Override
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        Object existed = cache.getIfPresent(key);
        if (existed == null) {
            cache.put(key, value);
            return null;
        }
        return new SimpleValueWrapper(existed);
    }

    @Override
    public void evict(Object key) {
        cache.invalidate(key);

    }

    @Override
    public void clear() {
        cache.cleanUp();
    }
}

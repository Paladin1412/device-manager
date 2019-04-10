package com.baidu.iot.devicecloud.devicemanager.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class DproxyCache extends AbstractValueAdaptingCache {
    private final String name;

    private final String prefix;

    public DproxyCache(String name, String prefix) {
        super(true);

        this.name = name;
        this.prefix = prefix;
    }

    public DproxyCache(DproxyCacheConfig config) {
        super(true);

        if (config == null) {
            throw new NullPointerException("GuavaCacheConfig is null.");
        }

        this.name = config.getName();
        this.prefix = config.getPrefix();
    }

    @Nullable
    @Override
    protected Object lookup(Object key) {
        return null;
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
        return null;
    }

    @Override
    public void put(Object key, @Nullable Object value) {

    }

    @Nullable
    @Override
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        return null;
    }

    @Override
    public void evict(Object key) {

    }

    @Override
    public void clear() {

    }
}

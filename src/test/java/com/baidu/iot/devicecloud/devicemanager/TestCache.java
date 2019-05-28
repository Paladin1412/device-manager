package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/27.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestCache {
    private final Logger logger = LoggerFactory.getLogger(TestCache.class);
    private LoadingCache<String, String> cache;
    private Cache<String, Optional<String>> cache1;
    private int index;

    @Before
    public void setup() {
        index = 0;
        cache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(4))
                .refreshAfterWrite(Duration.ofSeconds(5))
                .recordStats()
                .removalListener((RemovalListener<String, String>) n -> logger.info("Removed: ({}, {}), caused by: {}", n.getKey(), n.getValue(), n.getCause().toString()))
                .build(CacheLoader.asyncReloading(new AddressCacheLoader(), Executors.newSingleThreadExecutor()));

        cache1 = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(5))
                .expireAfterAccess(Duration.ofSeconds(2))
                .removalListener(
                        (RemovalListener<String, Optional<String>>) n ->
//                                logger.info("Removed Access Token: ({}, {}), caused by: {}",
//                                        n.getKey(), n.getValue().orElse("null"), n.getCause().toString())
                                System.out.println(String.format("Removed Access Token: (%s, %s), caused by: %s",
                                        n.getKey(), n.getValue().orElse("null"), n.getCause().toString()))
                )
                .build();
    }

    private class AddressCacheLoader extends CacheLoader<String, String> {

        @Override
        public String load(String s) throws Exception {
            logger.info("Cache loading...");
            Thread.sleep(2000);
            String result = "test" + index++;
            logger.info("Loaded: " + result);
            return result;
        }

        @Override
        public ListenableFuture<String> reload(String key, String oldValue) throws Exception {
            logger.info("Reloading automatically.");
            return super.reload(key, oldValue);
        }
    }

    @Test
    public void testGet() throws InterruptedException, ExecutionException {
        int i = 0;
        while (i++ < 10) {
            String value = cache.get("test-key");
            logger.info("Got: " + value);
            Thread.sleep(3000);
        }
        Thread.sleep(30000);
    }

    @Test
    public void test1() throws ExecutionException, InterruptedException {
        cache1.get("test1", () -> Optional.of("value1"));

        Thread.sleep(10000);
    }

    @After
    public void tearDown() {
        index = 0;
        cache = null;
    }
}

package com.baidu.iot.devicecloud.devicemanager.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DCS_PROXY_BNS;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.ASR_PORT_OFFSET;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.EVENT_PORT_OFFSET;

/**
 * <p>An effort to ensure a device would connect to the same remote server during a connection session.</p>
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class AddressCache {
    private static final String KEY_PATTERN = "%s_%s";
    private static final String DCS_SUFFIX = "DCS";
    private static final long EXPIRATION_MINUTES = 1;

    public static LoadingCache<String, InetSocketAddress> cache;

    static {
        cache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .initialCapacity(1_000)
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofMinutes(EXPIRATION_MINUTES))
                .refreshAfterWrite(Duration.ofMinutes(EXPIRATION_MINUTES))
                .recordStats()
                .removalListener((RemovalListener<String, InetSocketAddress>) n -> log.debug("Removed: ({}, {}), caused by: {}", n.getKey(), n.getValue(), n.getCause().toString()))
                .build(CacheLoader.asyncReloading(new AddressCache.AddressCacheLoader(), Executors.newSingleThreadExecutor()));
    }

    private static class AddressCacheLoader extends CacheLoader<String, InetSocketAddress> {
        @Override
        @ParametersAreNonnullByDefault
        public InetSocketAddress load(String s) throws Exception {
            if (StringUtils.endsWithIgnoreCase(s, DCS_SUFFIX)) {
                return BnsCache.getRandomInetAddress(DCS_PROXY_BNS);
            }
            return null;
        }
    }

    public static String getDcsAddressKey(String cuid) {
        return String.format(KEY_PATTERN, cuid, DCS_SUFFIX);
    }

    /**
     * Get the remote address that could deal with events
     * @param cuid the device id
     * @param isAsr {@code true} for asr, {@code false} for event
     * @return {@link InetSocketAddress}
     */
    @Nullable
    public static InetSocketAddress getDcsTcpAddress(String cuid, boolean isAsr) {
        InetSocketAddress initSocketAddress = null;
        try {
            initSocketAddress = cache.get(getDcsAddressKey(cuid));
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (initSocketAddress == null) {
            initSocketAddress = BnsCache.getRandomInetAddress(DCS_PROXY_BNS);
        }
        if (initSocketAddress == null) {
            return null;
        }
        int initPort = initSocketAddress.getPort();
        InetAddress initAddress = initSocketAddress.getAddress();
        return new InetSocketAddress(initAddress, initPort + (isAsr ? ASR_PORT_OFFSET : EVENT_PORT_OFFSET));
    }
}

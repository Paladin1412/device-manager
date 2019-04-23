package com.baidu.iot.devicecloud.devicemanager.cache;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DCS_PROXY_API;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DCS_PROXY_BNS;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DH_API;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DH_BNS;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DI_API;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DI_BNS;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DPROXY_API;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.DPROXY_BNS;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.TTS_PROXY_API;
import static com.baidu.iot.devicecloud.devicemanager.config.remoteserver.RemoteServerConfig.TTS_PROXY_BNS;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_SPACE;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.ASR_PORT_OFFSET;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.EVENT_PORT_OFFSET;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class BnsCache {
    private final static Supplier<List<String>> emptySupplier = ArrayList::new;

    private static List<String> bnsList;
    private static LoadingCache<String, List<String>> bnsIpPortCache;

    static {
        bnsIpPortCache = CacheBuilder.newBuilder()
                .build(CacheLoader.asyncReloading(new BnsCacheLoader(), Executors.newSingleThreadExecutor()));

        bnsList = Arrays.asList(
                DPROXY_BNS,
                DCS_PROXY_BNS,
                TTS_PROXY_BNS,
                DH_BNS,
                DI_BNS
        );
        log.info(DCS_PROXY_BNS);

        Executors
                .newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> bnsList.parallelStream()
                        .forEach(bnsIpPortCache::refresh),
                        20,
                        30,
                        TimeUnit.SECONDS
                );
    }

    @Nullable
    public static InetSocketAddress getHashedDcsHttpAddress(BaseMessage message) {
        return getHashedAddress(DCS_PROXY_BNS, getKey(message));
    }

    @Nullable
    public static InetSocketAddress getHashedDhHttpAddress(BaseMessage message) {
        return getHashedAddress(DH_BNS, getKey(message));
    }

    @Nullable
    public static InetSocketAddress getHashedDcsTcpAddress(BaseMessage message, boolean isAsr) {
        InetSocketAddress address = getHashedAddress(DCS_PROXY_BNS, getKey(message));
        if (address == null) {
            return null;
        }
        int initPort = address.getPort();
        InetAddress initAddress = address.getAddress();
        return new InetSocketAddress(initAddress, initPort + (isAsr ? ASR_PORT_OFFSET : EVENT_PORT_OFFSET));
    }

    @Nullable
    private static InetSocketAddress getHashedAddress(String bns, String key) {
        try {
            List<String> ipPorts = getIpPorts(bns);

            HashCode hashCode = Hashing.murmur3_128().newHasher().putString(key, Charsets.UTF_8).hash();
            String ipPort = ipPorts.get(Hashing.consistentHash(hashCode, ipPorts.size()));
            String[] items = ipPort.split(Pattern.quote(SPLITTER_COLON));
            if (items.length >= 2) {
                log.debug("key:{} address:{}", key, Arrays.toString(items));
                return new InetSocketAddress(InetAddress.getByName(items[0]), Integer.valueOf(items[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
        return null;
    }

    public static InetSocketAddress getRandomTtsProxyAddress() {
        return getRandomInetAddress(TTS_PROXY_BNS);
    }

    public static InetSocketAddress getRandomDProxyAddress() {
        return getRandomInetAddress(DPROXY_BNS);
    }

    public static InetSocketAddress getRandomDiAddress() {
        return getRandomInetAddress(DI_BNS);
    }

    static InetSocketAddress getRandomInetAddress(String bns) {
        try {
            List<String> ipPorts = getIpPorts(bns);

            String ipPort = ipPorts.get(new Random().nextInt(ipPorts.size()));
            String[] items = ipPort.split(Pattern.quote(SPLITTER_COLON));
            if (items.length >= 2) {
                log.debug(Arrays.toString(items));
                return new InetSocketAddress(InetAddress.getByName(items[0]), Integer.valueOf(items[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static List<String> getIpPorts(String bns) {
        if (StringUtils.isEmpty(bns)) {
            emptySupplier.get();
        }
        List<String> ipPorts;
        try {
            ipPorts = bnsIpPortCache.get(bns);
        } catch (ExecutionException e) {
            ipPorts = new ArrayList<>();
        }

        if (ipPorts.size() < 1) {
            String backup = null;if (DPROXY_BNS.equalsIgnoreCase(bns) && StringUtils.hasText(DPROXY_API)) {
                backup = DPROXY_API;
            } else if (DCS_PROXY_BNS.equalsIgnoreCase(bns) && StringUtils.hasText(DCS_PROXY_API)) {
                backup = DCS_PROXY_API;
            } else if (TTS_PROXY_BNS.equalsIgnoreCase(bns) && StringUtils.hasText(TTS_PROXY_API)){
                backup = TTS_PROXY_API;
            } else if (DH_BNS.equalsIgnoreCase(bns) && StringUtils.hasText(DH_API)){
                backup = DH_API;
            } else if (DI_BNS.equalsIgnoreCase(bns) && StringUtils.hasText(DI_API)){
                backup = DI_API;
            }
            if (StringUtils.hasText(backup)) {
                ipPorts.add(backup);
                bnsIpPortCache.put(bns, ipPorts);
            }
        }
        return ipPorts;
    }

    private static String getKey(BaseMessage message) {
        return String.format("%s_%s_%s", message.getDeviceId(), message.getCltId(), message.getSn());
    }

    private static final class BnsCacheLoader extends CacheLoader<String, List<String>> {
        private final static String GET_IP_BY_BNS = "get_instance_by_service %s -ip";
        private final static String HOST_ADDRESS = "%s:%s";

        @Override
        @ParametersAreNonnullByDefault
        public List<String> load(@NotNull String s) throws Exception {
            return runInstance(s);
        }

        private List<String> runInstance(String bns) {
            Runtime run = Runtime.getRuntime();
            Process process = null;
            if (StringUtils.isEmpty(bns)) {
                log.warn("bns empty: {}", bns);
                return emptySupplier.get();
            }
            String cmd = String.format(GET_IP_BY_BNS, bns);
            log.debug("cmd={}", cmd);
            try {
                process = run.exec(cmd);
                InputStream in = process.getInputStream();
                List<String> result = handleCmdData(in, bns);
                in.close();
                return result;
            } catch (Exception e) {
                log.warn("exec bns command error e={}", e.getMessage());
                log.warn("exec bns command warn", e);
            } finally {
                if (null != process) {
                    process.destroy();
                }
            }
            return emptySupplier.get();
        }

        private List<String> handleCmdData(InputStream in, String key) {
            List<String> temp = new ArrayList<>();
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line = br.readLine();
                while (StringUtils.hasText(line)) {
                    String[] r = line.split(SPLITTER_SPACE);
                    if (r.length == 3) {
                        temp.add(String.format(HOST_ADDRESS, r[1], r[2]));
                    }
                    line = br.readLine();
                }
                if (temp.size() > 0) {
                    for (int i = 0; i < temp.size(); i++) {
                        log.debug("get bns={}, [{}]host={}", key, i, temp.get(i));
                    }
                }
                return temp;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return temp;
        }
    }
}



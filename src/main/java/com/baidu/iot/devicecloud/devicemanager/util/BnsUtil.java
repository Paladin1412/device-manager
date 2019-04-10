package com.baidu.iot.devicecloud.devicemanager.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class BnsUtil {
    private final static String GET_IP_BY_BNS = "get_instance_by_service %s -ip";
    private final static String HOST_ADDRESS = "%s:%s";
    private final static String JPAAS_CLUSTER_NAME = "JPAAS_CLUSTER_NAME";
    private final static Map<String, List<String>> bnsMap = new HashMap<>();
    private final static Map<String, String> bnsName = new HashMap<>();
    private final static Object lock = new Object();

    private final static Map<String, String> clusterIdcMap = new HashMap<String, String>() {
        {
            put("dva00", "bjyz");
            put("dva01", "nj03");
            put("dva02", "gzns");
        }
    };

    @Value("${bns.address.sleep.time:10000}")
    private long loopSleep;

    private static List<String> handleCmdData(InputStream in, String key) {
        List<String> temp = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = br.readLine();
            while (StringUtils.hasText(line)) {
                String[] r = line.split(" ");
                if (r.length == 3) {
                    temp.add(String.format(HOST_ADDRESS, r[1], r[2]));
                }
                line = br.readLine();
            }
            if (temp.size() > 0) {
                bnsMap.put(key, temp);
                for (int i = 0; i < temp.size(); i++) {
                    log.info("get bns={}, [{}]host={}", key, i, temp.get(i));
                }
            }
            return temp;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return temp;
    }

    private static List<String> runInstance(String bns) {
        Runtime run = Runtime.getRuntime();
        Process process = null;
        if (StringUtils.isEmpty(bns)) {
            log.warn("bns empty: {}", bns);
            return null;
        }
        String cmd = String.format(GET_IP_BY_BNS, bns);
//        log.info("cmd={}", cmd);
        try {
            process = run.exec(cmd);
            InputStream in = process.getInputStream();
            List<String> result = handleCmdData(in, bns);
            in.close();
            if (result.size() > 0) {
                bnsName.put(bns, bns);
//                return randomAddress(result);
            }
            return result;
        } catch (Exception e) {
            log.warn("exec bns command error e={}", e.getMessage());
            log.warn("exec bns command warn", e);
        } finally {
            if (null != process) {
                process.destroy();
            }
        }
        return null;
    }

    private static String randomAddress(List<String> addressList) {
        int index = new Random().nextInt(addressList.size());
        String ip = addressList.get(index);
        log.info("randomAddress: select [{}]host={}", index, ip);
        return ip;
    }

    @Nullable
    private static String getBNSAddress(String bns) {

        if (StringUtils.isEmpty(bns)) {
            return null;
        }

        String ip = null;
        List<String> addressList = getBNSAddressList(bns);
        if (null != addressList && addressList.size() > 0) {
            ip = randomAddress(addressList);
        }
        return ip;
    }

    private static List<String> getBNSAddressList(String bns) {

        if (StringUtils.isEmpty(bns)) {
            return null;
        }

        // example:
        // from: group.smartbns-from_product=dumi-internal-access@group.flow-common-nginx.dumi.all:main
        // to:   group.smartbns-from_product=dumi-internal-access,from_idc=bjyz@group.flow-common-nginx.dumi.all
        Map<String, String> env = System.getenv();
        if (env.containsKey(JPAAS_CLUSTER_NAME)) {
            String clusterName = env.get(JPAAS_CLUSTER_NAME);
            for (Map.Entry<String, String> clusterIdc : clusterIdcMap.entrySet()) {
                String cluster = clusterIdc.getKey();
                if (clusterName.contains(cluster)) {
                    bns = bns.replace("@group", ",from_idc=" + clusterIdc.getValue() + "@group");
                }
            }
        }

        List<String> addressList;
        synchronized (lock) {
            addressList = bnsMap.get(bns);
            if (null == addressList || addressList.size() < 1) {
                log.info("first get bns address,key={}", bns);
                addressList = runInstance(bns);
//                log.debug("addressList = {}", addressList);
            }
        }

        // TODO: temp for test
//        if (addressList != null && "group.iot-paas-qa.IOT.all:vs".equals(bns)) {
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//            addressList.add("127.0.0.1:7890");
//        }
        return addressList;
    }

    public static String getBNSOrUrl(String schemePrefix, String bns, String url) {

        String bnsIp = null;
        if (!StringUtils.isEmpty(bns)) {
            synchronized (lock) {
                bnsIp = getBNSAddress(bns);
            }
        }

        if (null == bnsIp && null != url) {
            bnsIp = url;
        } else {
            bnsIp = schemePrefix + bnsIp;
        }
        return bnsIp;
    }


//    @PostConstruct
//    private void init() {
//        runner();
//    }
//
//    private void runner() {
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        for (String key : bnsName.keySet()) {
//                            runInstance(key);
//                        }
//                    } catch (Exception e) {
//                        log.warn("exec bns command error e={}", e);
//                    }
//
//                    ThreadUtils.sleep(loopSleep);
//                }
//            }
//
//        });
//        thread.setDaemon(true);
//        thread.start();
//    }

    @Scheduled(cron = "0/20 * * * * *")
    public void refreshBns() {
        log.debug("refreshBns");
        try {
            for (String key : bnsName.keySet()) {
                runInstance(key);
            }
        } catch (Exception e) {
            log.warn("exec bns command error e={}", e);
        }
    }
}

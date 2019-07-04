package com.baidu.iot.devicecloud.devicemanager.config;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.GREY_PEPPA_TEST_DEVICE;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/7/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class GreyConfiguration implements InitializingBean {
    private final static String GET_CONF_BY_BNS = "get_instance_by_service %s -c";
    @Getter
    private static JsonNode conf = JsonUtil.createObjectNode();

    @Value("${grey.conf.interval:30}")
    public Long refreshInterval;
    @Value("${grey.conf.bns_group:}")
    private String bns;

    @Override
    public void afterPropertiesSet() {
        log.debug("greyConfBns: {}", bns);
        Executors
                .newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> refreshConf(bns),
                        0,
                        refreshInterval,
                        TimeUnit.SECONDS
                );
    }

    private static void refreshConf(String bns) {
        if (StringUtils.isEmpty(bns) || StringUtils.startsWithIgnoreCase(bns, "unknown")) {
            return;
        }

        Runtime run = Runtime.getRuntime();
        Process process = null;

        String cmd = String.format(GET_CONF_BY_BNS, bns);
        try {
            process = run.exec(cmd);
            InputStream in = process.getInputStream();
            JsonNode result = JsonUtil.readTree(in);
            in.close();

            if (result != null && !result.isNull()) {
                conf = result;
            }
        } catch (Exception e) {
            log.error("refreshConf error=" + e.getMessage(), e);
        } finally {
            if (null != process) {
                process.destroy();
            }
        }
    }

    public static boolean checkIfTestDevice(String cuid) {
        JsonNode testDevices = get(GREY_PEPPA_TEST_DEVICE);

        if (testDevices == null || testDevices.isNull() || testDevices.isMissingNode()) {
            return false;
        }

        if (isAll(testDevices)) {
            return true;
        }

        Set<String> deviceIds = StringUtils.commaDelimitedListToSet(testDevices.asText());

        return deviceIds.contains(cuid);
    }

    private static boolean isAll(JsonNode node) {
        return "ALL".equalsIgnoreCase(node.asText(""));
    }

    @SuppressWarnings("SameParameterValue")
    private static JsonNode get(String key) {
        return conf.path(key);
    }
}

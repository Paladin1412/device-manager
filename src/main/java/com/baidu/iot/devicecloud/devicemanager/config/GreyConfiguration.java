package com.baidu.iot.devicecloud.devicemanager.config;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.GREY_CONF;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.GREY_CONF_USE_OFFLINE_PIPELET;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_UNDERSCORE;

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
    private static JsonNode conf = new ObjectMapper().createObjectNode();

    @Value("${grey.conf.interval:30}")
    public Long refreshInterval;
    @Value("${grey.conf.bns_group:}")
    private String bns;

    @Override
    public void afterPropertiesSet() {
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
                log.debug("Read grey configuration:\n{}", result);
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

    public static boolean checkGreyConf(String cuid) {
        try {
            JsonNode testDevices = get(GREY_CONF_USE_OFFLINE_PIPELET);

            if (testDevices == null || testDevices.isNull() || testDevices.isMissingNode()) {
                return false;
            }

            if (isAll(testDevices)) {
                return true;
            }

            String project = cuid.substring(0, 4);
            String hex = cuid.substring(4);
            Long seq = Long.valueOf(hex, 16);

            Set<String> devices = StringUtils.commaDelimitedListToSet(testDevices.asText());
            for (String s : devices) {
                String[] items = StringUtils.delimitedListToStringArray(s, SPLITTER_UNDERSCORE);
                if (items.length != 3) {
                    log.debug("Grey info conf error:{}", s);
                    continue;
                }
                String greyPid = items[0];
                String greyBegin = items[1];
                String greyEnd   = items[2];
                // Not the current project id
                if (!greyPid.equalsIgnoreCase(project)) {
                    continue;
                }
                // All case
                if (greyBegin.equalsIgnoreCase("ALL")
                        && greyEnd.equalsIgnoreCase("ALL")) {
                    return  true;
                }

                // Turn uuid to long
                Long begin = Long.valueOf(greyBegin, 16);
                Long end   = Long.valueOf(greyEnd, 16);
                if (seq >= begin && seq <= end) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Checking if {} is peppa device failed", cuid, e);
        }

        return false;
    }

    private static boolean isAll(JsonNode node) {
        return "ALL".equalsIgnoreCase(node.asText(""));
    }

    @SuppressWarnings("SameParameterValue")
    private static JsonNode get(String key) {
        return conf.path(GREY_CONF).path(key);
    }
}

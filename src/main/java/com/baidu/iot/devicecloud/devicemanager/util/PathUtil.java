package com.baidu.iot.devicecloud.devicemanager.util;

import org.springframework.util.StringUtils;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_URL;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/19.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class PathUtil {

    public static String lookAfterSuffix(String path) {
        if (!StringUtils.endsWithIgnoreCase(path, SPLITTER_URL)) {
            return path + SPLITTER_URL;
        }
        return path;
    }

    public static String dropOffSuffix(String path, String suffix) {
        if (StringUtils.isEmpty(path) || StringUtils.isEmpty(suffix)) {
            return path;
        }

        if (StringUtils.endsWithIgnoreCase(path, suffix)) {
            return path.substring(0, path.length() - suffix.length());
        }
        return path;
    }

    public static String lookAfterPrefix(String path) {
        if (!StringUtils.startsWithIgnoreCase(path, SPLITTER_URL)) {
            return SPLITTER_URL + path;
        }
        return path;
    }

    public static String dropOffPrefix(String path, String prefix) {
        if (StringUtils.isEmpty(path) || StringUtils.isEmpty(prefix)) {
            return path;
        }

        if (StringUtils.startsWithIgnoreCase(path, prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }
}

package com.baidu.iot.devicecloud.devicemanager.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/22.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class IdGenerator {
    /**
     * The initial timestamp.
     */
    private static final AtomicInteger INITIAL_ID = new AtomicInteger(
            new Random().nextInt(1 << 10)
    );

    public static int nextId() {
        return INITIAL_ID.incrementAndGet();
    }

    public static int projectId(String cuid) {
        if (StringUtils.hasText(cuid) && cuid.length() > 4) {
            try {
                return Integer.parseInt(cuid.substring(0, 4), 16);
            } catch (NumberFormatException e) {
                log.error("Decoding project id from cuid({}) has failed", cuid);
            }
        }
        return -1;
    }
}

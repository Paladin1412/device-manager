package com.baidu.iot.devicecloud.devicemanager.cache;

import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
public class GuavaCacheConfig {
    private String name;
    private int initialCapacity;
    private long maximumSize;
    private int concurrencyLevel;
    private long ttl;
    private TimeUnit ttlUnit;
}

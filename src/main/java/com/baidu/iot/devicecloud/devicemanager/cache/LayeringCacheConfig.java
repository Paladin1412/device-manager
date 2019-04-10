package com.baidu.iot.devicecloud.devicemanager.cache;

import lombok.Data;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
public class LayeringCacheConfig {
    private String name;
    private GuavaCacheConfig guavaCacheConfig;
    private DproxyCacheConfig dproxyCacheConfig;
}

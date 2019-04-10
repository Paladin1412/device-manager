package com.baidu.iot.devicecloud.devicemanager.cache;

import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/5.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
public class DproxyCacheConfig {
    private String name;
    private String prefix;
}

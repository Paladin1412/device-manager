package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bigpipe写配置文件
 *
 * @author Zheng Xiaoxiong (zhengxiaoxiong@baidu.com)
 */
@Data
@Component
class BigpipeConfig {

    @Value("${bigpipe.zookeeper.url}")
    private String zooKeeperUrl;

    @Value("${bigpipe.zookeeper.timeout:2000}")
    private String zooKeeperTimeout;

    @Value("${bigpipe.clustername}")
    private String clusterName;

    @Value("${bigpipe.username}")
    private String username;

    @Value("${bigpipe.password}")
    private String password;

    @Value("${bigpipe.pipename}")
    private String pipeName;

    @Value("${bigpipe.msg.pipelet.map}")
    private String msgPipeletMap;

    @Value("${bigpipe.client.number.per.pipe}")
    private String clientNumberPerPipe;


    // Below is config about queue
    @Value("${bigpipe.queue.token:token}")
    private String queueToken;

    // The max window size is 64
    @Value("${bigpipe.queue.windowsize:64}")
    private String queueWindowSize;

    @Value("${bigpipe.queue.timeout:5000}")
    private String queueTimeout;

    // The max window size is 64, so the max batch size is 64 too.
    @Value("${bigpipe.queue.runner.batch.size:64}")
    private String queueRunnerBatchSize;

    @Value("${bigpipe.client.number.per.queue:50}")
    private String clientNumberPerQueue;

    @Value("${bigpipe.runner.max.sleep.millisecond:15000}")
    private int maxSleepMilliSecond;
}

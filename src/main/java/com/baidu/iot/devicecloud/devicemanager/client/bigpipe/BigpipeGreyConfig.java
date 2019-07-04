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
class BigpipeGreyConfig {

    @Value("${grey.bigpipe.zookeeper.url}")
    private String zooKeeperUrl;

    @Value("${grey.bigpipe.zookeeper.timeout:2000}")
    private String zooKeeperTimeout;

    @Value("${grey.bigpipe.clustername}")
    private String clusterName;

    @Value("${grey.bigpipe.username}")
    private String username;

    @Value("${grey.bigpipe.password}")
    private String password;

    @Value("${grey.bigpipe.pipename}")
    private String pipeName;

    @Value("${grey.bigpipe.msg.pipelet.map}")
    private String msgPipeletMap;

    @Value("${grey.bigpipe.client.number.per.pipe}")
    private String clientNumberPerPipe;


    // Below is config about queue
    @Value("${grey.bigpipe.queue.token:token}")
    private String queueToken;

    // The max window size is 64
    @Value("${grey.bigpipe.queue.windowsize:64}")
    private String queueWindowSize;

    @Value("${grey.bigpipe.queue.timeout:5000}")
    private String queueTimeout;

    // The max window size is 64, so the max batch size is 64 too.
    @Value("${grey.bigpipe.queue.runner.batch.size:64}")
    private String queueRunnerBatchSize;

    @Value("${grey.bigpipe.client.number.per.queue:50}")
    private String clientNumberPerQueue;

    @Value("${grey.bigpipe.runner.max.sleep.millisecond:15000}")
    private int maxSleepMilliSecond;
}

package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.baidu.bigpipe.meta.ZooKeeperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * The provider of bigpipe client. {@link BigpipeClientProvider}.
 *
 * @author Shen Dayu (shendayu@baidu.com)
 **/
@Slf4j
@Component("bigpipeClientGreyProvider")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class BigpipeClientProviderGreyImpl implements BigpipeClientProvider {

    private volatile boolean initialized;

    private final BigpipeGreyConfig config;

    public BigpipeQueueClient getBigpipeQueueClient(String queue) throws BigpipeException {
        if (!initialized) {
            initialize();
        }
        return new BigpipeQueueClientGreyImpl(config, queue);
    }

    public BigpipeWriterClient getBigpipeWriterClient(String queue) throws BigpipeException {
        if (!initialized) {
            initialize();
        }
        return new BigpipeWriterClientGreyImpl(config, queue);
    }

    @PreDestroy
    public void destroy() {
        if (!initialized) {
            return;
        }

        try {
            ZooKeeperUtil.getInstance(config.getClusterName()).close();
        } catch (InterruptedException e) {
            log.error("Error while close zookeeper.", e);
        }
    }

    private void initialize() throws BigpipeException {
        synchronized (BigpipeClientProviderGreyImpl.class) {
            if (initialized) {
                return;
            }
            initialZookeeper();
            initialized = true;
        }
    }

    private void initialZookeeper() throws BigpipeException {
        try {
            ZooKeeperUtil.init(config.getClusterName(), config.getZooKeeperUrl(),
                    Integer.parseInt(config.getZooKeeperTimeout()));
        } catch (IOException e) {
            log.warn("Zookeeper cannot connect.", e);
            throw new BigpipeException("Zookeeper cannot connect.", e);
        }
    }
}

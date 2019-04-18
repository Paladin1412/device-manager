package com.baidu.iot.devicecloud.devicemanager.config.localserver;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Data
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TcpRelayServerConfig {
    @Value("${dm.tcp.port:8988}")
    public int dmTcpPort;

    @Value("${dm.tcp.timeout.idle.read:10}")
    public int dmTcpTimeoutIdle;
}

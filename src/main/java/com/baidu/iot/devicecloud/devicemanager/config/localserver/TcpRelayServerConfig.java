package com.baidu.iot.devicecloud.devicemanager.config.localserver;

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
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TcpRelayServerConfig {
    public static int DM_TCP_PORT;
    public static int DM_TCP_TIMEOUT_IDLE_READ;
    public static String DM_TCP_VERSION;
    public static int DM_TCP_NEED_AUTH;
    public static int DM_TCP_RESOURCE_THREADS;
    public static int DM_TCP_TLS_ENABLE;
    public static int DM_TCP_DEBUG;
    public static int DM_TCP_DEBUG_PORT;

    @Value("${dm.tcp.port:8988}")
    public void setDmTcpPort(int dmTcpPort) {
        DM_TCP_PORT = dmTcpPort;
    }

    @Value("${dm.tcp.timeout.idle.read:10}")
    public void setDmTcpTimeoutIdle(int dmTcpTimeoutIdle) {
        DM_TCP_TIMEOUT_IDLE_READ = dmTcpTimeoutIdle;
    }

    @Value("${dm.tcp.version:v1}")
    public void setDmTcpVersion(String dmTcpVersion) {
        DM_TCP_VERSION = dmTcpVersion;
    }

    @Value("${dm.tcp.need.auth:0}")
    public void setDmTcpNeedAuth(int dmTcpNeedAuth) {
        DM_TCP_NEED_AUTH = dmTcpNeedAuth;
    }

    @Value("${dm.tcp.resource.threads:500}")
    public void setDmTcpResourceThreads(int dmTcpResourceThreads) {
        DM_TCP_RESOURCE_THREADS = dmTcpResourceThreads;
    }

    @Value("${dm.tcp.tls.enable:1}")
    public void setDmTcpTlsEnable(int dmTcpTlsEnable) {
        DM_TCP_TLS_ENABLE = dmTcpTlsEnable;
    }

    @Value("${dm.tcp.debug:0}")
    public void setDmTcpDebug(int dmTcpDebug) {
        DM_TCP_DEBUG = dmTcpDebug;
    }

    @Value("${dm.tcp.debug.port:8987}")
    public void setDmTcpDebugPort(int dmTcpDebugPort) {
        DM_TCP_DEBUG_PORT = dmTcpDebugPort;
    }
}

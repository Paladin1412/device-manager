package com.baidu.iot.devicecloud.devicemanager.bean;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/21.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class LocalServerInfo implements InitializingBean {

    @Getter
    @Value("${local.network.interface:lo}")
    private String tcpInterface;

    @Getter
    @Value("${local.server.bns:}")
    private String localServerBns;

    public static int localServerPort;

    @Getter
    private String localServerIp;

    private static String getIpByInterface(String networkInterfaceName) throws SocketException, RuntimeException {
        NetworkInterface networkInterface = NetworkInterface.getByName(networkInterfaceName);
        if (networkInterface == null) {
            throw new RuntimeException("Network interface does not exist. interface=" + networkInterfaceName);
        }
        List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();

        for (InterfaceAddress address : interfaceAddresses) {
            if (address.getAddress() instanceof Inet4Address) {
                // Use IPv4
                return address.getAddress().getHostAddress();
            }
        }

        throw new RuntimeException("No valid IP address for specified network interface.");
    }

    private String autoChooseEthernet() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            log.info("Physical network adaptor list:");
            while (nis.hasMoreElements()) {
                NetworkInterface nif = nis.nextElement();
                log.info("interface: {}", nif);
                if (nif.isLoopback()) {
                    continue;
                }
                if (nif.isUp() && !nif.isVirtual() && StringUtils.hasText(new String(nif.getHardwareAddress()))) {
                    return nif.getName();
                }
            }
        } catch (SocketException ignore) {
        }
        return null;
    }

    @Override
    public String toString() {
        if (StringUtils.hasText(localServerBns)) {
            return localServerBns;
        }

        return String.format("%s:%d", localServerIp, localServerPort);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String tryNif = autoChooseEthernet();
        tcpInterface = StringUtils.hasText(tryNif) ? tryNif : tcpInterface;
        String ip = getIpByInterface(tcpInterface);
        log.info("Choose network interface={} ip={}", tcpInterface, ip);
        this.localServerIp = ip;
    }
}

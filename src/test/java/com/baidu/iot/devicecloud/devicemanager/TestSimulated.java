package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.client.tcp.TlvOverTcpClient;
import com.baidu.iot.devicecloud.devicemanager.server.SimulatedDCSProxy;
import com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer;
import com.baidu.iot.devicecloud.devicemanager.service.AccessTokenService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.InetSocketAddress;

import static com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant.TYPE_UPSTREAM_ASR;
import static com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant.TYPE_UPSTREAM_FINISH;
import static com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant.TYPE_UPSTREAM_INIT;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.demo;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@SpringBootTest
public class TestSimulated {

    @Test
    public void test() {
        SimulatedDCSProxy dcsProxy = new SimulatedDCSProxy();

//        dcsProxy.start();

        TlvOverTcpClient tcpClient = new TlvOverTcpClient(new InetSocketAddress("localhost", 8980));
        tcpClient.startClient();
        tcpClient.writeMessage(demo(TYPE_UPSTREAM_INIT, "{\"param\":\"{\\\"device-id\\\":\\\"3F1806118800C678\\\"}\",\"pid\":\"-1001\",\"clientip\":\"xxx.xxx.xxx.xxx\",\"sn\":\"91598059-4d06-447a-878f-551637bcaf89_7\"}"));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        tcpClient.writeMessage(demo(TYPE_UPSTREAM_ASR, "{\"param\":\"{\\\"device-id\\\":\\\"3F1806118800C678\\\"}\",\"pid\":\"-1002\",\"clientip\":\"xxx.xxx.xxx.xxx\",\"sn\":\"91598059-4d06-447a-878f-551637bcaf89_7\"}"));
        tcpClient.writeMessage(demo(TYPE_UPSTREAM_ASR, "{\"TTS\": [{\"text\": \"我爱我的祖国\",\"volume\": \"5\",\"speed\": \"5\",\"xml\": \"1\",\"speaker\": \"100\",\"pitch\": \"5\",\"aue\": \"4\",\"rate\": \"0\",\"content_id\": \"9001231\"},{\"text\": \"姚明是怎么考到驾照的？\",\"volume\": \"5\",\"speed\": \"5\",\"xml\": \"1\",\"speaker\": \"100\",\"pitch\": \"5\",\"aue\": \"4\",\"rate\": \"0\",\"content_id\": \"9001232\"}]}"));
        tcpClient.writeMessage(demo(TYPE_UPSTREAM_FINISH, "{\"param\":\"{\\\"device-id\\\":\\\"3F1806118800C678\\\"}\",\"pid\":\"-1003\",\"clientip\":\"xxx.xxx.xxx.xxx\",\"sn\":\"91598059-4d06-447a-878f-551637bcaf89_7\"}"));
    }
}

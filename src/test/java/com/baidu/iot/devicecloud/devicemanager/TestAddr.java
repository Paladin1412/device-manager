package com.baidu.iot.devicecloud.devicemanager;

import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestAddr {
    @Test
    public void testAddr() {
        String[] items = new String[]{"10.173.51.160", "8260"};
        try {
            InetSocketAddress random = new InetSocketAddress(InetAddress.getByName(items[0]), Integer.valueOf(items[1]));
            System.out.println(random.toString());
            System.out.println(random.getAddress().getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}

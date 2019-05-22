package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.service.SecurityService;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class DeviceManagerApplicationTests {
	@Autowired
	private TtsService ttsService;

	@Autowired
	private SecurityService securityService;

	@Test
	public void contextLoads() {
		try {
			InetSocketAddress random = AddressCache.cache.get(AddressCache.getDcsAddressKey("0285000000001c"));
			System.out.println(random.toString());
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testAddr() {
		String[] items = new String[]{"10.173.51.160", "8260"};
		try {
			InetSocketAddress random = new InetSocketAddress(InetAddress.getByAddress(items[0].getBytes()), Integer.valueOf(items[1]));
			System.out.println(random.toString());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testRequestTTSSync() {
		String tts = "{\"TTS\": [{\"text\": \"我爱我的祖国\",\"volume\": \"5\",\"speed\": \"5\",\"xml\": \"1\",\"speaker\": \"100\",\"pitch\": \"5\",\"aue\": \"3\",\"rate\": \"0\",\"content_id\": \"9001231\"},{\"text\": \"姚明是怎么考到驾照的？\",\"volume\": \"5\",\"speed\": \"5\",\"xml\": \"1\",\"speaker\": \"100\",\"pitch\": \"5\",\"aue\": \"3\",\"rate\": \"0\",\"content_id\": \"9001232\"}]}";
		byte[] bytes = tts.getBytes();
		TlvMessage message = new TlvMessage(TlvConstant.TYPE_DOWNSTREAM_TTS, bytes.length, bytes);
		TtsRequest ttsRequest = new TtsRequest();
		ttsRequest.setData(message.getValue());
		ttsRequest.setSn("9009");
		ttsRequest.setCuid("0285000000001c");
		System.out.println(ttsService.requestTTSSync(ttsRequest, false, null));
	}

	@Test
	public void testRequestPreTTSSync() {
		String pretts = "{\"PRE_TTS\": [{\"text\": \"今天天气怎么样？\",\"volume\": \"6\",\"speed\": \"5\",\"xml\": \"1\",\"speaker\": \"0\",\"pitch\": \"5\",\"aue\": \"3\",\"rate\": \"0\",\"content_id\": \"9001231\"},{\"text\": \"我先处理一些事情！\",\"volume\": \"5\",\"speed\": \"5\",\"xml\": \"1\",\"speaker\": \"100\",\"pitch\": \"5\",\"aue\": \"3\",\"rate\": \"0\",\"content_id\": \"9001232\"}]}";
		byte[] bytes = pretts.getBytes();
		TlvMessage message = new TlvMessage(TlvConstant.TYPE_DOWNSTREAM_TTS, bytes.length, bytes);
		TtsRequest ttsRequest = new TtsRequest();
		ttsRequest.setData(message.getValue());
		ttsRequest.setSn("9009");
		ttsRequest.setCuid("0285000000001c");
		ttsService.requestTTSSync(ttsRequest, true, null);
	}

	@Test
	public void testDecrypt() {
		String secretKey = "IP6DVK5fmLgRxN2mNGbZafEWBIpvIk9PfUnEdMxivunifDMTB272MaX7DC1T4zh50-EfU_gBeHzFMw0Db94icluNXfXsyLXk";
		String[] items = securityService.decryptSecretKey(secretKey);
        Assert.assertNotNull(items);
		Assert.assertEquals(5, items.length);
        System.out.println(Arrays.toString(items));
    }
}

package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean.TtsRequest;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.service.SecurityService;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

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

	private String otaEvents = "2019-06-11 17:50:12 {\"description\":null,\"event\":4,\"percent\":0.201099,\"transaction\":\"1472892\"}\n2019-06-11 17:50:11 {\"description\":null,\"event\":4,\"percent\":0.10189,\"transaction\":\"1472892\"}\n2019-06-11 17:50:08 {\"description\":\"Creater Updater success\",\"event\":0,\"transaction\":\"1472892\"}";

	private ArrayNode orderedEvents(String otaEvents) {
		String[] eventItems = StringUtils.delimitedListToStringArray(otaEvents, "\n");
		ArrayNode result = JsonUtil.createArrayNode();
		Stream.of(eventItems)
				.map(e -> {
					String[] items = e.split(" ", 3);
					if (items.length > 2) {
						System.out.println(items[2]);
						return JsonUtil.readTree(items[2]);
					}
					return JsonUtil.createObjectNode();
				})
				.sorted((j1, j2) -> {
					double diff = j1.path("event").asInt(0) - j2.path("event").asInt(0) + (j1.path("percent").asDouble(0) - j2.path("percent").asDouble(0));
					if (diff < 0) {
						return -1;
					}
					if (diff > 0) {
						return 1;
					}
					return 0;
				})
				.forEach(result::add);
		return result;
	}

	@Test
	public void test() {
		ArrayNode arrayNode = orderedEvents(otaEvents);
		System.out.println(arrayNode);
	}
}

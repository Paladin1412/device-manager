package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceBaseMessage;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Sender
 *
 * @author Long Yunxiang (longyunxiang@baidu.com)
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class Sender {

    private final BigpipeWriterManager bigpipeWriterManager;

    private final Logger infoLog = LoggerFactory.getLogger("infoLog");

    private List<SenderMsg> sendmsgCache = Collections.synchronizedList(new ArrayList<SenderMsg>());

    private static final int overtime = 60 * 5;  // 单位秒

    public void send(String channel, String message, String logId) {

        if (bigpipeWriterManager.sendMessage(channel, message)) {
            infoLog.info(String.format("Send bigpipe message successs, logId=%s, msg=%s",
                    logId, message));
        } else {
            infoLog.info(String.format("Send bigpipe message error, logId=%s, msg=%s",
                    logId, message));
            putSenderMsg(channel, message);
        }
    }

    public void cleanSendmsgCache() {
        log.debug("begin to sender message size : {}", sendmsgCache.size());
        Iterator<SenderMsg> iterator = sendmsgCache.iterator();
        while (iterator.hasNext()) {
            SenderMsg senderMsg = iterator.next();
            if (System.currentTimeMillis() - senderMsg.getCrateTime() >= overtime * 1000) {
                iterator.remove();
                continue;
            }
            if (bigpipeWriterManager.sendMessage(senderMsg.getChannel(), senderMsg.getMessage())) {
                iterator.remove();
            }
        }
    }

    private void putSenderMsg(String channel, String message) {
        SenderMsg senderMsg = new SenderMsg();
        senderMsg.setChannel(channel);
        senderMsg.setMessage(message);
        senderMsg.setCrateTime(System.currentTimeMillis());
        sendmsgCache.add(senderMsg);
    }

    public void send(SenderChannelType channelType, DeviceBaseMessage message, String logId) {
        String messageStr = JsonUtil.serialize(message);
        send(channelType.toString(), messageStr, logId);
    }

    public void send(SenderChannelType channelType, String message, String logId) {
        send(channelType.toString(), message, logId);
    }
}

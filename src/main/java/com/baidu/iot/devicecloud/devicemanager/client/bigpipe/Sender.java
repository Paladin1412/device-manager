package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import com.baidu.iot.devicecloud.devicemanager.bean.device.DeviceBaseMessage;
import com.baidu.iot.devicecloud.devicemanager.config.GreyConfiguration;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private final BigpipeWriterGreyManager bigpipeWriterGreyManager;

    private final Logger infoLog = LoggerFactory.getLogger("infoLog");

    private List<SenderMsg> senderCache = Collections.synchronizedList(new ArrayList<>());
    private List<SenderMsg> greySenderCache = Collections.synchronizedList(new ArrayList<>());

    private static final int overtime = 60 * 5;  // 单位秒

    private void send(String channel, String message, String logId) {
        if (bigpipeWriterManager.sendMessage(channel, message)) {
            infoLog.info(String.format("Send bigpipe message successs, logId=%s, msg=%s",
                    logId, message));
        } else {
            infoLog.info(String.format("Send bigpipe message error, logId=%s, msg=%s",
                    logId, message));
            putSenderMsg(channel, message, false);
        }
    }

    private void sendGrey(String channel, String message, String logId) {
        if (bigpipeWriterGreyManager.sendMessage(channel, message)) {
            infoLog.info(String.format("Send bigpipe message successs, logId=%s, msg=%s",
                    logId, message));
        } else {
            infoLog.info(String.format("Send bigpipe message error, logId=%s, msg=%s",
                    logId, message));
            putSenderMsg(channel, message, true);
        }
    }

    void cleanSenderCache(boolean isGrey) {
        List<SenderMsg> target;
        if (isGrey) {
            target = greySenderCache;
        } else {
            target = senderCache;
        }
        log.debug("begin to sender message size : {}", target.size());
        Iterator<SenderMsg> iterator = target.iterator();
        while (iterator.hasNext()) {
            SenderMsg senderMsg = iterator.next();
            if (System.currentTimeMillis() - senderMsg.getCrateTime() >= overtime * 1000) {
                iterator.remove();
                continue;
            }
            if ((isGrey &&
                    bigpipeWriterGreyManager.sendMessage(senderMsg.getChannel(), senderMsg.getMessage())) ||
                    bigpipeWriterManager.sendMessage(senderMsg.getChannel(), senderMsg.getMessage())){
                iterator.remove();
            }
        }
    }

    private void putSenderMsg(String channel, String message, boolean isGrey) {
        SenderMsg senderMsg = new SenderMsg();
        senderMsg.setChannel(channel);
        senderMsg.setMessage(message);
        senderMsg.setCrateTime(System.currentTimeMillis());
        if (isGrey) {
            greySenderCache.add(senderMsg);
        } else {
            senderCache.add(senderMsg);
        }
    }

    public void send(SenderChannelType channelType, DeviceBaseMessage message, String logId) {
        String messageStr = JsonUtil.serialize(message);
        String cuid = message.getDeviceUuid();
        if (StringUtils.hasText(cuid) && GreyConfiguration.checkIfTestDevice(cuid)) {
            log.debug("Send to the grey configured bigpipe");
            sendGrey(channelType.toString(), messageStr, logId);
        } else {
            send(channelType.toString(), messageStr, logId);
        }
    }

    public void send(SenderChannelType channelType, String message, String logId) {
        send(channelType.toString(), message, logId);
    }
}

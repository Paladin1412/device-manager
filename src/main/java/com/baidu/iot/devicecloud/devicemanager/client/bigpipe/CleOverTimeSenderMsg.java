package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CleOverTimeSenderMsg {
    private final Sender sender;

    @Autowired
    public CleOverTimeSenderMsg(Sender sender) {
        this.sender = sender;
    }

    @Scheduled(cron = "30 */1 * * * *")
    public void clean() {
        try {
            sender.cleanSenderCache(true);
            sender.cleanSenderCache(false);
        } catch (Exception e) {
            log.error("Clean sender message data error!", e);
        }
    }

}



package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.LocalServerInfo;
import com.baidu.iot.devicecloud.devicemanager.util.Encryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/31.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Service
public class SecurityService {
    private static final String ERROR = "error";
    private static final String SPLITTER = "\001";

    private final LocalServerInfo localServerInfo;

    @Autowired
    public SecurityService(LocalServerInfo localServerInfo) {
        this.localServerInfo = localServerInfo;
    }

    public String nextSecretKey(String cuid) {

        String uniqueId = UUID.randomUUID().toString();
        String key = StringUtils.arrayToDelimitedString(
                new String[]{
                        uniqueId,
                        cuid,
                        localServerInfo.getLocalServerIp(),
                        Integer.toString(LocalServerInfo.localServerPort),
                        Long.toString(System.currentTimeMillis(), 16)
                },
                SPLITTER
        );
        Encryptor encryptor = Encryptor.EncryptHelper.getInstance();
        try {
            return encryptor.encrypt(key);
        } catch (Exception e) {
            log.error("Encryptor has accounted something wrong, regard the secret key as {}. cuid:{}",
                    ERROR, cuid);
            log.error("The stack traces listed below", e);
            return ERROR;
        }
    }

    public String[] decryptSecretKey(String secretKey) {
        String[] items = new String[]{};
        Encryptor encryptor = Encryptor.EncryptHelper.getInstance();
        try {
            return StringUtils.tokenizeToStringArray(encryptor.decrypt(secretKey), SPLITTER);
        } catch (Exception e) {
            log.error("Something goes wrong when decrypting the secret key: {}", secretKey);
            log.error("The stack traces listed below", e);
        }
        return items;
    }
}

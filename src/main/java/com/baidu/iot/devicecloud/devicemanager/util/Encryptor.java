package com.baidu.iot.devicecloud.devicemanager.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.security.SecureRandom;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/30.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class Encryptor {
    private SecretKey secretKey ;
    private SecureRandom random;
    private Cipher cipher;

    private Encryptor() {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("des");
            String key = "A key from dumi shanghai iot device manager";
            DESKeySpec keySpec = new DESKeySpec(key.getBytes());
            secretKey = keyFactory.generateSecret(keySpec);
            random = new SecureRandom();
            cipher = Cipher.getInstance("des");
        } catch (Exception e) {
            log.error("DesEncrypt init error", e);
        }
    }

    public String encrypt(String plainData) throws Exception {
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, random);
        byte[] cipherData = cipher.doFinal(plainData.getBytes());
        return Base64.encodeBase64URLSafeString(cipherData);
    }

    public String decrypt(String encryptedData) throws Exception {
        byte[] data = Base64.decodeBase64(encryptedData.getBytes());

        cipher.init(Cipher.DECRYPT_MODE, secretKey, random);
        byte[] plainData = cipher.doFinal(data);
        return new String(plainData);
    }

    public static class EncryptHelper {
        private static final Encryptor INSTANCE = new Encryptor();

        public static Encryptor getInstance() {
            return INSTANCE;
        }
    }
}

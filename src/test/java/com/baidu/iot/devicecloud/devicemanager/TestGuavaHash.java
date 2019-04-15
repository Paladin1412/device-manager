package com.baidu.iot.devicecloud.devicemanager;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.junit.Test;
import sun.security.provider.MD5;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/4/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestGuavaHash {
    private List<String> list = Arrays.asList("one", "two", "three", "four");

    @Test
    public void testConsistHash() {
        String key = "02850000002806_1$02850000002805$10.211.245.46$8300$1024_1$02850000002805$10.211.245.46$8300$1024_93_197568504099_10.211.245.46_8300_7017_1555315135.010873";
        HashCode code = Hashing.murmur3_128().newHasher().putString(key, Charsets.UTF_8).hash();
        System.out.println("code: " + code);
        System.out.println(list.get(Hashing.consistentHash(code, list.size())));
    }
}

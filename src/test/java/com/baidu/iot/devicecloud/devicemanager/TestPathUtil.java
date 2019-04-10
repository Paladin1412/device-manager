package com.baidu.iot.devicecloud.devicemanager;

import com.baidu.iot.devicecloud.devicemanager.util.PathUtil;
import org.junit.Test;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/28.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class TestPathUtil {
    @Test
    public void testDropOffPrefix() {
        System.out.println(PathUtil.dropOffPrefix("duer_event", "/"));
    }
}

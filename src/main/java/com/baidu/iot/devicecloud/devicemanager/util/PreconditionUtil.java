package com.baidu.iot.devicecloud.devicemanager.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.web.server.ServerWebInputException;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/18.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class PreconditionUtil {
    public static void checkInput(boolean expression, @Nullable Object errorMessage) {
        if (!expression) {
            throw new ServerWebInputException(String.valueOf(errorMessage));
        }
    }
}

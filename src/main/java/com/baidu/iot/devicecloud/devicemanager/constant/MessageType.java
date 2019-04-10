package com.baidu.iot.devicecloud.devicemanager.constant;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/4.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class MessageType {
    public static final int BASE = 0;
    public static final int AUTHORIZATION = 1;     // SYS_CONNECTED
    public static final int SYS_DISCONNECTED = 2;
    public static final int HEARTBEAT = 3;
    public static final int DATA_POINT = 4;
    public static final int PUSH_MESSAGE = 5;
    public static final int SYS_EXCEPTION = 6;
    public static final int ADVICE = 7;
}

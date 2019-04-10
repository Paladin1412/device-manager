package com.baidu.iot.devicecloud.devicemanager.bean;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/16.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class DataPointMessage extends BaseMessage {
    private int version;
    private int id;
    private int code;
    private String path;
    private String query;
    private String payload;
    private String misc;
}

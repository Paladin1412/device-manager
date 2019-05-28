package com.baidu.iot.devicecloud.devicemanager.client.http.ttsproxyclient.bean;

import com.fasterxml.jackson.databind.node.BinaryNode;
import lombok.Data;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/31.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
public class TtsRequest {
    private String cuid;
    private String sn;
    private String contentId;
    private String key;
    private BinaryNode data;
    private int messageType;
}

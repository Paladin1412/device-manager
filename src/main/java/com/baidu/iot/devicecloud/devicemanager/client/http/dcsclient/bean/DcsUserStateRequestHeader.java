package com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean;

import lombok.Data;
import lombok.ToString;
import org.springframework.http.MediaType;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
public class DcsUserStateRequestHeader {
    private String pid;
    private String userAgent;
    private String cltId;
    private String authorization;
    private String standbyDeviceId;
    private String bns;
    private String sn;
    private String streamingVersion;
    private String contentType = MediaType.APPLICATION_JSON_VALUE;
    private String duerosDeviceId;
    private String firmwareVersion;
    private String logId;
    private boolean isListenStartedEvent = false;
}

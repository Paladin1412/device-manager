package com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.builder;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.client.http.dcsclient.bean.DcsUserStateRequestHeader;
import okhttp3.internal.annotations.EverythingIsNonNull;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/21.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class DcsUserStateRequestHeaderBuilder {
    @EverythingIsNonNull
    public static DcsUserStateRequestHeader buildFrom(final BaseMessage baseMessage,
                                                      final String accessToken) {
        DcsUserStateRequestHeader header = new DcsUserStateRequestHeader();
        header.setAuthorization(accessToken);
        header.setBns(baseMessage.getBns());
        header.setCltId(baseMessage.getCltId());
        header.setContentType(baseMessage.getContentType());
        header.setDuerosDeviceId(baseMessage.getDeviceId());
        header.setLogId(baseMessage.getLogId());
        header.setPid(baseMessage.getProductId());
        header.setStandbyDeviceId(baseMessage.getStandbyDeviceId());
        header.setSn(baseMessage.getSn());
        header.setStreamingVersion(baseMessage.getStreamingVersion());
        header.setUserAgent(baseMessage.getUserAgent());
        return header;
    }
}

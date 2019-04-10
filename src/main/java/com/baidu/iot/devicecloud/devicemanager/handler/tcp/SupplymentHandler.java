package com.baidu.iot.devicecloud.devicemanager.handler.tcp;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * Only used by the outbound to DCS proxy, for supplying some required data.
 *
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/14.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class SupplymentHandler extends MessageToMessageEncoder<TlvMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, TlvMessage msg, List<Object> out) throws Exception {
        if (msg == null) {
            return;
        }
        out.add(msg);

        if (msg.getType() != TlvConstant.TYPE_UPSTREAM_INIT) {
            return;
        }

        makeUp(msg);
    }

    private void makeUp(TlvMessage msg) {
        JsonNode value = msg.getValue();

    }
}

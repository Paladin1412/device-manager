package com.baidu.iot.devicecloud.devicemanager.codec;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.fasterxml.jackson.databind.node.BinaryNode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.parseIntTo2BytesInOrder;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/7.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class TlvEncoder extends MessageToByteEncoder<TlvMessage> {
    private final String name;
    private final ByteOrder byteOrder;

    public TlvEncoder(String name) {
        this(name, ByteOrder.LITTLE_ENDIAN);
    }

    private TlvEncoder(String name, ByteOrder byteOrder) {
        super();
        this.name = name;
        this.byteOrder = byteOrder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, TlvMessage msg, ByteBuf out) throws Exception {
        log.debug("-----------------This is {}'s encoder, byte order is {}", name, byteOrder);
        int type = msg.getType();
        log.debug("Encoding type: {}({})", type, String.format("0x%04X", type));
        int intLen = Math.toIntExact(msg.getLength());
        BinaryNode valueBin = msg.getValue();
        if (valueBin == null || intLen != valueBin.binaryValue().length) {
            return;
        }
        byte[] typeBytes = parseIntTo2BytesInOrder(type, byteOrder);
        out.writeBytes(ByteBuffer.allocate(2).order(byteOrder).put(typeBytes).compact());
        out.writeBytes(ByteBuffer.allocate(4).order(byteOrder).putInt(intLen).compact());
        out.writeBytes(ByteBuffer.allocate(intLen).order(byteOrder).put(valueBin.binaryValue()).compact());
    }
}

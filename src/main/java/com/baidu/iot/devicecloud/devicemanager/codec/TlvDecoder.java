package com.baidu.iot.devicecloud.devicemanager.codec;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isLegalLength;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isLegalType;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/6.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class TlvDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            if (notEnoughData(in)) {
                return;
            }

            in.markReaderIndex();

            int type = in.readUnsignedShortLE();

            if (!isLegalType(type)) {
                closeConnection(ctx);
                return;
            }
            log.debug("TlvDecoder read type: {}({})", type, String.format("0x%04X", type));

            long len = in.readUnsignedIntLE();

            if (!isLegalLength(len)) {
                closeConnection(ctx);
                return;
            }
            log.debug("TlvDecoder read length: {}", len);

            if (in.readableBytes() < len) {
                log.debug("Wait for more data from:{}", ctx.channel().remoteAddress().toString());
                in.resetReaderIndex();
                return;
            } else {
                byte[] data;
                try {
                    data = new byte[Math.toIntExact(len)];
                } catch (ArithmeticException e) {
                    log.error("There is too much data to read. {}", len);
                    return;
                }
                in.readBytes(data);
                log.debug("TlvDecoder read data success:\n{}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(data)));

                out.add(new TlvMessage(type, data.length, data));
            }
        }
    }

    private boolean notEnoughData(ByteBuf in) {
        return in.readableBytes() < 6;
    }

    private void closeConnection(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(this);
        ctx.channel().close();
    }
}

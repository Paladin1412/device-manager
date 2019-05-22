package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CONFIRMATION_STATE;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.prettyLogString;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class NettyUtil {
    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        log.debug("Closing the channel: {}", ch);
        if (ch != null && ch.isOpen()) {
            ch.flush().close();
        }
    }

    public static void writeAndFlush(Channel channel, Object msg) {
        if (channel != null && channel.isOpen()) {
            channel
                    .writeAndFlush(msg)
                    .addListeners((ChannelFutureListener) future -> {
                        if (!future.isSuccess()) {
                            log.error("Writing and flushing message {} to channel {} has failed",
                                    msg,
                                    channel,
                                    future.cause());
                        }

                        String msgLog;
                        if (msg instanceof TlvMessage) {
                            msgLog = prettyLogString.apply((TlvMessage) msg);
                        } else {
                            msgLog = String.valueOf(msg);
                        }
                        log.debug("Writing and flushing message \n{}\nto {} successfully.", msgLog, channel);
                    }, ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            log.warn("Connection {} has been reset by peer", channel);
        }
    }

    public static byte[] parseIntTo2BytesInOrder(int unsignedShort, ByteOrder byteOrder) {
        byte[] parsed;
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            parsed = new byte[]{(byte)unsignedShort, (byte)(unsignedShort >> 8)};
        } else {
            parsed = new byte[]{(byte)(unsignedShort >> 8), (byte)unsignedShort};
        }
        return parsed;
    }

    public static <T> Supplier<Consumer<T>> good2Go(Channel channel) {
        return () -> (T t) -> {
            if (channel.hasAttr(CONFIRMATION_STATE)
                    && ConfirmationStates.CONFIRMED == channel.attr(CONFIRMATION_STATE).get()) {
                writeAndFlush(channel, t);
            } else {
                log.debug("{} has not been confirmed, the message {} being discarded.", channel.toString(), t);
            }
        };
    }
}

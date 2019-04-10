package com.baidu.iot.devicecloud.devicemanager.util;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CONFIRMATION_STATE;

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
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static void writeAndFlush(Channel channel, Object msg) {
        if (channel != null && channel.isActive()) {
            channel
                    .writeAndFlush(msg)
                    .addListeners((ChannelFutureListener) future -> {
                        if (!future.isSuccess()) {
                            log.error("Writing and flushing message {} to channel {} has failed: {}",
                                    String.valueOf(msg),
                                    String.valueOf(channel),
                                    future.cause());
                            future.cause().printStackTrace();
                        }
                        log.debug("Writing and flushing message {} to {} successfully.", String.valueOf(msg), String.valueOf(channel));
                    }, ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
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
                log.debug("{} has already been confirmed", channel.toString());
                writeAndFlush(channel, t);
            } else {
                log.debug("{} has not been confirmed, the message {} being discarded.", channel.toString(), String.valueOf(t));
            }
        };
    }

    public static Predicate<TlvMessage> isDirectiveTlv = (TlvMessage tlv) -> tlv.getType() == TlvConstant.TYPE_DOWNSTREAM_DUMI;
    public static Predicate<TlvMessage> isTTSTlv = (TlvMessage tlv) -> tlv.getType() == TlvConstant.TYPE_DOWNSTREAM_TTS;
    public static Predicate<TlvMessage> isPreTTSTlv = (TlvMessage tlv) -> tlv.getType() == TlvConstant.TYPE_DOWNSTREAM_PRE_TTS;
}
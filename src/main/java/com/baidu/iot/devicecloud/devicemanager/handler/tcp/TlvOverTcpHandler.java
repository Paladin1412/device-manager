package com.baidu.iot.devicecloud.devicemanager.handler.tcp;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class TlvOverTcpHandler extends SimpleChannelInboundHandler<TlvMessage> {
    private ChannelHandlerContext ctx;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TlvMessage msg) throws Exception {
        log.info("Simulated relay client has received a message: {}", msg.toString());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("A connection to the relay server has been made: {}", ctx.channel().toString());
        this.ctx = ctx;
        ctx.fireChannelActive();
    }

    public void sendMessage(TlvMessage message) {
        if (ctx != null) {
            log.debug("Simulated relay client is sending a message to the relay server: {}", ctx.channel().toString());
            ChannelFuture channelFuture = ctx.channel().write(message);
            ctx.flush();

            channelFuture.addListeners((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    log.error("Sending message has failed: {}", channelFuture.cause());

                }
            });
        } else {
            log.error("ChannelHandlerContext hasn't been initialized.");
        }
    }
}
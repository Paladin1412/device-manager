package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.util.NettyUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.UnicastProcessor;

import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CONFIRMATION_STATE;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.closeOnFlush;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.confirmedConnection;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isDownstreamFinishPackage;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isDownstreamInitPackage;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class RelayBackendHandler extends SimpleChannelInboundHandler<TlvMessage> {
    private final UnicastProcessor<TlvMessage> requestQueue;
    private final UnicastProcessor<TlvMessage> responseQueue;

    /**
     * Whether the connected servers, like dcs proxy, respond the initial package with type {@link TlvConstant#TYPE_DOWNSTREAM_INIT},
     * and the status is supposed to be {@code 0}.
     */
    private boolean initialPackageHasArrived;

    RelayBackendHandler(UnicastProcessor<TlvMessage> requestQueue,
                        UnicastProcessor<TlvMessage> responseQueue) {
        // auto release the received data
        super();

        this.requestQueue = requestQueue;
        this.responseQueue = responseQueue;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TlvMessage msg) {
        Channel upstreamChannel = ctx.channel();
        log.debug("The event-link relay server inner channel {} has read a message:\n{}",
                upstreamChannel, msg);
        if (isDownstreamInitPackage(msg)) {
            initialPackageHasArrived = true;
            if (confirmedConnection(msg)) {
                log.debug("{} has been confirmed by dcs. Subscribing to event requests", upstreamChannel.toString());
                upstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMED);
                requestQueue.subscribe(NettyUtil.good2Go(upstreamChannel).get());
            } else {
                upstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.EXCEPTION);
            }

            // expecting next message
            return;
        }

        // remote server has confirmed the connection
        // do something with the received msg, like requesting tts

        if (initialPackageHasArrived) {
            responseQueue.onNext(msg);

            if (isDownstreamFinishPackage(msg)) {
                log.debug("The downstream finish package(0xF005) has come, completing the work queue");
                responseQueue.onComplete();
                // closing this tcp connection to dcs
                closeOnFlush(upstreamChannel);
            }

            // don't print the last log
            return;
        }

        log.debug("{} ignored everything arrived before the downstream init package.", upstreamChannel.toString());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("The event-link tcp proxy client has connected to DCS: {}", ctx.channel().toString());
        ctx.channel().attr(CONFIRMATION_STATE).set(ConfirmationStates.UNCONFIRMED);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("The dcs has logged out.");
        clear();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        log.error("Caught an exception on the event-link dcs channel({})", channel);
        log.error("The stack traces listed below", cause);
        clear();
        closeOnFlush(channel);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            log.debug("[{}] Upstream idled, closing the connection.", e.state());
            ctx.close();
        }
        super.userEventTriggered(ctx, evt);
    }

    private void clear() {
        if (requestQueue != null && !requestQueue.isTerminated()) {
            requestQueue.onComplete();
            requestQueue.clear();
        }
        if (responseQueue != null && !responseQueue.isTerminated()) {
            responseQueue.onComplete();
            responseQueue.clear();
        }
    }
}

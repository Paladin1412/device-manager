package com.baidu.iot.devicecloud.devicemanager.handler.tcp;

import com.baidu.iot.devicecloud.devicemanager.adapter.Adapter;
import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.processor.DirectiveProcessor;
import com.baidu.iot.devicecloud.devicemanager.util.NettyUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CONFIRMATION_STATE;
import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CUID;
import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.SN;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.closeOnFlush;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.writeAndFlush;
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
    private final Channel downstreamChannel;
    private final UnicastProcessor<TlvMessage> downstreamWorkQueue;
    private final UnicastProcessor<TlvMessage> workQueue;
    private final DirectiveProcessor directiveProcessor;

    private String cuid = null;
    private String sn = null;

    /**
     * Whether the connected servers, like dcs proxy, respond the initial package with type {@link TlvConstant#TYPE_DOWNSTREAM_INIT},
     * and the status is supposed to be {@code 0}.
     */
    private boolean initialPackageHasArrived;

    RelayBackendHandler(Channel downstreamChannel,
                        UnicastProcessor<TlvMessage> downstreamWorkQueue,
                        DirectiveProcessor directiveProcessor) {
        // auto release the received data
        super();

        this.downstreamChannel = downstreamChannel;
        this.downstreamWorkQueue = downstreamWorkQueue;
        this.directiveProcessor = directiveProcessor;
        this.workQueue =
                UnicastProcessor.create(Queues.<TlvMessage>xs().get());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TlvMessage msg) throws Exception {
        Channel upstreamChannel = ctx.channel();
        log.debug("Relay server inner channel {} has read a message: {}", upstreamChannel.toString(), msg.toString());
        if (isDownstreamInitPackage(msg)) {
            initialPackageHasArrived = true;
            if (confirmedConnection(msg)) {
                upstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMED);
                log.debug("Subscribing to dcs");
                downstreamWorkQueue.subscribe(NettyUtil.good2Go(upstreamChannel).<TlvMessage>get());

                downstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMED);
                log.debug("{} has been confirmed by dcs. Subscribing to asr", upstreamChannel.toString());
                workQueue.collectList()
                        .flatMapMany(list -> Flux.fromStream(
                                Adapter.directive2DataPointTLV(
                                        directiveProcessor.process(this.cuid, this.sn, list),
                                        TlvConstant.TYPE_DOWNSTREAM_DUMI
                                ).stream()
                        ))
                        .subscribe(NettyUtil.good2Go(downstreamChannel).<TlvMessage>get());

                this.cuid = upstreamChannel.attr(CUID).get();
                this.sn = upstreamChannel.attr(SN).get();
            } else {
                upstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.EXCEPTION);
                downstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.EXCEPTION);
            }

            // received a correct initial response from dcs, responding to the connected asr
            writeAndFlush(downstreamChannel, msg);

            // expecting next message
            return;
        }

        // remote server has confirmed the connection
        // do something with the received msg, like requesting tts

        if (initialPackageHasArrived) {
            workQueue.onNext(msg);

            if (isDownstreamFinishPackage(msg)) {
                log.debug("The downstream finish package(0xF004) has come, {}, completing the work queue",
                        msg.toString());
                workQueue.onComplete();
            }
            return;
        }

        log.debug("{} ignored everything arrived before the downstream init package.", upstreamChannel.toString());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Connected {} to DCS: {}", downstreamChannel.toString(), ctx.channel().toString());
        ctx.channel().attr(CONFIRMATION_STATE).set(ConfirmationStates.UNCONFIRMED);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("The dcs has logged out.");
        workQueue.clear();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        workQueue.clear();
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                log.debug("Upstream's reader idled, closing the connection.");
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}

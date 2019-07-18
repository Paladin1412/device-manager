package com.baidu.iot.devicecloud.devicemanager.handler.tcp;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.config.localserver.TcpRelayServerConfig;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.processor.DirectiveProcessor;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.NettyUtil;
import com.baidu.iot.devicecloud.devicemanager.util.TlvUtil;
import com.baidu.iot.log.Log;
import com.baidu.iot.log.LogProvider;
import com.baidu.iot.log.Stopwatch;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.LOGTIME_FORMAT;
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
    private static final Logger infoLog = LoggerFactory.getLogger("infoLog");

    private final Channel downstreamChannel;
    private final UnicastProcessor<TlvMessage> downstreamWorkQueue;
    private final UnicastProcessor<TlvMessage> workQueue;
    private final DirectiveProcessor processor;
    private Log spanLog;
    private SimpleDateFormat sdf;
    private Stopwatch stopwatch;

    private String cuid = null;
    private String sn = null;

    /**
     * Whether the connected servers, like dcs proxy, respond the initial package with type {@link TlvConstant#TYPE_DOWNSTREAM_INIT},
     * and the status is supposed to be {@code 0}.
     */
    private boolean initialPackageHasArrived;

    RelayBackendHandler(Channel downstreamChannel,
                        UnicastProcessor<TlvMessage> downstreamWorkQueue,
                        TtsService ttsService,
                        TcpRelayServerConfig config) {
        // auto release the received data
        super();

        this.downstreamChannel = downstreamChannel;
        this.downstreamWorkQueue = downstreamWorkQueue;
        this.processor = new DirectiveProcessor(ttsService);
        this.sdf = new SimpleDateFormat(LOGTIME_FORMAT);
        this.sdf.setTimeZone(TimeZone.getTimeZone(config.getDmTimezone()));
        this.workQueue =
                UnicastProcessor.create(Queues.<TlvMessage>xs().get());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TlvMessage msg) {
        Channel upstreamChannel = ctx.channel();
        log.debug("The asr-link relay server inner channel {} has read a message:\n{}",
                upstreamChannel, msg);
        if (isDownstreamInitPackage(msg)) {
            initialPackageHasArrived = true;
            this.cuid = upstreamChannel.attr(CUID).get();
            this.sn = upstreamChannel.attr(SN).get();
            this.spanLog = LogProvider.getInstance().get(this.sn);
            this.stopwatch = this.spanLog.time("dcs");
            this.spanLog.count("dcs");
            infoLog.info("[ASR] Dcs started to response");
            infoLog.info("[ASR] The asr-link relay server inner channel {} has read a message:{}", upstreamChannel, msg);
            if (confirmedConnection(msg)) {
                upstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMED);
                log.debug("{} has been confirmed by dcs. Subscribing to dcs", upstreamChannel.toString());
                downstreamWorkQueue.subscribe(NettyUtil.good2Go(upstreamChannel).get());

                downstreamChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMED);
                log.debug("{} has been confirmed by dm. Subscribing to asr", upstreamChannel.toString());

                workQueue
                        .publishOn(Schedulers.single())
                        .groupBy(TlvUtil::isDownstreamFinishPackage)
                        .flatMapSequential(group -> {
                            if (Boolean.valueOf(String.valueOf(group.key()))) {
                                return group.flatMap(Mono::just);
                            } else {
                                // input 0xF002, 0xF003, 0xF004 or 0xF006
                                // output TlvMessage
                                return group
                                        .groupBy(TlvUtil::isAsrPackage)
                                        .flatMapSequential(subGroup -> {
                                            if (Boolean.valueOf(String.valueOf(subGroup.key()))) {
                                                return subGroup.flatMap(Mono::just);
                                            } else {
                                                // input 0xF003, 0xF004 or 0xF006
                                                // output TlvMessage
                                                return processor.processAsr(this.cuid, this.sn, subGroup);
                                            }
                                        });
                            }
                        })
                        .elapsed()
                        .flatMap(t -> {
                            log.debug("Elapsed time: {}ms", t.getT1());
                            return Mono.just(t.getT2());
                        })
                        .doFinally(signalType -> {
                            spanLog.append("query_end", sdf.format(new Date()));
                            infoLog.info(spanLog.format("[ASR] Dm finished to response."));
                        })
                        .subscribe(NettyUtil.good2Go(downstreamChannel).get());
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
            spanLog.count("dcs");
            infoLog.info("[ASR] The asr-link relay server inner channel {} has read a message:{}", upstreamChannel, msg);

            if (isDownstreamFinishPackage(msg)) {
                log.debug("The downstream finish package(0xF004) has come, completing the work queue");
                workQueue.onComplete();
                this.stopwatch.pause();
                infoLog.info(spanLog.format("[ASR] Dcs finished to response."));
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
        clearWorkQueue();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        log.error("Caught an exception on the asr-link dcs channel({})", channel);
        log.error("The stack traces listed below", cause);
        clearWorkQueue();
        closeOnFlush(channel);
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

    private void clearWorkQueue() {
        if (workQueue != null && !workQueue.isDisposed()) {
            workQueue.onComplete();
            workQueue.clear();
        }
    }
}

package com.baidu.iot.devicecloud.devicemanager.handler.tcp;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvDecoder;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvEncoder;
import com.baidu.iot.devicecloud.devicemanager.config.localserver.TcpRelayServerConfig;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.service.AccessTokenService;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.log.Log;
import com.baidu.iot.log.LogProvider;
import com.baidu.iot.log.Stopwatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.LOG_DATETIME_FORMAT;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_BEARER;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_DUEROS_DEVICE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_PARAM;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.JSON_KEY_SN;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_AUTHORIZATION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_LINK_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CONFIRMATION_STATE;
import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CUID;
import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.SN;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.closeOnFlush;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.writeAndFlush;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isUpstreamFinishPackage;
import static com.baidu.iot.devicecloud.devicemanager.util.TlvUtil.isUpstreamInitPackage;

/**
 * Getting responsible for the relay client to DCS proxy.
 * <p>
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class RelayFrontendHandler extends SimpleChannelInboundHandler<TlvMessage> {
    private static final Logger infoLog = LoggerFactory.getLogger("infoLog");
    private static final LogProvider logProvider = LogProvider.getInstance();
    /**
     * Whether the connected clients, like asr, report the initial package with type {@link TlvConstant#TYPE_UPSTREAM_INIT}
     */
    private boolean initialPackageHasArrived;
    private Channel outboundChannel;

    private final AccessTokenService accessTokenService;
    private final TtsService ttsService;
    private final InetSocketAddress assignedAsrAddress;
    private final TcpRelayServerConfig config;

    private final UnicastProcessor<TlvMessage> workQueue;

    private String cuid = null;
    private String sn = null;

    private Log spanLog = null;
    private Stopwatch stopwatch = null;
    private SimpleDateFormat sdf;

    public RelayFrontendHandler(AccessTokenService accessTokenService,
                                TtsService ttsService,
                                InetSocketAddress assignedAsrAddress,
                                TcpRelayServerConfig config) {
        super();
        this.accessTokenService = accessTokenService;
        this.ttsService = ttsService;
        this.assignedAsrAddress = assignedAsrAddress;
        this.config = config;

        // here shouldn't be so many messages, so use xs().
        this.workQueue =
                UnicastProcessor.create(Queues.<TlvMessage>xs().get());


        this.sdf = new SimpleDateFormat(LOG_DATETIME_FORMAT);
        this.sdf.setTimeZone(TimeZone.getTimeZone(config.getDmTimezone()));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("{} has connected to the relay server.", ctx.channel().toString());
        ctx.channel().attr(CONFIRMATION_STATE).set(ConfirmationStates.UNCONFIRMED);
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TlvMessage msg) {
        final Channel inboundChannel = ctx.channel();
        log.debug("The asr-link relay channel {} has read a message:\n{}", inboundChannel, msg);

        // server is expecting the first initial package: 0x0001
        // everything arrived before the first initial package will be ignored
        if (!initialPackageHasArrived && isUpstreamInitPackage(msg)) {
            initialPackageHasArrived = true;

            // 0x0001 showed up for the first time, initialize a new connection to DCS proxy
            // change the channel's confirmation state
            inboundChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMING);

            JsonNode valueNode = decodeAsJson(msg);
            JsonNode paramNode = readParam(valueNode.path(JSON_KEY_PARAM));
            if (paramNode != null) {
                // cuid and sn wouldn't be empty
                this.cuid = paramNode.path(JSON_KEY_DUEROS_DEVICE_ID).asText();
                this.sn = valueNode.path(JSON_KEY_SN).asText();
            }

            spanLog = logProvider.get(sn);
            stopwatch = spanLog.time("asr");
            spanLog.setCuId(cuid);
            infoLog.info("[ASR] Asr started");
            spanLog.append("query_start", this.sdf.format(new Date()));
            spanLog.count("asr");
            infoLog.info("[ASR] The asr-link relay channel {} has read a message:{}", inboundChannel, msg);

            // Initializing a new connection to DCS proxy for each client connected to the relay server til the client has report the first initial package
            Bootstrap dcsProxyClient = new Bootstrap();
            dcsProxyClient
                    .group(inboundChannel.eventLoop())      // share the outer channel's eventLoop with the inner
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // Inbounds start from below
                                    .addLast("tlvDecoder", new TlvDecoder())
                                    .addLast("idleStateHandler", new IdleStateHandler(config.dmTcpTimeoutIdle, config.dmTcpTimeoutIdle, 0))
                                    .addLast(new RelayBackendHandler(inboundChannel, workQueue, ttsService, config))
                                    // Inbounds stop at above

                                    // Outbounds stop at below
                                    .addLast("tlvEncoder", new TlvEncoder("dcsProxyClient"))
                                    // Outbounds start from above
                            ;
                        }
                    });

            // Connect to dcs after received the init message
            log.debug("The relay server is connecting {} to dcs.", inboundChannel.toString());
            InetSocketAddress assigned;
            if (assignedAsrAddress != null) {
                assigned = assignedAsrAddress;
            } else {
                assigned = AddressCache.getDcsTcpAddress(cuid, true);
            }
            if (assigned == null) {
                log.error("Couldn't find any dcs address");
                return;
            }

            ChannelFuture channelFuture = dcsProxyClient.connect(assigned);
            outboundChannel = channelFuture.channel();
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    if (StringUtils.hasText(cuid)) {
                        decorate(msg, valueNode);
                    }
                    writeAndFlush(outboundChannel, msg);
                    outboundChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMING);
                    tagChannel(outboundChannel);
                }
            });

            // or expecting next message
            return;
        }

        // all messages arrived after 0x0001 will enqueue
        if (initialPackageHasArrived) {
            log.debug("The upstream init package(0x0001) has already showed up, the message would join the work queue");
            workQueue.onNext(msg);
            spanLog.count("asr");
            infoLog.info("[ASR] The asr-link relay channel {} has read a message:{}", inboundChannel, msg);

            if (isUpstreamFinishPackage(msg)) {
                log.debug("The upstream finish package(0x0004) has come, completing the work queue");
                workQueue.onComplete();
                stopwatch.pause();
                spanLog.time("after_asr");
                infoLog.info(spanLog.format("[ASR] Asr finished."));
            }
            return;
        }

        log.debug("{} ignored everything arrived before the upstream init package.", ctx.channel().toString());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("The asr has logged out.");
        clearWorkQueue();
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
        if (this.spanLog != null) {
            this.spanLog.remove();
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        log.error("Caught an exception on the asr-link channel({})", channel, cause);
        log.error("The stack traces listed below", cause);
        clearWorkQueue();
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

    @NonNull
    private JsonNode decodeAsJson(TlvMessage tlv) {
        if (tlv != null) {
            BinaryNode valueBin = tlv.getValue();
            if (valueBin == null) {
                log.error("No value found");
                return NullNode.getInstance();
            }
            return JsonUtil.readTree(valueBin.binaryValue());
        }
        return NullNode.getInstance();
    }

    /**
     * Decorate access token for the initial package
     *
     * @param tlv the message would be fixed
     */
    private void decorate(TlvMessage tlv, JsonNode jsonNode) {
        ObjectNode valueNode = (ObjectNode) jsonNode;
        JsonNode param = valueNode.path(JSON_KEY_PARAM);
        if (param.isNull()) {
            log.error("No 'param' field");
            return;
        }

        ObjectNode paramNode = readParam(param);

        if (paramNode == null) {
            log.error("No param value found");
            return;
        }

        try {
            // supply access token
            String accessToken = accessTokenService.getAccessToken(cuid, sn);
            log.debug("Decorate access token for the initial package: {}", accessToken);
            // append access token to param
            paramNode.set(PAM_PARAM_AUTHORIZATION, TextNode.valueOf(PARAMETER_BEARER + accessToken));
            if (!paramNode.has(PAM_PARAM_LINK_VERSION)) {
                paramNode.set(PAM_PARAM_LINK_VERSION, IntNode.valueOf(2));
            }
            valueNode.set(JSON_KEY_PARAM, TextNode.valueOf(JsonUtil.serialize(paramNode)));

            BinaryNode valueBinUpdated = BinaryNode.valueOf(JsonUtil.writeAsBytes(valueNode));
            tlv.setValue(valueBinUpdated);

            // recalculate length
            tlv.setLength(valueBinUpdated.binaryValue().length);
        } catch (Exception e) {
            log.error("Decorating the access token for the upstream init package failed", e);
        }
    }

    private ObjectNode readParam(JsonNode param) {
        ObjectNode paramNode = null;
        if (param.isObject()) {
            paramNode = (ObjectNode) param;
        } else if (param.isTextual()) {
            String paramText = param.asText();
//                log.debug("paramText: {}", paramText);
            paramNode = (ObjectNode) JsonUtil.readTree(paramText);
        }
        return paramNode;
    }

    private void tagChannel(Channel channel) {
        if (StringUtils.hasText(cuid)) {
            channel.attr(CUID).set(cuid);
        }
        if (StringUtils.hasText(sn)) {
            channel.attr(SN).set(sn);
        }
    }

    private void clearWorkQueue() {
        if (workQueue != null && !workQueue.isDisposed()) {
            workQueue.onComplete();
            workQueue.clear();
        }
    }
}

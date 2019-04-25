package com.baidu.iot.devicecloud.devicemanager.handler.tcp;

import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.AddressCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenResponse;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvDecoder;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvEncoder;
import com.baidu.iot.devicecloud.devicemanager.config.localserver.TcpRelayServerConfig;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.processor.DirectiveProcessor;
import com.baidu.iot.devicecloud.devicemanager.service.AccessTokenService;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    /**
     * Whether the connected clients, like asr, report the initial package with type {@link TlvConstant#TYPE_UPSTREAM_INIT}
     */
    private boolean initialPackageHasArrived;
    private Channel outboundChannel;

    private final AccessTokenService accessTokenService;
    private final DirectiveProcessor directiveProcessor;
    private final InetSocketAddress assignedAsrAddress;
    private final TcpRelayServerConfig config;

    private final UnicastProcessor<TlvMessage> workQueue;

    private Cache<String, String> accessTokenCache;

    private String cuid;
    private String sn;

    public RelayFrontendHandler(AccessTokenService accessTokenService,
                                DirectiveProcessor directiveProcessor,
                                InetSocketAddress assignedAsrAddress,
                                TcpRelayServerConfig config) {
        super();
        this.accessTokenService = accessTokenService;
        this.directiveProcessor = directiveProcessor;
        this.assignedAsrAddress = assignedAsrAddress;
        this.config = config;

        // here shouldn't be so many messages, so use xs().
        this.workQueue =
                UnicastProcessor.create(Queues.<TlvMessage>xs().get());

        accessTokenCache =
                CacheBuilder
                        .newBuilder()
                        .expireAfterWrite(10, TimeUnit.SECONDS)
                        .build();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("{} has connected to the relay server.", ctx.channel().toString());
        ctx.channel().attr(CONFIRMATION_STATE).set(ConfirmationStates.UNCONFIRMED);
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TlvMessage msg) throws Exception {
        log.debug("The asr-link relay server has read a message: {}", String.valueOf(msg));

        // server is expecting the first initial package: 0x0001
        // everything arrived before the first initial package will be ignored
        if (!initialPackageHasArrived && isUpstreamInitPackage(msg)) {
            // 0x0001 showed up for the first time, initialize a new connection to DCS proxy
            final Channel inboundChannel = ctx.channel();
            // change the channel's confirmation state
            inboundChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMING);

            // Initializing a new connection to DCS proxy for each client connected to the relay server til the client has report the first initial package
            Bootstrap dcsProxyClient = new Bootstrap();
            dcsProxyClient
                    .group(inboundChannel.eventLoop())      // share the outer channel's eventLoop with the inner
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    // Inbounds start from below
                                    .addLast("tlvDecoder", new TlvDecoder())
                                    .addLast("idleStateHandler", new IdleStateHandler(config.dmTcpTimeoutIdle, 0, 0))
                                    .addLast(new RelayBackendHandler(inboundChannel, workQueue, directiveProcessor))
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
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    decorate(msg);
                    writeAndFlush(outboundChannel, msg);
                    outboundChannel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMING);
                    tagChannel(outboundChannel);
                }
            });
            outboundChannel = channelFuture.channel();

            initialPackageHasArrived = true;

            // or expecting next message
            return;
        }

        // all messages arrived after 0x0001 will enqueue
        if (initialPackageHasArrived) {
            log.debug("The upstream init package(0x0001) has already showed up, the message would join the work queue");
            workQueue.onNext(msg);

            if (isUpstreamFinishPackage(msg)) {
                log.debug("The upstream finish package(0x0004) has come, completing the work queue");
                workQueue.onComplete();
            }
            return;
        }

        log.debug("{} ignored everything arrived before the upstream init package.", ctx.channel().toString());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("The asr has logged out.");
        workQueue.clear();
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
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
                log.debug("Downstream's reader idled, closing the connection.");
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Obtain access token
     *
     * @param tlv the message would be fixed
     */
    private void decorate(TlvMessage tlv) {
        if (tlv != null) {
            BinaryNode valueBin = tlv.getValue();
            if (valueBin == null) {
                log.error("No value found");
                return;
            }
            ObjectNode valueNode;
            try {
                valueNode = (ObjectNode) JsonUtil.readTree(valueBin.binaryValue());
            } catch (Exception e) {
                log.error("Read value as tree error");
                return;
            }
            if (valueNode == null) {
                return;
            }
            JsonNode param = valueNode.path(JSON_KEY_PARAM);
            if (param.isNull()) {
                log.error("No 'param' field");
                return;
            }
            ObjectNode paramNode = null;
            if (param.isObject()) {
                paramNode = (ObjectNode) param;
            } else if (param.isTextual()) {
                String paramText = param.asText();
                log.debug("paramText: {}", paramText);
                paramNode = (ObjectNode) JsonUtil.readTree(paramText);
            }
            if (paramNode == null) {
                log.error("No param value found");
                return;
            }
            String cuid = paramNode.path(JSON_KEY_DUEROS_DEVICE_ID).asText();
            String sn = valueNode.path(JSON_KEY_SN).asText();
            if (StringUtils.hasText(cuid)) {
                this.cuid = cuid;
                this.sn = sn;
                try {
                    // supply access token
                    String accessToken =
                            accessTokenCache.get(String.format("%s_%s", cuid, sn),
                                    () -> Optional.ofNullable(accessTokenService.try2ObtainAccessToken(cuid, sn))
                                            .orElseGet(AccessTokenResponse::new).getAccessToken());
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
                    e.printStackTrace();
                }
            }
        }
    }

    private void tagChannel(Channel channel) {
        if (StringUtils.hasText(cuid)) {
            channel.attr(CUID).set(cuid);
        }
        if (StringUtils.hasText(sn)) {
            channel.attr(SN).set(sn);
        }
    }
}

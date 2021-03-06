package com.baidu.iot.devicecloud.devicemanager.server;

import com.baidu.iot.devicecloud.devicemanager.codec.TlvDecoder;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvEncoder;
import com.baidu.iot.devicecloud.devicemanager.config.localserver.TcpRelayServerConfig;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.handler.tcp.RelayFrontendHandler;
import com.baidu.iot.devicecloud.devicemanager.service.AccessTokenService;
import com.baidu.iot.devicecloud.devicemanager.service.TtsService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;

/**
 * <pre>
 *  +----------------------+
 *  |    Target server     |
 *  +----------+-----------+
 *            /|\
 *             |  upstream
 *  +----------+-----------+
 *  |  +----------------+  |
 *  |  |  inner client  |  |
 *  |  +----------------+  |
 *  |   TCP RELAY SERVER   |
 *  +----------+-----------+
 *            /|\
 *             |  downstream
 *  +----------+-----------+
 *  |        Clients       |
 *  +----------+-----------+
 * </pre>
 *
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class TcpRelayServer {
    public static final AttributeKey<ConfirmationStates> CONFIRMATION_STATE = AttributeKey.valueOf("confirmed");
    public static final AttributeKey<String> CUID = AttributeKey.valueOf("cuid");
    public static final AttributeKey<String> SN = AttributeKey.valueOf("sn");
    private final AccessTokenService accessTokenService;
    private final TtsService ttsService;
    private final TcpRelayServerConfig config;

    private ChannelFuture channelFuture;

    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;

    @Value("${dcs.proxy.address.asr:}")
    private String dcsProxyAsrAddress;

    @Autowired
    public TcpRelayServer(AccessTokenService accessTokenService,
                          TtsService ttsService,
                          TcpRelayServerConfig config) {
        this.accessTokenService = accessTokenService;
        this.ttsService = ttsService;
        this.config = config;
    }

    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(config.dmTcpPort);
        InetSocketAddress tryDcsProxyAsr = null;
        if (StringUtils.hasText(dcsProxyAsrAddress)) {
            String[] items = dcsProxyAsrAddress.split(Pattern.quote(SPLITTER_COLON));
            try {
                tryDcsProxyAsr = new InetSocketAddress(InetAddress.getByName(items[0]), Integer.valueOf(items[1]));
            } catch (UnknownHostException e) {
                tryDcsProxyAsr = null;
            }
        }
        final InetSocketAddress dcsProxyAsr = tryDcsProxyAsr;

        // Init the relay server
        ServerBootstrap b = new ServerBootstrap();

        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("tlvDecoder", new TlvDecoder())
                                .addLast("idleStateHandler", new IdleStateHandler(config.dmTcpTimeoutIdle, 0, 0))
                                .addLast("relayHandler", new RelayFrontendHandler(accessTokenService, ttsService, dcsProxyAsr, config))

                                .addLast("tlvEncoder", new TlvEncoder("Relay server"));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        try {
            channelFuture = b.bind(inetSocketAddress).sync();
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("The relay server has bounded to {}", inetSocketAddress.getPort());
                } else {
                    log.error("The relay server has failed to bind {}.", inetSocketAddress.getPort());
                    log.error("The stack traces listed below", future.cause());
                }
            });
        } catch (InterruptedException e) {
            log.error("The relay server has been interrupted", e);
        }
    }

    @SuppressWarnings("unused")
    public void stop() {
        if (channelFuture != null) {
            try {
                channelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("Stopping the relay server failed", e);
            }
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}

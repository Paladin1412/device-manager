package com.baidu.iot.devicecloud.devicemanager.service.handler;

import com.baidu.iot.devicecloud.devicemanager.adapter.Adapter;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.TlvMessage;
import com.baidu.iot.devicecloud.devicemanager.cache.BnsCache;
import com.baidu.iot.devicecloud.devicemanager.client.http.deviceiamclient.bean.AccessTokenResponse;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvDecoder;
import com.baidu.iot.devicecloud.devicemanager.codec.TlvEncoder;
import com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.ConfirmationStates;
import com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant;
import com.baidu.iot.devicecloud.devicemanager.constant.TlvConstant;
import com.baidu.iot.devicecloud.devicemanager.processor.DirectiveProcessor;
import com.baidu.iot.devicecloud.devicemanager.service.AccessTokenService;
import com.baidu.iot.devicecloud.devicemanager.service.PushService;
import com.baidu.iot.devicecloud.devicemanager.util.IdGenerator;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT;
import static com.baidu.iot.devicecloud.devicemanager.constant.CoapConstant.COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.HEADER_CONTENT_TYPE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_BEARER;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.PARAMETER_METADATA;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.SPLITTER_COLON;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DATA_POINT_DUER_EVENT;
import static com.baidu.iot.devicecloud.devicemanager.constant.DataPointConstant.DEFAULT_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_AUTHORIZATION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_DUEROS_DEVICE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_LINK_VERSION;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_STANDBY_DEVICE_ID;
import static com.baidu.iot.devicecloud.devicemanager.constant.PamConstant.PAM_PARAM_USER_AGENT;
import static com.baidu.iot.devicecloud.devicemanager.server.TcpRelayServer.CONFIRMATION_STATE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.NettyUtil.writeAndFlush;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/25.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class DuerEventHandler extends AbstractLinkableDataPointHandler {
    private static final String KEY_PATTERN = "%s_%s_%s";
    private static final String RELAY_BACK_HANDLER = "relayBackendHandler";
    private static final byte[] CRLF = {'\r', '\n'};
    private final AccessTokenService accessTokenService;
    private final PushService pushService;
    private final DirectiveProcessor directiveProcessor;

    public Cache<String, Optional<String>> cache;
    private ChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap;
    private ExecutorService commonSideExecutor;

    @Value("${dcs.proxy.address.evt:}")
    private String dcsProxyEvtAddress;

    @Autowired
    public DuerEventHandler(AccessTokenService accessTokenService,
                            PushService pushService,
                            DirectiveProcessor directiveProcessor) {
        this.accessTokenService = accessTokenService;
        this.pushService = pushService;
        this.directiveProcessor = directiveProcessor;

        cache = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofMinutes(3))
                .recordStats()
                .removalListener(
                        (RemovalListener<String, Optional<String>>) n ->
                                log.debug("Removed Access Token: ({}, {}), caused by: {}",
                                        n.getKey(), n.getValue().orElse("null"), n.getCause().toString())
                )
                .build();

        poolMap = new AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
            @Override
            protected SimpleChannelPool newPool(InetSocketAddress key) {
                log.debug("Creating new pool for {}", key.toString());
                return new SimpleChannelPool(
                        new Bootstrap()
                                .group(new NioEventLoopGroup())
                                .channel(NioSocketChannel.class)
                                .remoteAddress(key),
                        new ChannelPoolHandler() {
                            @Override
                            public void channelReleased(Channel ch) throws Exception {
                                log.debug("{} has been released", ch.toString());
                            }

                            @Override
                            public void channelAcquired(Channel ch) throws Exception {
                                log.debug("{} has been acquired", ch.toString());
                            }

                            @Override
                            public void channelCreated(Channel ch) throws Exception {
                                log.debug("{} has been created", ch.toString());
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline
                                        // Inbounds start from below
                                        .addLast("tlvDecoder", new TlvDecoder())
                                        // Inbounds stop at above

                                        // Outbounds stop at below
                                        .addLast("disconnectedHandler", new DisconnectedHandler())
                                        .addLast("tlvEncoder", new TlvEncoder("dcsProxyClient"))
                                        // Outbounds start from above
                                ;
                            }
                        }
                );
            }
        };

        commonSideExecutor = new ThreadPoolExecutor(0, 50,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    @Override
    boolean canHandle(String type) {
        return DATA_POINT_DUER_EVENT.equalsIgnoreCase(type);
    }

    @Override
    Mono<Object> work(DataPointMessage message) {
        if (StringUtils.isEmpty(message.getPayload())) {
            return Mono.just(emptyResponses.apply(message));
        }

        String cuid = message.getDeviceId();
        String pid = message.getProductId();
        String sn = message.getSn();
        String key = String.format(KEY_PATTERN, cuid, pid, sn);
        Optional<String> optAccessToken;
        try {
            optAccessToken = cache.get(key, () -> loadAccessToken(message));
        } catch (Exception e) {
            return Mono.error(e);
        }
        if (!optAccessToken.isPresent()) {
            cache.invalidate(key);
            return Mono.just(unauthorizedResponses.apply(message));
        }

        CompletableFuture<Mono<BaseResponse>> future =
                CompletableFuture.supplyAsync(() -> doWork(message, optAccessToken.get()), commonSideExecutor);
        future.handleAsync(
                (r, t) -> r
                        .timeout(
                                Duration.ofSeconds(5),
                                Mono.just(failedResponses.apply(message.getLogId(), "Processing timeout"))
                        )
                        .subscribe(baseResponse -> {
                            log.debug("{} executing result:{} messageId:{}",
                                    DATA_POINT_DUER_EVENT, baseResponse, message.getId());
                        })
        );
        return Mono.just(successDataPointResponses.get());
    }

    private Mono<BaseResponse> doWork(DataPointMessage message, String accessToken) {
        // Initializing a new connection to DCS proxy for each client connected to the relay server til the client has report the first initial package
        log.debug("The relay server is connecting to dcs.");
        InetSocketAddress assigned;
        if (StringUtils.hasText(dcsProxyEvtAddress)) {
            String[] items = dcsProxyEvtAddress.split(Pattern.quote(SPLITTER_COLON));
            try {
                assigned = new InetSocketAddress(InetAddress.getByName(items[0]), Integer.valueOf(items[1]));
            } catch (UnknownHostException e) {
                assigned = BnsCache.getHashedDcsTcpAddress(message, false);
            }
        } else {
            assigned = BnsCache.getHashedDcsTcpAddress(message, false);
        }
        if (assigned == null) {
            return Mono.error(new IllegalStateException("Couldn't find any dcs address"));
        }
        // all tlv messages those should be sent to dcs
        final UnicastProcessor<TlvMessage> requestQueue = UnicastProcessor.create(Queues.<TlvMessage>xs().get());
        // all tlv messages those received from dcs
        final UnicastProcessor<TlvMessage> responseQueue = UnicastProcessor.create(Queues.<TlvMessage>xs().get());
        final SimpleChannelPool pool = poolMap.get(assigned);
        Future<Channel> f = pool.acquire();
        f.addListener((FutureListener<Channel>) channelFuture -> {
            if (channelFuture.isSuccess()) {
                final Channel channel = channelFuture.getNow();
                ChannelPipeline pipelines = channel.pipeline();
                if (pipelines.get(RELAY_BACK_HANDLER) != null) {
                    pipelines.remove(RELAY_BACK_HANDLER);
                }
                pipelines.addLast(RELAY_BACK_HANDLER, new RelayBackendHandler(requestQueue, responseQueue));
                writeAndFlush(channel, initPackage.apply(message, accessToken));
                channel.attr(CONFIRMATION_STATE).set(ConfirmationStates.CONFIRMING);

                TlvMessage data = dataPackage.apply(message);
                if (data != null) {
                    requestQueue.onNext(data);
                }
                requestQueue.onNext(finishPackage.apply(message));
                requestQueue.onComplete();
                pool.release(channel);
            }
        });

        return Flux.push(fluxSink -> responseQueue.subscribe(
                    new BaseSubscriber<TlvMessage>() {
                        @Override
                        protected void hookOnNext(TlvMessage value) {
                            fluxSink.next(value);
                        }

                        @Override
                        protected void hookOnComplete() {
                            fluxSink.complete();
                        }
                    }
                ))
                .collectList()
                .flatMap(list -> pushService.push(deal(list, message)))
                .doFinally(signalType -> {
                    log.debug("Finally, refreshing the access token, signalType={}", signalType);
                    accessTokenService.refreshAccessToken(message);
                })
                .switchIfEmpty(Mono.defer(() ->Mono.just(failedResponses.apply(message.getLogId(), "Nothing to respond"))));
    }

    private final BiFunction<DataPointMessage, String, TlvMessage> initPackage =
            (DataPointMessage message, String accessToken) -> {
                ObjectNode value = JsonUtil.createObjectNode();

                ObjectNode param = JsonUtil.createObjectNode();
                param.set(PAM_PARAM_DUEROS_DEVICE_ID, TextNode.valueOf(message.getDeviceId()));
                param.set(PAM_PARAM_STANDBY_DEVICE_ID, TextNode.valueOf(message.getStandbyDeviceId()));
                param.set(PAM_PARAM_USER_AGENT, TextNode.valueOf(message.getUserAgent()));
                param.set(PAM_PARAM_AUTHORIZATION, TextNode.valueOf(PARAMETER_BEARER + accessToken));
                param.set(PAM_PARAM_LINK_VERSION, IntNode.valueOf(2));
                value.set(DCSProxyConstant.JSON_KEY_PARAM, TextNode.valueOf(JsonUtil.serialize(param)));

                value.set(DCSProxyConstant.JSON_KEY_SN, TextNode.valueOf(message.getSn()));
                value.set(DCSProxyConstant.JSON_KEY_CLIENT_IP, TextNode.valueOf(message.getDeviceIp()));
                String cltId = message.getCltId();
                String[] items = cltId.split(Pattern.quote(CommonConstant.SPLITTER_DOLLAR));
                Preconditions.checkArgument(items.length >= 2, "Illegal client id: " + cltId);
                value.set(DCSProxyConstant.JSON_KEY_PID, TextNode.valueOf(items[0]));

                try {
                    byte[] bytes = JsonUtil.writeAsBytes(value);
                    long vlen = bytes.length;
                    return new TlvMessage(TlvConstant.TYPE_UPSTREAM_INIT, vlen, BinaryNode.valueOf(bytes));
                } catch (JsonProcessingException e) {
                    log.error("Assembling the init package failed", e);
                }
                return null;
            };

    private final Function<DataPointMessage, TlvMessage> dataPackage = (DataPointMessage message) -> {

        String payload = message.getPayload();
        MultipartBody.Part part = MultipartBody.Part.createFormData(
                PARAMETER_METADATA,
                null,
                RequestBody.create(okhttp3.MediaType.parse(MediaType.APPLICATION_JSON_UTF8_VALUE), payload)
        );
        RequestBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(part)
                .build();
        Buffer buffer = new Buffer();
        okhttp3.MediaType mediaType = Optional.ofNullable(multipartBody.contentType())
                .orElseGet(() ->
                        okhttp3.MediaType.get(
                                MediaType.APPLICATION_PROBLEM_JSON_UTF8_VALUE
                                        + "; boundary="
                                        + ByteString.encodeUtf8(UUID.randomUUID().toString()).utf8()));
        ByteString header = ByteString.encodeString(
                String.format("%s: %s", HEADER_CONTENT_TYPE, mediaType.toString()), Charsets.UTF_8);
        try {
            // entity header
            buffer.write(header);
            buffer.write(CRLF);
            buffer.write(CRLF);
            // body
            multipartBody.writeTo(buffer);
            long vlen = buffer.size();
            byte[] content = buffer.readByteArray();
            log.debug("multipartBody:\n{}", new String(content, Charsets.UTF_8));
            return new TlvMessage(TlvConstant.TYPE_UPSTREAM_DUMI, vlen, content);
        } catch (IOException e) {
            log.error("Assembling the data package failed", e);
        }
        return null;
    };

    private final Function<DataPointMessage, TlvMessage> finishPackage = (DataPointMessage message) -> {
        ObjectNode value = JsonUtil.createObjectNode();
        value.set(DCSProxyConstant.JSON_KEY_ERROR, IntNode.valueOf(MESSAGE_SUCCESS_CODE));
        value.set(DCSProxyConstant.JSON_KEY_ERROR_MSG, TextNode.valueOf(MESSAGE_SUCCESS));
        long vlen = value.toString().getBytes().length;
        try {
            return new TlvMessage(TlvConstant.TYPE_UPSTREAM_FINISH, vlen, BinaryNode.valueOf(JsonUtil.writeAsBytes(value)));
        } catch (JsonProcessingException e) {
            log.error("Assembling the finish package failed", e);
            return null;
        }
    };

    private final class DisconnectedHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            Channel channel = ctx.channel();
            log.debug("{} has closed from dcs", channel.toString());
            super.close(ctx, promise);
        }
    }

    private final Supplier<DataPointMessage> successDataPointResponses = () -> {
        DataPointMessage response = new DataPointMessage();
        response.setVersion(DEFAULT_VERSION);
        response.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_VALID);
        response.setId(IdGenerator.nextId());
        return response;
    };

    private final Function<DataPointMessage, DataPointMessage> unauthorizedResponses = (origin) -> {
        DataPointMessage response = new DataPointMessage();
        response.setVersion(origin.getVersion());
        response.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNAUTHORIZED);
        response.setId(origin.getId());
        response.setPath(DataPointConstant.DATA_POINT_PRIVATE_ERROR);
        response.setPayload(String.format("Couldn't obtain access token for %s", origin.getDeviceId()));
        return response;
    };

    private final Function<DataPointMessage, DataPointMessage> emptyResponses = (origin) -> {
        DataPointMessage response = new DataPointMessage();
        response.setVersion(origin.getVersion());
        response.setCode(COAP_RESPONSE_CODE_DUER_MSG_RSP_UNSUPPORTED_CONTENT_FORMAT);
        response.setId(origin.getId());
        response.setPath(DataPointConstant.DATA_POINT_PRIVATE_ERROR);
        response.setPayload(String.format("No payload for %s", origin.getDeviceId()));
        return response;
    };

    private Optional<String> loadAccessToken(BaseMessage message) throws Exception {
        AccessTokenResponse response = accessTokenService.try2ObtainAccessToken(message);
        if (response != null && StringUtils.hasText(response.getAccessToken())) {
            return Optional.of(response.getAccessToken());
        }
        log.error("This device({})'s login session has been expired.", message.getDeviceId());
        return Optional.empty();
    }

    private List<DataPointMessage> deal(List<Object> list, DataPointMessage origin) {
        List<TlvMessage> tlvs = list
                .stream()
                .filter(o -> o instanceof TlvMessage)
                .map(TlvMessage.class::cast)
                .collect(Collectors.toList());

        return Adapter.directive2DataPoint(
                directiveProcessor.process(
                        origin.getDeviceId(),
                        origin.getSn(),
                        tlvs
                ),
                origin
        );
    }
}

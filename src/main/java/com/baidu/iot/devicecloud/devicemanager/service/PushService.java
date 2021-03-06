package com.baidu.iot.devicecloud.devicemanager.service;

import com.baidu.iot.devicecloud.devicemanager.bean.BaseMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.BaseResponse;
import com.baidu.iot.devicecloud.devicemanager.bean.DataPointMessage;
import com.baidu.iot.devicecloud.devicemanager.bean.LocalServerInfo;
import com.baidu.iot.devicecloud.devicemanager.client.http.dhclient.DhClient;
import com.baidu.iot.devicecloud.devicemanager.client.http.redirectclient.RedirectClient;
import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import com.baidu.iot.log.Log;
import com.baidu.iot.log.LogProvider;
import com.baidu.iot.log.Stopwatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.UnicastProcessor;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.List;

import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_NEED;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_ACK_SECRET_KEY;
import static com.baidu.iot.devicecloud.devicemanager.constant.CommonConstant.MESSAGE_SUCCESS_CODE;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.close;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.failedResponses;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.isCoapOk;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponse;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponseFromDP;
import static com.baidu.iot.devicecloud.devicemanager.util.HttpUtil.successResponsesWithMessage;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/20.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Component
public class PushService implements InitializingBean {
    private static final Logger infoLog = LoggerFactory.getLogger("infoLog");
    private static final LogProvider logProvider = LogProvider.getInstance();

    private final DhClient client;
    private final RedirectClient redirectClient;
    private final SecurityService securityService;
    private final LocalServerInfo localServerInfo;
    private Cache<String, UnicastProcessor<DataPointMessage>> pooledMonoSignals;

    @Value("${expire.tts.bytes:1800}")
    private Integer ttsExpire;

    @Value("${dm.scheme:http://}")
    private String dmScheme;

    @Value("${dm.report.api:/api/v2/report}")
    private String dmReportApi;

    @Autowired
    public PushService(DhClient client,
                       RedirectClient redirectClient,
                       SecurityService securityService,
                       LocalServerInfo localServerInfo) {
        this.client = client;
        this.redirectClient = redirectClient;
        this.securityService = securityService;
        this.localServerInfo = localServerInfo;
    }

    @Override
    public void afterPropertiesSet() {
        pooledMonoSignals = CacheBuilder.newBuilder()
                .concurrencyLevel(100)
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(1_000_000)
                .removalListener(LogUtils.REMOVAL_LOGGER.apply(log))
                .build();
    }

    public String pool(BaseMessage message) {
        if (message.isNeedAck()) {
            String key = securityService.nextSecretKey(message.getDeviceId());
            UnicastProcessor<DataPointMessage> signal = UnicastProcessor.create(Queues.<DataPointMessage>xs().get());
            pooledMonoSignals.put(key, signal);
            return key;
        }
        return null;
    }

    public void unPool(String key) {
        UnicastProcessor<DataPointMessage> signal = pooledMonoSignals.getIfPresent(key);
        pooledMonoSignals.invalidate(key);
        if (signal != null) {
            signal.dispose();
        }
    }

    public void advice(String key, DataPointMessage message) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        String[] items = securityService.decryptSecretKey(key);
        if (items != null && items.length >= 5) {
            String ip = items[2];
            String port = items[3];
            if (localServerInfo.getLocalServerIp().equalsIgnoreCase(ip) &&
            Integer.toString(LocalServerInfo.localServerPort).equalsIgnoreCase(port)) {
                UnicastProcessor<DataPointMessage> signal = pooledMonoSignals.getIfPresent(key);
                if (signal != null && !signal.isDisposed()) {
                    signal.onNext(message);
                }
            } else {
                redirectClient.redirectDataPointAsync(ip, port, message).handleAsync(
                        (r, t) -> {
                            try {
                                if (r != null && r.isSuccessful()) {
                                    log.info("Redirecting to {}:{} succeeded", ip, port);
                                }
                                return null;
                            } finally {
                                close(r);
                            }
                        }
                );
            }
        }
    }

    public Mono<BaseResponse> push(DataPointMessage message) {
        Log spanLog = logProvider.get(message.getLogId());
        spanLog.setCuId(message.getDeviceId());
        Stopwatch stopwatch = spanLog.time("dh2");
        String logId = spanLog.getLogId();
        return Mono.fromFuture(
                this.client.pushMessageAsync(message)
                .handleAsync(
                        (r, t) -> {
                            stopwatch.pause();
                            try(ResponseBody body = r.body()) {
                                if (body != null) {
                                    byte[] bytes = body.bytes();
                                    log.debug("DH response:\n{}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(bytes)));

                                    JsonNode bodyNode = JsonUtil.readTree(bytes);
                                    infoLog.info(spanLog.format(String.format("DH response:%s", bodyNode.toString())));
                                    if (!bodyNode.isNull()) {
                                        int err_no = bodyNode.path("err_no").asInt(-1);
                                        String err_msg = bodyNode.path("error").asText("");
                                        if (err_no == MESSAGE_SUCCESS_CODE) {
                                            return successResponse.apply(logId, err_msg);
                                        } else {
                                            failedResponses.apply(logId, err_msg);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Pushing dh failed", e);
                                return failedResponses.apply(logId, e.getMessage());
                            } finally {
                                logProvider.revoke(logId);
                            }
                            return failedResponses.apply(logId, "Pushing dh failed");
                        }
                )
        );
    }

    public void justPush(DataPointMessage message) {
        this.client.pushMessageAsync(message)
                .handleAsync(
                        (r, t) -> {
                            try(ResponseBody body = r.body()) {
                                if (log.isDebugEnabled() && r.isSuccessful() && body != null) {
                                    log.debug("DH response:\n{}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(body.bytes())));
                                }
                            } catch (Exception e) {
                                log.error("Pushing dh failed", e);
                            }
                            return null;
                        }
                );
    }

    public void prepareAckPush(DataPointMessage assembled) {
        if (assembled != null && StringUtils.hasText(assembled.getCltId()) && StringUtils.hasText(assembled.getDeviceId())) {
            assembled.setNeedAck(true);
            String key = pool(assembled);
            assembled.setKey(key);
            ObjectNode misc = JsonUtil.createObjectNode();
            misc.set(MESSAGE_ACK_NEED, BooleanNode.getTrue());
            misc.set(MESSAGE_ACK_SECRET_KEY, TextNode.valueOf(key));
            assembled.setMisc(misc.toString());
        }
    }

    public Mono<BaseResponse> check(BaseMessage message, String key, List<Integer> stub) {
        if (message == null || !message.isNeedAck() || stub == null || stub.size() < 1) {
            return Mono.just(successResponsesWithMessage.apply(message));
        }
        UnicastProcessor<DataPointMessage> signal = pooledMonoSignals.getIfPresent(key);
        if (signal == null || signal.isDisposed()) {
            return Mono.just(failedResponses.apply(message.getLogId(), "No signal"));
        }

        return Mono.create(sink -> signal
                .subscribe(new BaseSubscriber<DataPointMessage>(){
            @Override
            protected void hookOnNext(DataPointMessage ack) {
                if (ack != null) {
                    int id = ack.getId();
                    if (isCoapOk.test(ack) && stub.contains(id)) {
                        stub.removeIf(i -> i == id);
                    }
                    if (stub.isEmpty()) {
                        sink.success(successResponseFromDP.apply(message.getLogId(), ack));
                        signal.cancel();
                    }
                }
            }

            @Override
            protected void hookOnComplete() {
                sink.success();
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                sink.error(throwable);
            }

            @Override
            protected void hookFinally(SignalType type) {
                unPool(key);
            }
        }));
    }
}

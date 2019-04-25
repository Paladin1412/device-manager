package com.baidu.iot.devicecloud.devicemanager.config;

import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

import static reactor.core.scheduler.Schedulers.single;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/27.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
public class PartnerServerHttpResponseDecorator extends ServerHttpResponseDecorator {
    public PartnerServerHttpResponseDecorator(ServerHttpResponse delegate) {
        super(delegate);
    }
    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return super.writeAndFlushWith(body);
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        HttpHeaders httpHeaders = super.getHeaders();
        final String headers = httpHeaders.entrySet()
                .stream()
                .map(entry -> "             " + entry.getKey() + ": [" + String.join(";", entry.getValue()) + "]")
                .collect(Collectors.joining("\n"));
        log.debug("\n{}", String.format("Headers    : \n%s", headers));
        final MediaType contentType = httpHeaders.getContentType();
        if (LogUtils.legalLogMediaTypes.contains(contentType)) {
            if (body instanceof Mono) {
                final Mono<DataBuffer> monoBody = (Mono<DataBuffer>) body;
                return super.writeWith(monoBody.publishOn(single()).map(dataBuffer -> LogUtils.loggingResponse(log, dataBuffer)));
            } else if (body instanceof Flux) {
                final Flux<DataBuffer> monoBody = (Flux<DataBuffer>) body;
                return super.writeWith(monoBody.publishOn(single()).map(dataBuffer -> LogUtils.loggingResponse(log, dataBuffer)));
            }
        }
        return super.writeWith(body);
    }

}

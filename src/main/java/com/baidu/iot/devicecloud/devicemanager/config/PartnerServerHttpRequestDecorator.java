package com.baidu.iot.devicecloud.devicemanager.config;

import com.baidu.iot.devicecloud.devicemanager.util.LogUtils;
import io.netty.buffer.UnpooledByteBufAllocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/27.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
class PartnerServerHttpRequestDecorator extends ServerHttpRequestDecorator {
    private NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(new UnpooledByteBufAllocator(false));
    private final DataBuffer bodyBuffer = nettyDataBufferFactory.allocateBuffer();
    private StringBuilder requestMetadata = new StringBuilder();

    PartnerServerHttpRequestDecorator(ServerHttpRequest delegate) {
        super(delegate);
        final String path = delegate.getURI().getPath();
        final String query = delegate.getURI().getQuery();
        final String method = Optional.ofNullable(delegate.getMethod()).orElse(HttpMethod.GET).name();
        final String headers = delegate.getHeaders().entrySet()
                .stream()
                .map(entry -> "             " + entry.getKey() + ": [" + String.join(";", entry.getValue()) + "]")
                .collect(Collectors.joining("\n"));
        if (log.isDebugEnabled()) {
            requestMetadata
                    .append("\n")
                    .append(String.format("HttpMethod : %s\n", method))
                    .append(String.format("Uri        : %s\n", path + (StringUtils.isEmpty(query) ? "" : "?" + query)))
                    .append(String.format("Headers    : \n%s", headers));
        }
    }

    @NonNull
    @Override
    public Flux<DataBuffer> getBody() {
        return super.getBody().doOnNext(this::cache).doOnComplete(() -> {
            log.debug(requestMetadata.toString());
            LogUtils.loggingRequest(log, bodyBuffer);
        });
    }

    private void cache(DataBuffer buffer) {
        bodyBuffer.write(buffer.asByteBuffer());
    }
}

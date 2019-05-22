package com.baidu.iot.devicecloud.devicemanager.util;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/5/14.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
public class BufferUtil {
    private static final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    public static DataBuffer create(int initialCapacity) {
        return dataBufferFactory.allocateBuffer(initialCapacity);
    }

    public static Flux<DataBuffer> readAsDataBuffers(InputStream in) {
        return DataBufferUtils.readInputStream(
                () -> in,
                dataBufferFactory,
                1 << 10
        );
    }

    public static Mono<DataBuffer> joinBuffers(Flux<DataBuffer> bufferFlux) {
        return DataBufferUtils.join(bufferFlux);
    }
}
